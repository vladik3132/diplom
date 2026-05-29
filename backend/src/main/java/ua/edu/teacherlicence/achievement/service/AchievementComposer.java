package ua.edu.teacherlicence.achievement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.repository.AchievementRepository;
import ua.edu.teacherlicence.common.model.BaseAuditEntity;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.model.PpDataValidationResult;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.ppdata.repository.PpDataValidationResultRepository;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Генерує title та description для досягнень на основі структурованих ентіті
 * (Publication, ppData) замість збереження сирого тексту з DOCX.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementComposer {

    private final AchievementRepository achievementRepository;
    /**
     * ValidationService — джерело правди для ✅ записів пп.20 (з AI-фільтрацією
     * профільності кафедри). Lazy щоб уникнути циклічної залежності composer ↔ validator
     * (validator читає Achievement.description який композує composer).
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private AchievementValidationService achievementValidationService;
    private final PublicationRepository publicationRepository;
    private final ScientificSupervisionRepository supervisionRepository;
    private final AttestationActivityRepository attestationRepository;
    private final EditorialActivityRepository editorialRepository;
    private final ExpertCouncilRepository expertCouncilRepository;
    private final InternationalProjectRepository internationalRepository;
    private final ScientificConsultingRepository consultingRepository;
    private final ForeignLanguageTeachingRepository foreignLangRepository;
    private final OlympiadGuidanceRepository olympiadRepository;
    private final MilitaryMissionRepository militaryRepository;
    private final ProfessionalAssociationRepository associationRepository;
    private final PracticalExperienceRepository practicalRepository;
    private final ua.edu.teacherlicence.teacher.repository.CareerRecordRepository careerRecordRepository;
    private final PpDataValidationResultRepository validationResultRepo;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.publication.service.PublicationClassifier publicationClassifier;

    /**
     * Оновлює title та description усіх досягнень викладача
     * на основі пов'язаних структурованих сутностей.
     * Автоматично створює Achievement-записи для ppType,
     * яких ще немає, але для яких є структуровані дані.
     */
    public void recomposeForTeacher(Teacher teacher) {
        Long teacherId = teacher.getId();
        List<Achievement> achievements = new ArrayList<>(achievementRepository.findByTeacherId(teacherId));
        List<Publication> publications = publicationRepository.findByTeacherId(teacherId);

        log.info("Recompose for {}: {} achievements, {} publications",
                teacher.getLastName(), achievements.size(), publications.size());

        // Дедуп Achievement-записів: по одному на тип. Залишаємо найстаріший (за id —
        // найменший id зазвичай створений раніше і має зв'язки в ValidationResult).
        // Раніше були випадки де DataImport / повторний AI імпорт створював 2-й запис того ж типу,
        // через що в Досягнення п.38 показувалось 2 рядки пп.20.
        achievements = deduplicateAchievements(achievements, teacher);

        // Збираємо ID сутностей з валідацією ERROR — вони не повинні попадати в досягнення
        Map<String, Set<Long>> invalidEntities = getInvalidEntityMap(teacherId);
        if (!invalidEntities.isEmpty()) {
            log.info("Filtering out invalid ppData entities: {}", invalidEntities);
        }

        // Групуємо публікації за пп. через канонічний {@link PublicationClassifier}.
        // Це забезпечує: composer і AchievementValidationService.checkPpN() використовують
        // ОДНАКОВУ фільтрацію, тож опис ↔ dbCount завжди узгоджені.
        Map<AchievementType, List<Publication>> pubsByPp = new HashMap<>();
        for (int pp : new int[]{1, 2, 3, 4, 12}) {
            List<Publication> filtered = publicationClassifier.filterForPp(publications, pp);
            if (!filtered.isEmpty()) {
                AchievementType type = AchievementType.fromNumber(pp);
                if (type != null) pubsByPp.put(type, filtered);
            }
        }

        log.info("PubsByPp keys: {}", pubsByPp.keySet());

        // Збираємо типи досягнень, які вже є
        Set<AchievementType> existingTypes = achievements.stream()
                .map(Achievement::getAchievementType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Визначаємо типи, для яких є дані, але немає Achievement-запису
        Set<AchievementType> neededTypes = new HashSet<>(pubsByPp.keySet());
        // Додаємо ppData типи, для яких є валідні структуровані записи
        addPpDataTypes(teacherId, neededTypes, invalidEntities);

        // Створюємо відсутні Achievement-записи
        for (AchievementType type : neededTypes) {
            if (!existingTypes.contains(type)) {
                Achievement newAch = new Achievement();
                newAch.setTeacher(teacher);
                newAch.setAchievementType(type);
                newAch.setTitle("—");
                newAch.setDescription("—");
                achievementRepository.save(newAch);
                achievements.add(newAch);
                log.info("Auto-created achievement {} for {}", type, teacher.getLastName());
            }
        }

        int updated = 0;
        List<Achievement> toDelete = new ArrayList<>();
        for (Achievement a : achievements) {
            try {
                String composed = composeDescription(a.getAchievementType(), teacher, pubsByPp, invalidEntities);
                if (composed != null) {
                    a.setTitle(formatTitle(a.getAchievementType(), composed));
                    a.setDescription(composed);
                    a.setVerified(true);
                    // Для пп.1: qualifiedCount — кількість публікацій після content-dedup
                    // (DOI/назва), вже виконаного у filterForPp.
                    if (a.getAchievementType() == AchievementType.PP_1_PUBLICATIONS) {
                        List<Publication> pp1Pubs = pubsByPp.get(AchievementType.PP_1_PUBLICATIONS);
                        a.setQualifiedCount(pp1Pubs != null ? pp1Pubs.size() : 0);
                    }
                    achievementRepository.save(a);
                    updated++;
                    log.debug("Recomposed {}: {}", a.getAchievementType(),
                            composed.length() > 80 ? composed.substring(0, 80) + "..." : composed);
                } else if (isPpDataBasedType(a.getAchievementType())) {
                    // Захист: для PP_20 НЕ видаляємо Achievement якщо в БД є хоч одне джерело
                    // даних (career_records / practical_experience). composePp20 може повернути
                    // null лише через помилку інжекції/валідації — Achievement має зберегтися.
                    if (a.getAchievementType() == AchievementType.PP_20_PRACTICAL_EXPERIENCE) {
                        boolean hasCareer = !careerRecordRepository.findByTeacherId(teacherId).isEmpty();
                        boolean hasPractical = !practicalRepository.findByTeacherId(teacherId).isEmpty();
                        if (hasCareer || hasPractical) {
                            log.warn("PP_20 composer returned null but data exists (career={}, practical={}) for {} — keeping Achievement",
                                    hasCareer, hasPractical, teacher.getLastName());
                            continue;
                        }
                    }
                    toDelete.add(a);
                    log.info("Removing achievement {} — no valid ppData entries", a.getAchievementType());
                } else {
                    log.debug("No data to compose for {} of {}", a.getAchievementType(), teacher.getLastName());
                }
            } catch (Exception e) {
                log.warn("Failed to recompose achievement {} for teacher {}: {}",
                        a.getAchievementType(), teacher.getLastName(), e.getMessage(), e);
            }
        }
        // Видаляємо невалідні досягнення
        for (Achievement a : toDelete) {
            achievementRepository.delete(a);
        }
        log.info("Recomposed {}/{} achievements for {} (removed {})",
                updated, achievements.size(), teacher.getLastName(), toDelete.size());
    }

    /**
     * Видаляє дублюючі Achievement-записи: один тип = один запис.
     * Залишаємо запис з найменшим id (найстаріший — він зазвичай має звя'зки
     * у ValidationResult, тож видалення новіших дублів безпечніше).
     * Дублі видаляємо з БД (з catch на FK-конфлікти — тоді просто залишаємо обидва).
     */
    private List<Achievement> deduplicateAchievements(List<Achievement> achievements, Teacher teacher) {
        Map<AchievementType, Achievement> kept = new LinkedHashMap<>();
        List<Achievement> dups = new ArrayList<>();
        for (Achievement a : achievements) {
            AchievementType t = a.getAchievementType();
            if (t == null) {
                kept.put(null, a);   // null-typed — не дедуплікуємо за типом, залишаємо як є
                continue;
            }
            Achievement existing = kept.get(t);
            if (existing == null) {
                kept.put(t, a);
            } else {
                Long existingId = existing.getId();
                Long aId = a.getId();
                if (aId != null && existingId != null && aId < existingId) {
                    dups.add(existing);
                    kept.put(t, a);
                } else {
                    dups.add(a);
                }
            }
        }
        for (Achievement dup : dups) {
            try {
                achievementRepository.delete(dup);
                log.info("Removed duplicate achievement {} (id={}) for {}",
                        dup.getAchievementType(), dup.getId(), teacher.getLastName());
            } catch (Exception e) {
                log.warn("Failed to remove duplicate achievement {} (id={}) for {}: {}",
                        dup.getAchievementType(), dup.getId(), teacher.getLastName(), e.getMessage());
                // Не вдалося видалити (FK constraint?) — залишаємо в списку, нехай recompose оновить
                kept.put(dup.getAchievementType(), dup);
            }
        }
        // Повертаємо тільки унікальні (по одному запису на тип) у тому ж порядку, що були додані
        List<Achievement> unique = new ArrayList<>(kept.values());
        // Прибираємо null-typed з мапи (якщо був), додаємо назад окремо щоб уникнути null-key issues
        unique.removeIf(Objects::isNull);
        return unique;
    }

    /**
     * Перевіряє наявність структурованих ppData записів і додає відповідні AchievementType.
     * Записи з валідацією ERROR ігноруються.
     */
    private void addPpDataTypes(Long teacherId, Set<AchievementType> types,
                                Map<String, Set<Long>> invalidEntities) {
        if (hasValidEntries(supervisionRepository.findByTeacherId(teacherId), "scientific-supervision", invalidEntities))
            types.add(AchievementType.PP_6_SUPERVISION);
        if (hasValidEntries(attestationRepository.findByTeacherId(teacherId), "attestation-activity", invalidEntities))
            types.add(AchievementType.PP_7_ATTESTATION);
        if (hasValidEntries(editorialRepository.findByTeacherId(teacherId), "editorial-activity", invalidEntities))
            types.add(AchievementType.PP_8_EDITORIAL);
        if (hasValidEntries(expertCouncilRepository.findByTeacherId(teacherId), "expert-council", invalidEntities))
            types.add(AchievementType.PP_9_EXPERT_COUNCIL);
        if (hasValidEntries(internationalRepository.findByTeacherId(teacherId), "international-project", invalidEntities))
            types.add(AchievementType.PP_10_INTERNATIONAL);
        if (hasValidEntries(consultingRepository.findByTeacherId(teacherId), "scientific-consulting", invalidEntities))
            types.add(AchievementType.PP_11_CONSULTING);
        if (hasValidEntries(foreignLangRepository.findByTeacherId(teacherId), "foreign-language-teaching", invalidEntities))
            types.add(AchievementType.PP_13_FOREIGN_LANGUAGE);
        if (hasValidEntries(olympiadRepository.findByTeacherId(teacherId), "olympiad-guidance", invalidEntities))
            types.add(AchievementType.PP_14_STUDENT_OLYMPIAD);
        if (hasValidEntries(militaryRepository.findByTeacherId(teacherId), "military-mission", invalidEntities))
            types.add(AchievementType.PP_17_UN_PEACEKEEPING);
        if (hasValidEntries(associationRepository.findByTeacherId(teacherId), "professional-association", invalidEntities))
            types.add(AchievementType.PP_19_PROFESSIONAL_ASSOCIATIONS);
        // PP_20: триггерим на наявність будь-яких ппдата записів АБО career_records.
        // Раніше тільки practical_experience → якщо викладач заповнив тільки "Послужний список",
        // Achievement PP_20 не створювався і прогрес не відображався в п.38.
        boolean hasPracticalEntries = hasValidEntries(
                practicalRepository.findByTeacherId(teacherId), "practical-experience", invalidEntities);
        int careerCount = careerRecordRepository.findByTeacherId(teacherId).size();
        boolean hasPp20Source = hasPracticalEntries || careerCount > 0;
        log.info("PP_20 sources for teacherId={}: practical={}, careerCount={}, willAddType={}",
                teacherId, hasPracticalEntries, careerCount, hasPp20Source);
        if (hasPp20Source) {
            types.add(AchievementType.PP_20_PRACTICAL_EXPERIENCE);
        }
    }

    /** Чи є хоча б один валідний запис (не ERROR) */
    private <T extends BaseAuditEntity> boolean hasValidEntries(List<T> entities, String entityType,
                                                                 Map<String, Set<Long>> invalidEntities) {
        Set<Long> invalidIds = invalidEntities.getOrDefault(entityType, Set.of());
        return entities.stream().anyMatch(e -> !invalidIds.contains(e.getId()));
    }

    /** Фільтрує список, залишаючи тільки валідні записи */
    private <T extends BaseAuditEntity> List<T> filterValid(List<T> entities, String entityType,
                                                             Map<String, Set<Long>> invalidEntities) {
        Set<Long> invalidIds = invalidEntities.getOrDefault(entityType, Set.of());
        if (invalidIds.isEmpty()) return entities;
        return entities.stream().filter(e -> !invalidIds.contains(e.getId())).toList();
    }

    /** Чи є тип досягнення на основі ppData (не публікацій) */
    private boolean isPpDataBasedType(AchievementType type) {
        return type != null && switch (type) {
            case PP_6_SUPERVISION, PP_7_ATTESTATION, PP_8_EDITORIAL,
                 PP_9_EXPERT_COUNCIL, PP_10_INTERNATIONAL, PP_11_CONSULTING,
                 PP_13_FOREIGN_LANGUAGE, PP_14_STUDENT_OLYMPIAD, PP_15_SCHOOL_OLYMPIAD,
                 PP_17_UN_PEACEKEEPING, PP_18_NATO_EXERCISES,
                 PP_19_PROFESSIONAL_ASSOCIATIONS, PP_20_PRACTICAL_EXPERIENCE -> true;
            default -> false;
        };
    }

    /**
     * Будує мапу невалідних сутностей: entityType → Set<entityId>.
     * Включає сутності з останнім статусом ERROR або WARNING — обидва не попадають в досягнення.
     */
    private Map<String, Set<Long>> getInvalidEntityMap(Long teacherId) {
        List<PpDataValidationResult> results = validationResultRepo.findByTeacherIdOrderByValidatedAtDesc(teacherId);
        if (results.isEmpty()) return Map.of();

        // Зберігаємо тільки останній результат для кожного (entityType, entityId)
        Map<String, PpDataValidationResult> latestByEntity = new LinkedHashMap<>();
        for (PpDataValidationResult r : results) {
            String key = r.getEntityType() + ":" + r.getEntityId();
            latestByEntity.putIfAbsent(key, r); // перший = найновіший (сортовано DESC)
        }

        // Збираємо ID з ERROR або WARNING статусом — обидва не відповідають вимогам
        Map<String, Set<Long>> invalidMap = new HashMap<>();
        for (PpDataValidationResult r : latestByEntity.values()) {
            if ("ERROR".equals(r.getStatus()) || "WARNING".equals(r.getStatus())) {
                invalidMap.computeIfAbsent(r.getEntityType(), k -> new HashSet<>()).add(r.getEntityId());
            }
        }
        return invalidMap;
    }

    private String formatTitle(AchievementType type, String desc) {
        return desc.length() > 200 ? desc.substring(0, 200) + "..." : desc;
    }

    private String composeDescription(AchievementType type, Teacher teacher,
                                       Map<AchievementType, List<Publication>> pubsByPp,
                                       Map<String, Set<Long>> invalidEntities) {
        if (type == null) return null;
        Long teacherId = teacher.getId();

        return switch (type) {
            case PP_1_PUBLICATIONS -> composePp1(pubsByPp.get(type));
            case PP_2_PATENTS -> composePp2(pubsByPp.get(type));
            case PP_3_TEXTBOOK -> composePp3(pubsByPp.get(type));
            case PP_4_METHODICAL -> composePp4(pubsByPp.get(type));
            case PP_5_DISSERTATION -> composePp5(teacher);
            case PP_6_SUPERVISION -> composePp6(filterValid(
                    supervisionRepository.findByTeacherId(teacherId), "scientific-supervision", invalidEntities));
            case PP_7_ATTESTATION -> composePp7(filterValid(
                    attestationRepository.findByTeacherId(teacherId), "attestation-activity", invalidEntities));
            case PP_8_EDITORIAL -> composePp8(filterValid(
                    editorialRepository.findByTeacherId(teacherId), "editorial-activity", invalidEntities));
            case PP_9_EXPERT_COUNCIL -> composePp9(filterValid(
                    expertCouncilRepository.findByTeacherId(teacherId), "expert-council", invalidEntities));
            case PP_10_INTERNATIONAL -> composePp10(filterValid(
                    internationalRepository.findByTeacherId(teacherId), "international-project", invalidEntities));
            case PP_11_CONSULTING -> composePp11(filterValid(
                    consultingRepository.findByTeacherId(teacherId), "scientific-consulting", invalidEntities));
            case PP_12_APPROBATION -> composePp12(pubsByPp.get(type));
            case PP_13_FOREIGN_LANGUAGE -> composePp13(filterValid(
                    foreignLangRepository.findByTeacherId(teacherId), "foreign-language-teaching", invalidEntities));
            case PP_14_STUDENT_OLYMPIAD, PP_15_SCHOOL_OLYMPIAD ->
                    composePp14_15(filterValid(
                            olympiadRepository.findByTeacherId(teacherId), "olympiad-guidance", invalidEntities));
            case PP_16_COMBAT_VETERAN -> composePp16(teacher);
            case PP_17_UN_PEACEKEEPING, PP_18_NATO_EXERCISES ->
                    composePp17_18(filterValid(
                            militaryRepository.findByTeacherId(teacherId), "military-mission", invalidEntities));
            case PP_19_PROFESSIONAL_ASSOCIATIONS ->
                    composePp19(filterValid(
                            associationRepository.findByTeacherId(teacherId), "professional-association", invalidEntities));
            case PP_20_PRACTICAL_EXPERIENCE -> composePp20(teacher);
        };
    }

    // ============================================================
    // Публікаційні секції (пп.1-4, 12)
    // ============================================================

    private String composePp1(List<Publication> pubs) {
        if (pubs == null || pubs.isEmpty()) return null;

        // Кожна публікація рахується окремо. Content-dedup (DOI/назва) вже виконаний
        // у PublicationClassifier.filterForPp — у списку лише унікальні публікації.
        long scopus = pubs.stream().filter(p -> p.getArticleCategory() == ArticleCategory.SCOPUS).count();
        long wos = pubs.stream().filter(p -> p.getArticleCategory() == ArticleCategory.WOS).count();
        long catA = pubs.stream().filter(p -> p.getArticleCategory() == ArticleCategory.CATEGORY_A).count();
        long catB = pubs.stream().filter(p -> p.getArticleCategory() == ArticleCategory.CATEGORY_B).count();

        StringBuilder sb = new StringBuilder();
        sb.append(pubs.size()).append(" наукових публікацій");

        StringBuilder details = new StringBuilder();
        if (scopus > 0) details.append(scopus).append(" Scopus");
        if (wos > 0) { if (!details.isEmpty()) details.append(", "); details.append(wos).append(" WoS"); }
        if (catA > 0) { if (!details.isEmpty()) details.append(", "); details.append(catA).append(" Категорія А"); }
        if (catB > 0) { if (!details.isEmpty()) details.append(", "); details.append(catB).append(" Категорія Б"); }

        if (!details.isEmpty()) {
            sb.append(" (").append(details).append(")");
        }
        sb.append(":\n");

        int idx = 1;
        for (Publication p : pubs) {
            sb.append(idx++).append(". ").append(formatPubEntry(p));
            if (p.getArticleCategory() != null) {
                sb.append(" [").append(formatCategory(p.getArticleCategory())).append("]");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp2(List<Publication> pubs) {
        if (pubs == null || pubs.isEmpty()) return null;

        long patents = pubs.stream().filter(p -> p.getType() == PublicationType.PATENT).count();
        long declPatents = pubs.stream().filter(p -> p.getType() == PublicationType.DECLARATIVE_PATENT).count();
        long copyrights = pubs.stream().filter(p -> p.getType() == PublicationType.COPYRIGHT).count();

        StringBuilder sb = new StringBuilder();
        if (patents > 0) sb.append(patents).append(" патентів");
        if (declPatents > 0) { if (!sb.isEmpty()) sb.append(", "); sb.append(declPatents).append(" деклараційних патентів"); }
        if (copyrights > 0) { if (!sb.isEmpty()) sb.append(", "); sb.append(copyrights).append(" свідоцтв авторського права"); }
        sb.append(":\n");

        for (int i = 0; i < pubs.size(); i++) {
            Publication p = pubs.get(i);
            sb.append(i + 1).append(". ").append(formatPubEntry(p));
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp3(List<Publication> pubs) {
        return composePublicationSection(pubs, "підручників/посібників/монографій");
    }

    private String composePp4(List<Publication> pubs) {
        return composePublicationSection(pubs, "навчально-методичних праць");
    }

    private String composePp12(List<Publication> pubs) {
        if (pubs == null || pubs.isEmpty()) return null;

        // Кожна апробація рахується окремо. Content-dedup (DOI/назва) вже виконаний.
        long scopus = pubs.stream().filter(p -> p.getArticleCategory() == ArticleCategory.SCOPUS).count();
        long popular = pubs.stream().filter(p -> p.getType() == PublicationType.POPULAR_SCIENTIFIC).count();

        StringBuilder sb = new StringBuilder();
        sb.append(pubs.size()).append(" апробаційних/науково-популярних публікацій");
        StringBuilder details = new StringBuilder();
        if (scopus > 0) details.append(scopus).append(" Scopus");
        if (popular > 0) { if (!details.isEmpty()) details.append(", "); details.append(popular).append(" наук.-популярних"); }
        if (!details.isEmpty()) sb.append(" (").append(details).append(")");
        sb.append(":\n");

        int idx = 1;
        for (Publication p : pubs) {
            sb.append(idx++).append(". ").append(formatPubEntry(p));
            if (p.getArticleCategory() != null) {
                sb.append(" [").append(formatCategory(p.getArticleCategory())).append("]");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePublicationSection(List<Publication> pubs, String label) {
        if (pubs == null || pubs.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(pubs.size()).append(" ").append(label).append(":\n");

        for (int i = 0; i < pubs.size(); i++) {
            Publication p = pubs.get(i);
            sb.append(i + 1).append(". ").append(formatPubEntry(p));
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Форматує один запис публікації для досягнення.
     * Якщо є dstuCitation — використовує його, інакше — fallback з полів.
     */
    private String formatPubEntry(Publication p) {
        // Якщо є ДСТУ-посилання — пріоритет
        if (p.getDstuCitation() != null && !p.getDstuCitation().isBlank()) {
            return p.getDstuCitation();
        }
        // Fallback: конструюємо з полів
        StringBuilder entry = new StringBuilder();
        entry.append(p.getTitle() != null ? p.getTitle() : "—");
        if (p.getJournalName() != null) entry.append(" // ").append(p.getJournalName());
        if (p.getYear() != null) entry.append(", ").append(p.getYear());
        if (p.getAuthors() != null && !p.getAuthors().isEmpty()) {
            entry.append(" (").append(p.getAuthors()).append(")");
        }
        return entry.toString();
    }

    // ============================================================
    // ppData секції (пп.5-20)
    // ============================================================

    private String composePp5(Teacher t) {
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        AcademicDegree primary = AcademicDegreeRanking.primary(degrees);
        if (primary == null) return null;
        StringBuilder sb = new StringBuilder("Захист дисертації");
        if (primary.getDegree() != null) sb.append(": ").append(primary.getDegree());
        if (primary.getDiplomaDate() != null) sb.append(", ").append(primary.getDiplomaDate());
        if (primary.getDissertationTopic() != null) sb.append(". Тема: ").append(primary.getDissertationTopic());
        if (primary.getDiploma() != null) sb.append(". ").append(primary.getDiploma());
        return sb.toString();
    }

    private String composePp6(List<ScientificSupervision> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " наукових керівництв:\n");
        for (int i = 0; i < items.size(); i++) {
            ScientificSupervision s = items.get(i);
            sb.append(i + 1).append(". ");
            if (s.getStudentName() != null) sb.append(s.getStudentName());
            if (s.getTopic() != null) sb.append(" — ").append(s.getTopic());
            if (s.getDefenseDate() != null) sb.append(", ").append(s.getDefenseDate());
            if (s.getDegreeType() != null) sb.append(" (").append(degreeLabel(s.getDegreeType())).append(")");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp7(List<AttestationActivity> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " атестаційних активностей:\n");
        for (int i = 0; i < items.size(); i++) {
            AttestationActivity a = items.get(i);
            sb.append(i + 1).append(". ");
            if (a.getRole() != null) sb.append(attestationLabel(a.getRole())).append(": ");
            if (a.getCouncilName() != null) sb.append(a.getCouncilName());
            if (a.getStudentName() != null) sb.append(", здобувач: ").append(a.getStudentName());
            // Для разових ролей — defenseDate; для постійної ради — період dateFrom..dateTo.
            if (a.getRole() == AttestationRole.COUNCIL_MEMBER
                    && (a.getDateFrom() != null || a.getDateTo() != null)) {
                sb.append(", період: ")
                        .append(a.getDateFrom() != null ? a.getDateFrom() : "?")
                        .append(" — ")
                        .append(a.getDateTo() != null ? a.getDateTo() : "?");
                if (a.getDefenseDate() != null) sb.append(", захист: ").append(a.getDefenseDate());
            } else if (a.getDefenseDate() != null) {
                sb.append(", ").append(a.getDefenseDate());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp8(List<EditorialActivity> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " редакційно-видавничих активностей:\n");
        for (int i = 0; i < items.size(); i++) {
            EditorialActivity e = items.get(i);
            sb.append(i + 1).append(". ");
            if (e.getRole() != null) sb.append(editorialLabel(e.getRole())).append(": ");
            if (e.getJournalOrProjectName() != null) sb.append(e.getJournalOrProjectName());
            if (e.getDateFrom() != null) sb.append(" (з ").append(e.getDateFrom());
            if (e.getDateTo() != null) sb.append(" по ").append(e.getDateTo());
            if (e.getDateFrom() != null) sb.append(")");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp9(List<ExpertCouncil> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " експертних рад:\n");
        for (int i = 0; i < items.size(); i++) {
            ExpertCouncil e = items.get(i);
            sb.append(i + 1).append(". ");
            if (e.getCouncilName() != null) sb.append(e.getCouncilName());
            if (e.getType() != null) sb.append(" (").append(expertCouncilLabel(e.getType())).append(")");
            if (e.getRole() != null) sb.append(", роль: ").append(e.getRole());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp10(List<InternationalProject> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " міжнародних проєктів:\n");
        for (int i = 0; i < items.size(); i++) {
            InternationalProject p = items.get(i);
            sb.append(i + 1).append(". ");
            if (p.getProjectName() != null) sb.append(p.getProjectName());
            if (p.getProgram() != null) sb.append(" (").append(programLabel(p.getProgram())).append(")");
            if (p.getRole() != null) sb.append(", ").append(p.getRole());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp11(List<ScientificConsulting> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " наукових консультувань:\n");
        for (int i = 0; i < items.size(); i++) {
            ScientificConsulting c = items.get(i);
            sb.append(i + 1).append(". ");
            if (c.getOrganizationName() != null) sb.append(c.getOrganizationName());
            if (c.getContractNumber() != null) sb.append(", договір ").append(c.getContractNumber());
            if (c.getYearsCount() != null) sb.append(", ").append(c.getYearsCount()).append(" р.");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp13(List<ForeignLanguageTeaching> items) {
        if (items == null || items.isEmpty()) return null;
        int totalHours = items.stream().filter(f -> f.getHours() != null).mapToInt(ForeignLanguageTeaching::getHours).sum();
        StringBuilder sb = new StringBuilder("Викладання іноземною мовою: ")
                .append(totalHours).append(" годин\n");
        for (int i = 0; i < items.size(); i++) {
            ForeignLanguageTeaching f = items.get(i);
            sb.append(i + 1).append(". ");
            if (f.getDisciplineName() != null) sb.append(f.getDisciplineName());
            if (f.getLanguage() != null) sb.append(" (").append(f.getLanguage()).append(")");
            if (f.getHours() != null) sb.append(", ").append(f.getHours()).append(" год.");
            if (f.getAcademicYear() != null) sb.append(", ").append(f.getAcademicYear());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp14_15(List<OlympiadGuidance> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " записів (олімпіади/гуртки/конкурси):\n");
        for (int i = 0; i < items.size(); i++) {
            OlympiadGuidance o = items.get(i);
            sb.append(i + 1).append(". ");

            // Тип діяльності
            if (o.getActivityType() != null) {
                sb.append("[").append(switch (o.getActivityType()) {
                    case OLYMPIAD -> "Олімпіада";
                    case SCIENTIFIC_COMPETITION -> "Конкурс наук. робіт";
                    case COMPETITION -> "Конкурс";
                    case SCIENTIFIC_GROUP -> "Гурток";
                    case SPORTS -> "Спорт";
                    case ARTS -> "Мистецтво";
                    case OTHER -> "Інше";
                }).append("] ");
            }

            // Назва
            if (o.getOlympiadName() != null) sb.append(o.getOlympiadName());
            else if (o.getCompetitionName() != null) sb.append(o.getCompetitionName());

            // Для гуртків — кафедра та к-сть учасників
            if (o.getDepartmentName() != null) sb.append(", каф. ").append(o.getDepartmentName());
            if (o.getParticipantCount() != null) sb.append(" (").append(o.getParticipantCount()).append(" учасн.)");
            if (o.getAcademicYear() != null) sb.append(", ").append(o.getAcademicYear()).append(" н.р.");

            // Для олімпіад/конкурсів — студент та результат
            if (o.getStudentName() != null) sb.append(", ").append(o.getStudentName());
            if (o.getResult() != null) sb.append(" — ").append(o.getResult());
            if (o.getYear() != null) sb.append(", ").append(o.getYear());

            // Наказ
            if (o.getOrderNumber() != null) sb.append(". Наказ №").append(o.getOrderNumber());

            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp16(Teacher t) {
        if (!t.isCombatVeteranStatus()) return null;
        StringBuilder sb = new StringBuilder("Учасник бойових дій");
        if (t.getCombatVeteranDocDate() != null) sb.append(", посвідчення від ").append(t.getCombatVeteranDocDate());
        if (t.getCombatVeteranDocIssuedBy() != null) sb.append(", видане: ").append(t.getCombatVeteranDocIssuedBy());
        if (t.getCombatExperienceDates() != null) sb.append(". Дати: ").append(t.getCombatExperienceDates());
        return sb.toString();
    }

    private String composePp17_18(List<MilitaryMission> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " військових місій:\n");
        for (int i = 0; i < items.size(); i++) {
            MilitaryMission m = items.get(i);
            sb.append(i + 1).append(". ");
            if (m.getMissionName() != null) sb.append(m.getMissionName());
            if (m.getMissionType() != null) sb.append(" (").append(missionLabel(m.getMissionType())).append(")");
            if (m.getCountry() != null) sb.append(", ").append(m.getCountry());
            if (m.getDateFrom() != null) sb.append(", ").append(m.getDateFrom());
            if (m.getDateTo() != null) sb.append("–").append(m.getDateTo());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String composePp19(List<ProfessionalAssociation> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(items.size() + " професійних об'єднань:\n");
        for (int i = 0; i < items.size(); i++) {
            ProfessionalAssociation a = items.get(i);
            sb.append(i + 1).append(". ");
            if (a.getOrganizationName() != null) sb.append(a.getOrganizationName());
            if (a.getRole() != null) sb.append(", ").append(a.getRole());
            if (a.getCertificateNumber() != null) sb.append(", серт. ").append(a.getCertificateNumber());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Опис пп.20 — пріоритетно ✅ зараховані записи (через {@link AchievementValidationService}).
     * Якщо ValidationService недоступний (Lazy bean ще не ініціалізований під час бутстрапу)
     * або всі записи відсіяно AI — fallback: опис формується із сирих даних (career_records
     * або practical_experience) без AI-фільтрації, з міткою про статус. Це гарантує що
     * Achievement.PP_20 не видаляється з БД, поки в БД є хоч одне джерело даних.
     */
    private String composePp20(Teacher teacher) {
        if (teacher == null) return null;
        Long teacherId = teacher.getId();
        var careerRecords = careerRecordRepository.findByTeacherId(teacherId);
        var practicalRecords = practicalRepository.findByTeacherId(teacherId);
        boolean hasAnyData = !careerRecords.isEmpty() || !practicalRecords.isEmpty();
        if (!hasAnyData) return null;

        // Спроба №1: ValidationService → тільки ✅ записи (з AI-фільтрацією)
        if (achievementValidationService != null) {
            try {
                var records = achievementValidationService.getValidPp20Records(teacher);
                if (!records.isEmpty()) {
                    return renderPp20Description(records);
                }
                // Усі відсіяно — повернемо raw-опис нижче (Achievement не видаляємо).
                log.info("composePp20: all records filtered out by AI/pedagogical for teacher {}",
                        teacher.getLastName());
            } catch (Exception e) {
                log.warn("composePp20: validationService failed for {}, falling back to raw: {}",
                        teacher.getLastName(), e.getMessage());
            }
        }

        // Fallback: рендер усіх записів без AI-фільтрації (з позначкою статусу)
        return renderPp20Fallback(careerRecords, practicalRecords);
    }

    /** Рендер опису з ✅ записів (через ValidationService). */
    private String renderPp20Description(List<AchievementValidationService.ValidPp20Record> records) {
        var sorted = new ArrayList<>(records);
        sorted.sort(java.util.Comparator.comparing(
                AchievementValidationService.ValidPp20Record::dateFrom));

        // Merge overlap → точний Period
        java.time.Period total = java.time.Period.ZERO;
        java.time.LocalDate curStart = null;
        java.time.LocalDate curEnd = null;
        for (var r : sorted) {
            if (curStart == null) {
                curStart = r.dateFrom();
                curEnd = r.dateTo();
            } else if (!r.dateFrom().isAfter(curEnd)) {
                if (r.dateTo().isAfter(curEnd)) curEnd = r.dateTo();
            } else {
                total = total.plus(java.time.Period.between(curStart, curEnd));
                curStart = r.dateFrom();
                curEnd = r.dateTo();
            }
        }
        if (curStart != null) total = total.plus(java.time.Period.between(curStart, curEnd));

        int days = total.getDays();
        int months = total.getMonths() + days / 30;
        int years = total.getYears() + months / 12;
        months = months % 12;

        StringBuilder sb = new StringBuilder("Практичний досвід: ")
                .append(years).append(" р. ").append(months).append(" міс.\n");
        int idx = 1;
        for (var r : sorted) {
            sb.append(idx++).append(". ");
            if (r.organization() != null && !r.organization().isEmpty()) sb.append(r.organization());
            if (r.position() != null && !r.position().isEmpty()) sb.append(", ").append(r.position());
            sb.append(" (").append(r.dateFrom()).append("–").append(r.dateTo()).append(")");
            if (r.synthetic()) sb.append(" [синтетична дата з yearsCount]");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Fallback-рендер коли ValidationService недоступний АБО AI відсіяв усі записи.
     * Виводить ВСІ записи з пріоритетного джерела (career → fallback на pp20)
     * без AI-фільтрації, щоб Achievement зберігся в БД і користувач бачив дані.
     * Прогрес у Звіті відповідності все одно покаже 0/5 (бо валідація відсіяла все).
     */
    private String renderPp20Fallback(List<ua.edu.teacherlicence.teacher.model.CareerRecord> career,
                                       List<PracticalExperience> practical) {
        boolean useCareer = !career.isEmpty();
        StringBuilder sb = new StringBuilder("Практичний досвід (записи з БД):\n");
        int idx = 1;
        if (useCareer) {
            for (var c : career) {
                sb.append(idx++).append(". ");
                if (c.getOrganization() != null) sb.append(c.getOrganization());
                if (c.getPosition() != null) sb.append(", ").append(c.getPosition());
                if (c.getStartDate() != null) sb.append(" (").append(c.getStartDate());
                if (c.getEndDate() != null) sb.append("–").append(c.getEndDate());
                else if (c.getStartDate() != null) sb.append("–по т.ч.");
                if (c.getStartDate() != null) sb.append(")");
                if (c.getNotes() != null && !c.getNotes().isBlank()) sb.append(". ").append(c.getNotes());
                sb.append("\n");
            }
        } else {
            for (var p : practical) {
                sb.append(idx++).append(". ");
                if (p.getOrganizationName() != null) sb.append(p.getOrganizationName());
                if (p.getPosition() != null) sb.append(", ").append(p.getPosition());
                if (p.getDateFrom() != null) sb.append(" (").append(p.getDateFrom());
                if (p.getDateTo() != null) sb.append("–").append(p.getDateTo());
                if (p.getDateFrom() != null) sb.append(")");
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ── Enum labels (Ukrainian) ─────────────────────────────────────

    private String degreeLabel(DegreeType t) {
        return switch (t) { case PHD -> "PhD"; case DSC -> "д.н."; case CANDIDATE -> "к.н."; case DOCTOR -> "доктор наук"; };
    }
    private String attestationLabel(AttestationRole r) {
        return switch (r) {
            case OPPONENT -> "офіційний опонент";
            case REVIEWER -> "рецензент";
            case CHAIR -> "голова разової спецради";
            case COUNCIL_MEMBER -> "член постійної спецради";
        };
    }
    private String editorialLabel(EditorialRole r) {
        return switch (r) { case THEME_LEADER -> "керівник теми"; case RESPONSIBLE_EXECUTOR -> "відповідальний виконавець"; case CHIEF_EDITOR -> "головний редактор"; case BOARD_MEMBER -> "член редколегії"; case REVIEWER -> "рецензент"; };
    }
    private String expertCouncilLabel(ExpertCouncilType t) {
        return switch (t) { case MON -> "МОН"; case NAZYAVO -> "НАЗЯВО"; case ACCREDITATION -> "акред. комісія"; case NMR -> "НМР"; case STATE_SERVICE -> "держ. служба"; };
    }
    private String programLabel(InternationalProgram p) {
        return switch (p) { case ERASMUS -> "Erasmus+"; case HORIZON -> "Horizon Europe"; case NATO -> "НАТО"; case BILATERAL -> "двостороння угода"; case GRANT -> "грант"; case OTHER -> "інше"; };
    }
    private String missionLabel(MissionType t) {
        return switch (t) { case UN_PEACEKEEPING -> "миротворча операція ООН"; case NATO_EXERCISE -> "навчання НАТО"; };
    }

    private String formatCategory(ArticleCategory cat) {
        return switch (cat) {
            case SCOPUS -> "Scopus";
            case WOS -> "WoS";
            case CATEGORY_A -> "Кат. А";
            case CATEGORY_B -> "Кат. Б";
        };
    }
}
