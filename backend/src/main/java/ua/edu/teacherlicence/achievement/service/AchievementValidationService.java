package ua.edu.teacherlicence.achievement.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.*;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.model.ValidationResult;
import ua.edu.teacherlicence.achievement.repository.AchievementRepository;
import ua.edu.teacherlicence.achievement.repository.ValidationResultRepository;
import ua.edu.teacherlicence.ai.dto.ClassificationResult;
import ua.edu.teacherlicence.ai.service.AchievementClassifierService;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.teacher.model.CareerRecord;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.CareerRecordRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementValidationService {

    private final AchievementRepository achievementRepository;
    private final ValidationResultRepository validationResultRepository;
    private final PublicationRepository publicationRepository;

    // Structured ppData repositories (Phase 6)
    private final ua.edu.teacherlicence.publication.service.PublicationClassifier publicationClassifier;
    private final ScientificSupervisionRepository scientificSupervisionRepository;
    private final AttestationActivityRepository attestationActivityRepository;
    private final EditorialActivityRepository editorialActivityRepository;
    private final ExpertCouncilRepository expertCouncilRepository;
    private final InternationalProjectRepository internationalProjectRepository;
    private final ScientificConsultingRepository scientificConsultingRepository;
    private final ForeignLanguageTeachingRepository foreignLanguageTeachingRepository;
    private final OlympiadGuidanceRepository olympiadGuidanceRepository;
    private final MilitaryMissionRepository militaryMissionRepository;
    private final ProfessionalAssociationRepository professionalAssociationRepository;
    private final PracticalExperienceRepository practicalExperienceRepository;
    private final CareerRecordRepository careerRecordRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository academicDegreeRepository;

    /** Період відповідності: досягнення мають бути не старші за N років */
    private static final int COMPLIANCE_PERIOD_YEARS = 5;

    @Setter(onMethod_ = @Autowired(required = false))
    private AchievementClassifierService classifierService;

    /**
     * Optional AI-сервіс для перевірки чи практичний досвід (пп.20) відповідає
     * напряму діяльності кафедри. У dev (без ai.enabled) — null, тоді checkPp20
     * fallback'ить на старий критерій (тільки isPedagogicalOrScientific).
     */
    @Setter(onMethod_ = @Autowired(required = false))
    private ua.edu.teacherlicence.ai.service.QualificationMatchAiService qualificationMatchAiService;

    private int getCutoffYear() {
        return LocalDate.now().getYear() - COMPLIANCE_PERIOD_YEARS;
    }

    private LocalDate getCutoffDate() {
        return LocalDate.now().minusYears(COMPLIANCE_PERIOD_YEARS);
    }

    /**
     * Перевірка чи публікація не старша за 5 років.
     * Використовує publicationDate (точна дата); fallback на year.
     */
    private static boolean isFreshPublication(Publication p, LocalDate cutoffDate) {
        LocalDate d = p.effectiveDate();
        return d != null && !d.isBefore(cutoffDate);
    }

    public boolean isAiAvailable() {
        return classifierService != null;
    }

    // ==================== MAIN VALIDATE ====================

    /**
     * Гібридна перевірка: детерміністичний підрахунок + AI аналіз тексту.
     * Працює навіть якщо AI недоступний (тоді тільки детерміністика).
     * Групує досягнення по викладачах — кожен викладач отримує свою сесію.
     */
    @Transactional
    public AchievementValidationResponse validate(AchievementValidationRequest request) {
        List<Achievement> achievements = loadAchievements(request);
        if (achievements.isEmpty()) {
            return AchievementValidationResponse.builder()
                    .totalValidated(0).fulfilledCount(0).notFulfilledCount(0)
                    .suggestions(List.of()).build();
        }

        // Групуємо досягнення по викладачах
        Map<Long, List<Achievement>> byTeacher = new LinkedHashMap<>();
        for (Achievement a : achievements) {
            Long tid = a.getTeacher() != null ? a.getTeacher().getId() : 0L;
            byTeacher.computeIfAbsent(tid, k -> new ArrayList<>()).add(a);
        }

        // AI аналіз тексту (якщо доступний) — один виклик для всіх
        Map<Long, AchievementClassifierService.FulfillmentItem> aiResults = new HashMap<>();
        if (classifierService != null) {
            try {
                aiResults = runAiAnalysis(achievements);
            } catch (Exception e) {
                log.warn("AI analysis failed, using deterministic only: {}", e.getMessage());
            }
        }

        List<AchievementValidationSuggestion> allSuggestions = new ArrayList<>();
        int totalFulfilled = 0;
        String lastSessionId = "";

        // Валідація окремо для кожного викладача (своя сесія)
        for (Map.Entry<Long, List<Achievement>> entry : byTeacher.entrySet()) {
            List<Achievement> teacherAchievements = entry.getValue();
            Teacher teacher = teacherAchievements.get(0).getTeacher();
            Long teacherIdForPubs = teacher != null ? teacher.getId() : 0L;
            List<Publication> publications = publicationRepository.findByTeacherId(teacherIdForPubs);

            String sessionId = UUID.randomUUID().toString().substring(0, 8);
            lastSessionId = sessionId;

            for (Achievement a : teacherAchievements) {
                String teacherName = formatTeacherName(a);
                int ppNum = a.getAchievementType().getNumber();
                String desc = a.getDescription() != null ? a.getDescription() : "";
                String descPreview = desc.length() > 200 ? desc.substring(0, 200) : (desc.isEmpty() ? a.getTitle() : desc);

                // 1. Детерміністичний підрахунок
                DeterministicResult detResult = checkDeterministic(ppNum, desc, publications, teacher);

                // 2. AI результат (якщо є)
                AchievementClassifierService.FulfillmentItem aiItem = aiResults.get(a.getId());

                // 3. Комбінований результат
                int requiredCount = detResult.requiredCount;
                int currentCount = detResult.currentCount;
                StringBuilder reasoningBuilder = new StringBuilder();

                reasoningBuilder.append(detResult.reasoning);

                if (aiItem != null && aiItem.type() > 0) {
                    if (aiItem.currentCount() > currentCount) {
                        currentCount = aiItem.currentCount();
                    }
                    reasoningBuilder.append(" | 🤖 AI: ").append(aiItem.reasoning());
                    if (!aiItem.matchesType()) {
                        reasoningBuilder.append(" ⚠️ AI вважає, що зміст НЕ відповідає цьому підпункту!");
                    }
                }

                if (!detResult.recommendation.isEmpty()) {
                    reasoningBuilder.append(" | 💡 ").append(detResult.recommendation);
                }

                // 4. Перевірка 5-річного терміну для досягнення
                if (a.getDateAchieved() != null) {
                    LocalDate cutoff = LocalDate.now().minusYears(COMPLIANCE_PERIOD_YEARS);
                    if (a.getDateAchieved().isBefore(cutoff)) {
                        reasoningBuilder.append(" | ⏰ УВАГА: досягнення від ")
                                .append(a.getDateAchieved())
                                .append(" — старше ").append(COMPLIANCE_PERIOD_YEARS).append(" років!");
                    }
                } else if (desc.isEmpty()) {
                    reasoningBuilder.append(" | ℹ️ Дата досягнення не вказана");
                }

                String reasoning = reasoningBuilder.toString();

                double progress = requiredCount > 0 ? Math.min(1.0, (double) currentCount / requiredCount) : 0.0;
                boolean fulfilled = currentCount >= requiredCount;

                if (fulfilled) {
                    totalFulfilled++;
                    a.setVerified(true);
                    a.setVerifiedBy("AI+DB");
                    achievementRepository.save(a);
                }

                // Зберігаємо результат із правильним teacher та sessionId
                ValidationResult vr = ValidationResult.builder()
                        .sessionId(sessionId)
                        .teacher(teacher)
                        .achievement(a)
                        .ppNumber(ppNum)
                        .fulfilled(fulfilled)
                        .currentCount(currentCount)
                        .requiredCount(requiredCount)
                        .progress(progress)
                        .reasoning(reasoning)
                        .descriptionPreview(descPreview)
                        .build();
                validationResultRepository.save(vr);

                allSuggestions.add(AchievementValidationSuggestion.builder()
                        .achievementId(a.getId())
                        .teacherName(teacherName)
                        .achievementType(a.getAchievementType().name())
                        .ppNumber(ppNum)
                        .currentCount(currentCount)
                        .requiredCount(requiredCount)
                        .progress(progress)
                        .fulfilled(fulfilled)
                        .reasoning(reasoning)
                        .descriptionPreview(descPreview)
                        .build());
            }

            log.info("Validation teacher={}: {} achievements, session={}",
                    teacherIdForPubs, teacherAchievements.size(), sessionId);
        }

        int notFulfilled = allSuggestions.size() - totalFulfilled;
        log.info("Validation total: {} achievements, {} teachers, {} fulfilled, {} not",
                achievements.size(), byTeacher.size(), totalFulfilled, notFulfilled);

        return AchievementValidationResponse.builder()
                .totalValidated(achievements.size())
                .fulfilledCount(totalFulfilled)
                .notFulfilledCount(notFulfilled)
                .sessionId(byTeacher.size() == 1 ? lastSessionId : "multi-" + byTeacher.size())
                .suggestions(allSuggestions)
                .build();
    }

    // ==================== AI ANALYSIS ====================

    private Map<Long, AchievementClassifierService.FulfillmentItem> runAiAnalysis(
            List<Achievement> achievements) {
        // Формуємо описи для AI
        List<String> descriptions = achievements.stream()
                .map(a -> String.format("[пп.%d] %s",
                        a.getAchievementType().getNumber(),
                        a.getDescription() != null ? a.getDescription() : a.getTitle()))
                .collect(Collectors.toList());

        List<AchievementClassifierService.FulfillmentItem> aiResults =
                classifierService.checkFulfillment(descriptions);

        Map<Long, AchievementClassifierService.FulfillmentItem> map = new HashMap<>();
        for (int i = 0; i < achievements.size(); i++) {
            AchievementClassifierService.FulfillmentItem item =
                    (i < aiResults.size()) ? aiResults.get(i) : null;
            if (item != null) {
                map.put(achievements.get(i).getId(), item);
            }
        }
        return map;
    }

    // ==================== DETERMINISTIC CHECKS ====================

    private record DeterministicResult(int currentCount, int requiredCount,
                                       String reasoning, String recommendation) {}

    /**
     * Детерміністичний аналіз: підрахунок з БД + парсинг тексту.
     */
    private DeterministicResult checkDeterministic(int ppNum, String description,
                                                    List<Publication> publications, Teacher teacher) {
        return switch (ppNum) {
            case 1 -> checkPp1Publications(description, publications);
            case 2 -> checkPp2Patents(description, publications);
            case 3 -> checkPp3Textbooks(description, publications);
            case 4 -> checkPp4Methodical(description, publications);
            case 5 -> checkPp5Dissertation(description, teacher);
            case 6 -> checkPp6ScientificSupervision(description, teacher);
            case 7 -> checkPp7Attestation(description, teacher);
            case 8 -> checkPp8Editorial(description, teacher);
            case 9 -> checkPp9ExpertCouncil(description, teacher);
            case 10 -> checkPp10InternationalProject(description, teacher);
            case 11 -> checkPp11Consulting(description, teacher);
            case 12 -> checkPp12Approbation(description, publications);
            case 13 -> checkPp13ForeignLanguage(description, teacher);
            case 14 -> checkPp14OlympiadStudent(description, teacher);
            case 15 -> checkPp15OlympiadSchool(description, teacher);
            case 16 -> checkPp16CombatVeteran(description, teacher);
            case 17 -> checkPp17Peacekeeping(description, teacher);
            case 18 -> checkPp18NatoExercise(description, teacher);
            case 19 -> checkPp19ProfessionalAssociation(description, teacher);
            case 20 -> checkPp20PracticalExperience(description, teacher);
            default -> new DeterministicResult(0, 1, "Невідомий підпункт", "");
        };
    }

    // --- пп.1: ≥5 публікацій (Scopus/WoS/фахові) ---
    private DeterministicResult checkPp1Publications(String desc, List<Publication> pubs) {
        LocalDate cutoffDate = publicationClassifier.getCutoffDate();

        StringBuilder reasoning = new StringBuilder("📄 Публікації (Scopus/WoS/фахові) — пп.1:\n");
        reasoning.append("ℹ️ Правила (п.38(1)): ARTICLE з категорією {SCOPUS, WOS, CATEGORY_A, CATEGORY_B}. "
                + "Дублі за нормалізованою назвою об'єднуються в одну публікацію (від самоплагіату). "
                + "Старіші за ").append(COMPLIANCE_PERIOD_YEARS)
                .append(" років не зараховуються.\n");

        // Свіжі публікації, що задовольняють умови пп.1 (вже content-deduped у filterForPp)
        List<Publication> qualified = publicationClassifier.filterForPp(pubs, 1, cutoffDate);
        // Старі — для діагностики
        List<Publication> oldOnes = pubs.stream()
                .filter(p -> p.getType() == PublicationType.ARTICLE && p.getArticleCategory() != null)
                .filter(p -> !publicationClassifier.isFresh(p, cutoffDate))
                .toList();

        for (Publication p : qualified) {
            reasoning.append(String.format("  ✅ [%s] \"%s\" (%s)\n",
                    p.getArticleCategory(), truncate(p.getTitle() != null ? p.getTitle() : "—", 50),
                    p.getYear() != null ? p.getYear() : "?"));
        }
        for (Publication p : oldOnes) {
            reasoning.append(String.format("  ⏰ [%s] \"%s\" (%d) — старше %d років\n",
                    p.getArticleCategory(), truncate(p.getTitle() != null ? p.getTitle() : "—", 50),
                    p.getYear(), COMPLIANCE_PERIOD_YEARS));
        }

        int dbCount = qualified.size();
        int textCount = countItemsInText(desc);
        int count = dbCount;

        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Усього зараховано (свіжі, унікальні за назвою): %d\n", dbCount));
        if (!oldOnes.isEmpty()) {
            reasoning.append(String.format("   • Не зараховано через вік (>%d років): %d\n",
                    COMPLIANCE_PERIOD_YEARS, oldOnes.size()));
        }
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі знайдено: ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d з %d необхідних", count, 5));

        String rec = count < 5 ? String.format("Потрібно ще %d публікацій (Scopus/WoS/фахові)", 5 - count) : "";
        return new DeterministicResult(count, 5, reasoning.toString(), rec);
    }

    // --- пп.2: 1 патент на винахід АБО ≥5 деклараційних/свідоцтв ---
    private DeterministicResult checkPp2Patents(String desc, List<Publication> pubs) {
        LocalDate cutoffDate = publicationClassifier.getCutoffDate();
        StringBuilder reasoning = new StringBuilder("📜 Патенти / свідоцтва — пп.2:\n");
        reasoning.append(String.format(
                "ℹ️ Правила (п.38(2)): 1 patent на винахід (PATENT) виконує вимогу; інакше — "
                        + "≥5 деклараційних патентів/корисних моделей (DECLARATIVE_PATENT) або свідоцтв авторського права (COPYRIGHT). "
                        + "Старіші за %d років не зараховуються.\n",
                COMPLIANCE_PERIOD_YEARS));

        // Beruемо лише ті, що зараховуються (через classifier).
        List<Publication> qualified = publicationClassifier.filterForPp(pubs, 2, cutoffDate);
        int dbInventionPatents = 0;
        int dbDeclarative = 0;
        int dbCopyright = 0;
        for (Publication p : qualified) {
            switch (p.getType()) {
                case PATENT -> dbInventionPatents++;
                case DECLARATIVE_PATENT -> dbDeclarative++;
                case COPYRIGHT -> dbCopyright++;
                default -> { /* unreachable */ }
            }
        }
        // Старі — для діагностики
        int oldSkipped = (int) pubs.stream()
                .filter(p -> p.getType() == PublicationType.PATENT
                        || p.getType() == PublicationType.DECLARATIVE_PATENT
                        || p.getType() == PublicationType.COPYRIGHT)
                .filter(p -> !publicationClassifier.isFresh(p, cutoffDate))
                .count();
        boolean hasInventionPatent = dbInventionPatents > 0;
        int declarativeAndCert = dbDeclarative + dbCopyright;

        // Діагностика тексту (НЕ використовується для підрахунку — БД primary).
        boolean textHasInventionPatent = Pattern.compile(
                "(?i)патент\\s+на\\s+винахід(?!.*деклараційн)").matcher(desc).find();
        int textDeclarative = countMatches(desc,
                "(?i)деклараційн\\S*\\s+патент|патент\\s+на\\s+корисну\\s+модель");
        int textCertificates = countMatches(desc,
                "(?i)свідоцтв[оа]\\s+(?:про\\s+)?(?:реєстрацію\\s+)?(?:авторськ|авторського)");

        int count;
        int required;
        if (hasInventionPatent) {
            count = 1;
            required = 1;
            reasoning.append(String.format("  ✅ Знайдено патент на винахід у БД — зараховано (потрібно 1)\n"));
        } else {
            count = declarativeAndCert;
            required = 5;
            if (dbDeclarative > 0) reasoning.append(String.format("  ✅ Деклараційних патентів у БД: %d\n", dbDeclarative));
            if (dbCopyright > 0) reasoning.append(String.format("  ✅ Свідоцтв авторського права у БД: %d\n", dbCopyright));
        }

        if (oldSkipped > 0) {
            reasoning.append(String.format("  ⏰ Не зараховано через вік (>%d років): %d\n",
                    COMPLIANCE_PERIOD_YEARS, oldSkipped));
        }

        if (!hasInventionPatent && (textHasInventionPatent || textDeclarative > 0 || textCertificates > 0)) {
            reasoning.append("  ⚠️ В описі знайдено згадки патентів/свідоцтв (");
            if (textHasInventionPatent) reasoning.append("патент на винахід; ");
            if (textDeclarative > 0) reasoning.append("деклараційних: ").append(textDeclarative).append("; ");
            if (textCertificates > 0) reasoning.append("свідоцтв: ").append(textCertificates).append("; ");
            reasoning.append(") — НЕ використовується для підрахунку (треба внести у publications як PATENT/DECLARATIVE_PATENT/COPYRIGHT).\n");
        }

        reasoning.append(String.format("Усього: %d з %d необхідних", count, required));
        String rec = count < required ? "Потрібно: 1 патент на винахід АБО 5 деклараційних патентів/свідоцтв (внести у publications)" : "";
        return new DeterministicResult(count, required, reasoning.toString(), rec);
    }

    // --- пп.3: підручник/посібник/монографія (НЕ методичні праці — вони в пп.4) ---
    private DeterministicResult checkPp3Textbooks(String desc, List<Publication> pubs) {
        LocalDate cutoffDate = publicationClassifier.getCutoffDate();
        StringBuilder reasoning = new StringBuilder("📚 Підручники / посібники / монографії — пп.3:\n");
        reasoning.append(String.format(
                "ℹ️ Правила (п.38(3)): підручник, навчальний посібник, монографія (≥5 авт. аркушів). "
                        + "TEXTBOOK/STUDY_GUIDE з методичними keywords у заголовку → пп.4. "
                        + "Старіші за %d років не зараховуються.\n",
                COMPLIANCE_PERIOD_YEARS));

        List<Publication> qualified = publicationClassifier.filterForPp(pubs, 3, cutoffDate);
        for (Publication p : qualified) {
            reasoning.append(String.format("  ✅ [%s] \"%s\" (%s) — зараховано\n",
                    p.getType(), truncate(p.getTitle() != null ? p.getTitle() : "—", 60),
                    p.getYear() != null ? p.getYear().toString() : "?"));
        }
        // Діагностика — методичні (інша секція) і старі
        int methodicalSkipped = (int) pubs.stream()
                .filter(p -> (p.getType() == PublicationType.TEXTBOOK
                              || p.getType() == PublicationType.STUDY_GUIDE)
                        && publicationClassifier.isMethodicalWork(p.getTitle()))
                .count();
        int oldSkipped = (int) pubs.stream()
                .filter(p -> p.getType() == PublicationType.TEXTBOOK
                          || p.getType() == PublicationType.STUDY_GUIDE
                          || p.getType() == PublicationType.MONOGRAPH)
                .filter(p -> !publicationClassifier.isFresh(p, cutoffDate))
                .count();
        for (Publication p : pubs) {
            if ((p.getType() == PublicationType.TEXTBOOK || p.getType() == PublicationType.STUDY_GUIDE)
                    && publicationClassifier.isMethodicalWork(p.getTitle())) {
                reasoning.append(String.format("  ❌ \"%s\" (%s) — методична праця (→ пп.4)\n",
                        truncate(p.getTitle() != null ? p.getTitle() : "—", 60),
                        p.getYear() != null ? p.getYear().toString() : "?"));
            }
        }
        int dbCount = qualified.size();
        int rawCount = (int) pubs.stream().filter(p -> p.getType() == PublicationType.TEXTBOOK
                                                    || p.getType() == PublicationType.STUDY_GUIDE
                                                    || p.getType() == PublicationType.MONOGRAPH).count();

        // З тексту опису: рахуємо окремо підручники, посібники (не методичні), монографії
        int textTextbooks = countMatches(desc, "(?i)підручник");
        int textManuals = countMatches(desc, "(?i)(?:навчальн\\S*\\s+)?посібник(?!.*(?:методичн|самостійн))");
        int textMonographs = countMatches(desc, "(?i)монограф");
        int textCount = textTextbooks + textManuals + textMonographs;

        // БД — ЄДИНЕ джерело правди. countMatches на описі давав хибно завищений textCount
        // (знаходить ключові слова в назвах публікацій + у заголовку composePp3-секції
        // "X підручників/посібників/монографій") → подвійний/потрійний обрахунок.
        int count = dbCount;

        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Усього кандидатів у БД (підручники/посібники/монографії): %d\n", rawCount));
        if (methodicalSkipped > 0) {
            reasoning.append(String.format("   • Пропущено як методичні праці (→ пп.4): %d\n", methodicalSkipped));
        }
        if (oldSkipped > 0) {
            reasoning.append(String.format("   • Пропущено через вік (>%d років): %d\n",
                    COMPLIANCE_PERIOD_YEARS, oldSkipped));
        }
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): підручників=%d, посібників=%d, монографій=%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textTextbooks, textManuals, textMonographs));
        }
        reasoning.append(String.format("Усього зараховано: %d з %d необхідних", count, 1));

        String rec = count < 1 ? "Потрібно: виданий підручник/посібник (≥5 авт. аркушів) або монографія" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    /** Чи є публікація справжнім підручником/посібником (а не методичною працею) */
    private boolean isRealTextbook(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return (lower.contains("підручник") || lower.contains("посібник") || lower.contains("монограф"))
                && !isMethodicalWork(lower);
    }

    /** Обрізає текст до maxLen символів */
    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // --- пп.4: ≥3 навчально-методичних праць ---
    private DeterministicResult checkPp4Methodical(String desc, List<Publication> pubs) {
        LocalDate cutoffDate = publicationClassifier.getCutoffDate();
        StringBuilder reasoning = new StringBuilder("📝 Навчально-методичні праці — пп.4:\n");
        reasoning.append(String.format(
                "ℹ️ Правила (п.38(4)): METHODICAL + TEXTBOOK/STUDY_GUIDE з методичними keywords "
                        + "у заголовку (практикум, конспект, РПНД, методичні вказівки, е-курс, для самост. роботи). "
                        + "Старіші за %d років не зараховуються.\n",
                COMPLIANCE_PERIOD_YEARS));

        List<Publication> qualified = publicationClassifier.filterForPp(pubs, 4, cutoffDate);
        for (Publication p : qualified) {
            reasoning.append(String.format("  ✅ [%s] \"%s\" (%s)\n",
                    p.getType(),
                    truncate(p.getTitle() != null ? p.getTitle() : "—", 60),
                    p.getYear() != null ? p.getYear().toString() : "?"));
        }
        int dbCount = qualified.size();
        int rawCount = (int) pubs.stream().filter(p -> p.getType() == PublicationType.METHODICAL
                        || ((p.getType() == PublicationType.TEXTBOOK
                              || p.getType() == PublicationType.STUDY_GUIDE)
                            && publicationClassifier.isMethodicalWork(p.getTitle())))
                .count();
        int oldSkipped = (int) pubs.stream().filter(p -> p.getType() == PublicationType.METHODICAL
                        || ((p.getType() == PublicationType.TEXTBOOK
                              || p.getType() == PublicationType.STUDY_GUIDE)
                            && publicationClassifier.isMethodicalWork(p.getTitle())))
                .filter(p -> !publicationClassifier.isFresh(p, cutoffDate))
                .count();

        // З тексту опису: рахуємо окремі типи методичних праць
        // countWithNumericPrefix враховує "2 РПНД", "3 практикуми" тощо
        int praktikum = countWithNumericPrefix(desc, "практикум");
        int konspekt = countWithNumericPrefix(desc, "конспект\\s+лекцій");
        int rpnd = countWithNumericPrefix(desc, "(?:РПНД|робоч\\S*\\s+програм)");
        int metod = countWithNumericPrefix(desc, "методичн\\S*\\s+(?:посібник|вказівк|рекомендац|розробк)");
        int ecourse = countWithNumericPrefix(desc, "(?:електронн\\S*|дистанційн\\S*)\\s+(?:курс|навчальн)");
        int selfStudy = countWithNumericPrefix(desc, "для\\s+самостійної\\s+роботи");

        int textCount = praktikum + konspekt + rpnd + metod + ecourse + selfStudy;

        // БД — ЄДИНЕ джерело правди. Завищення відбувалося коли keyword-парсер знаходив
        // "посібник"/"конспект" у назвах публікацій + у заголовку composePp4-секції.
        int count = dbCount;

        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Усього кандидатів у БД: %d\n", rawCount));
        if (oldSkipped > 0) {
            reasoning.append(String.format("   • Не зараховано через вік (>%d років): %d\n",
                    COMPLIANCE_PERIOD_YEARS, oldSkipped));
        }
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            List<String> parts = new ArrayList<>();
            if (praktikum > 0) parts.add("практикумів=" + praktikum);
            if (konspekt > 0) parts.add("конспектів лекцій=" + konspekt);
            if (rpnd > 0) parts.add("РПНД/робочих програм=" + rpnd);
            if (metod > 0) parts.add("методичних посібників=" + metod);
            if (ecourse > 0) parts.add("е-курсів=" + ecourse);
            if (selfStudy > 0) parts.add("для самост. роботи=" + selfStudy);
            reasoning.append(String.format("   • В описі (keyword-парсинг): %s — разом %d — НЕ використовується для підрахунку (лише діагностика)\n",
                    String.join(", ", parts), textCount));
        }
        reasoning.append(String.format("Усього зараховано: %d з %d необхідних", count, 3));

        String rec = count < 3
                ? String.format("Потрібно ще %d навчально-методичних праць (посібники, е-курси, конспекти, РПНД)", 3 - count)
                : "";
        return new DeterministicResult(count, 3, reasoning.toString(), rec);
    }

    // --- пп.5: Захист дисертації (перевірка профілю + текст) ---
    private DeterministicResult checkPp5Dissertation(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🎓 Захист дисертації:\n");
        reasoning.append(String.format(
                "ℹ️ Правила підрахунку: один зі ступенів викладача (academic_degrees) має бути "
                        + "захищений у межах останніх %d років. Без дати — зараховуємо «на користь викладача».\n",
                COMPLIANCE_PERIOD_YEARS));
        int count = 0;

        // 1. Перевіряємо ВСІ ступені у списку academic_degrees.
        //    Хоча б один свіжий (≤5 років) → зараховано.
        if (teacher != null) {
            LocalDate cutoff = LocalDate.now().minusYears(COMPLIANCE_PERIOD_YEARS);
            var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());

            for (var d : degrees) {
                if (d.getDiplomaDate() != null
                        && (d.getDiplomaDate().isAfter(cutoff) || d.getDiplomaDate().isEqual(cutoff))) {
                    count = 1;
                    reasoning.append(String.format("  ✅ %s, диплом %s (від %s)",
                            d.getDegree() != null ? d.getDegree() : "—",
                            d.getDiploma() != null ? d.getDiploma() : "—",
                            d.getDiplomaDate()));
                    if (d.getDissertationTopic() != null) {
                        reasoning.append(String.format(", тема: \"%s\"", truncate(d.getDissertationTopic(), 60)));
                    }
                    reasoning.append("\n");
                    break;
                } else if (d.getDiplomaDate() != null) {
                    reasoning.append(String.format("  ⏰ %s, диплом від %s — старше %d років\n",
                            d.getDegree() != null ? d.getDegree() : "—",
                            d.getDiplomaDate(), COMPLIANCE_PERIOD_YEARS));
                } else if (count == 0 && d.getDegree() != null && !d.getDegree().isBlank()) {
                    // Дата відсутня — за замовчуванням зараховуємо (не штрафуємо за неповноту даних)
                    count = 1;
                    reasoning.append(String.format("  ❓ %s (дата диплому не вказана — зараховано)\n",
                            d.getDegree()));
                }
            }
        }

        // Текст-fallback видалено: дисертація має бути в academic_degrees таблиці.
        // Якщо її немає — це сигнал про неповні дані, а не привід зарахувати на основі тексту.
        boolean textFound = containsAny(desc, List.of("дисертац", "дис.", "Ph.D", "PhD", "канд", "докт"));
        if (textFound && count == 0) {
            reasoning.append("  ⚠️ В описі є згадка про дисертацію, але немає запису в academic_degrees — НЕ зараховано (треба внести дані у профіль).\n");
        }

        String rec = count < 1 ? "Потрібно: документ про захист дисертації (не старше 5 років) — внести в academic_degrees" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.6: Наукове керівництво (структурована таблиця + текст) ---
    private DeterministicResult checkPp6ScientificSupervision(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🔬 Наукове керівництво:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з scientific_supervision (керівник захищеного "
                + "здобувача — кандидат/доктор/PhD). Достатньо ≥1 записа.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<ScientificSupervision> records = scientificSupervisionRepository.findByTeacherId(teacher.getId());
            for (ScientificSupervision s : records) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s — %s (%s, %s)\n",
                        s.getStudentName() != null ? s.getStudentName() : "—",
                        s.getDegreeType() != null ? s.getDegreeType().name() : "?",
                        s.getTopic() != null ? truncate(s.getTopic(), 50) : "—",
                        s.getDefenseDate() != null ? s.getDefenseDate().toString() : "дата не вказана"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("керівниц", "здобувач", "аспірант", "дисертант"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено згадку про наукове керівництво в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження наукового керівництва захищеним здобувачем" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.7: Атестаційна діяльність ---
    // Член постійної спецради (COUNCIL_MEMBER) → 1 запису достатньо.
    // Інакше — потрібно ≥3 разових ролей у сумі: OPPONENT + REVIEWER + CHAIR.
    private DeterministicResult checkPp7Attestation(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("⚖️ Атестаційна діяльність:\n");
        reasoning.append("ℹ️ Правила підрахунку: член постійної спецради — 1 запис достатньо; "
                + "інакше потрібно ≥3 разових ролей (опонент + рецензент + голова в сумі).\n");

        List<AttestationActivity> records = teacher != null
                ? attestationActivityRepository.findByTeacherId(teacher.getId())
                : List.of();

        boolean hasPermanent = false;
        int discreteCount = 0;
        for (AttestationActivity a : records) {
            AttestationRole r = a.getRole();
            if (r == AttestationRole.COUNCIL_MEMBER) {
                hasPermanent = true;
            } else if (r == AttestationRole.OPPONENT
                    || r == AttestationRole.REVIEWER
                    || r == AttestationRole.CHAIR) {
                discreteCount++;
            }
            reasoning.append(String.format("  ✅ %s — %s%s\n",
                    r != null ? roleLabel(r) : "?",
                    a.getCouncilName() != null ? a.getCouncilName() : "—",
                    a.getStudentName() != null ? " (" + a.getStudentName() + ")" : ""));
        }

        // Діагностика тексту (НЕ використовується для підрахунку).
        boolean textOpponent = containsAny(desc, List.of("опонент"));
        boolean textPermanent = containsAny(desc, List.of("постійн", "спеціалізован"));

        int count;
        int required;
        if (hasPermanent) {
            reasoning.append("Членство в постійній спеціалізованій раді (підтверджено в БД)\n");
            required = 1;
            count = 1;
        } else {
            required = 3;
            count = discreteCount;
        }

        if ((textOpponent || textPermanent) && !hasPermanent && discreteCount == 0) {
            reasoning.append("  ⚠️ В описі є згадка опонента/спецради, але немає записів у attestation_activity — НЕ зараховано (треба внести у профіль).\n");
        }

        reasoning.append(String.format("Усього: %d з %d необхідних", count, required));
        String rec = count < required
                ? "Потрібно: член постійної спецради, або ≥3 разових (опонент/рецензент/голова) — внести в attestation_activity"
                : "";
        return new DeterministicResult(count, required, reasoning.toString(), rec);
    }

    /** Українська назва ролі для reasoning. */
    private String roleLabel(AttestationRole r) {
        return switch (r) {
            case OPPONENT -> "офіційний опонент";
            case REVIEWER -> "рецензент";
            case CHAIR -> "голова разової спецради";
            case COUNCIL_MEMBER -> "член постійної спецради";
        };
    }

    // --- пп.8: Редакційна/експертна діяльність (структурована таблиця + текст) ---
    private DeterministicResult checkPp8Editorial(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("📰 Наук. керівник теми/редколегія:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з editorial_activity (член редколегії, головний редактор, "
                + "експерт/рецензент видання, керівник наукової теми). Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<EditorialActivity> records = editorialActivityRepository.findByTeacherId(teacher.getId());
            for (EditorialActivity e : records) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s — %s (%s — %s)\n",
                        e.getRole() != null ? e.getRole().name() : "?",
                        e.getJournalOrProjectName() != null ? truncate(e.getJournalOrProjectName(), 50) : "—",
                        e.getDateFrom() != null ? e.getDateFrom().toString() : "?",
                        e.getDateTo() != null ? e.getDateTo().toString() : "теперішній час"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("керівник теми", "відповідальний виконавець",
                "редколег", "головний редактор", "експерт", "рецензент", "фахов"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження участі як керівника теми, члена редколегії або експерта видання" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.9: Експертна рада МОН/НАЗЯВО (структурована таблиця + текст) ---
    private DeterministicResult checkPp9ExpertCouncil(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🏛️ Експертна рада:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з expert_council (МОН, НАЗЯВО, акредитаційна, НМР тощо). "
                + "Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<ExpertCouncil> records = expertCouncilRepository.findByTeacherId(teacher.getId());
            for (ExpertCouncil e : records) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s (%s) — %s (%s — %s)\n",
                        e.getCouncilName() != null ? e.getCouncilName() : "—",
                        e.getType() != null ? e.getType().name() : "?",
                        e.getRole() != null ? e.getRole() : "—",
                        e.getDateFrom() != null ? e.getDateFrom().toString() : "?",
                        e.getDateTo() != null ? e.getDateTo().toString() : "теперішній час"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("МОН", "НАЗЯВО", "акредитац", "НМР", "експерт", "Держслуж"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження роботи у складі експертної ради МОН/НАЗЯВО/інше" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.10: Міжнародний проект (структурована таблиця + текст) ---
    private DeterministicResult checkPp10InternationalProject(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🌍 Міжнародні проекти:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з international_project (Erasmus+, Horizon, NATO, "
                + "грантові, освітні). Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<InternationalProject> records = internationalProjectRepository.findByTeacherId(teacher.getId());
            for (InternationalProject p : records) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s (%s) — %s (%s — %s)\n",
                        p.getProjectName() != null ? truncate(p.getProjectName(), 50) : "—",
                        p.getProgram() != null ? p.getProgram().name() : "?",
                        p.getRole() != null ? p.getRole() : "—",
                        p.getDateFrom() != null ? p.getDateFrom().toString() : "?",
                        p.getDateTo() != null ? p.getDateTo().toString() : "теперішній час"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("міжнарод", "international", "проект", "грант",
                "Erasmus", "Horizon", "НАТО", "суддя міжнарод"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження участі в міжнародному науковому/освітньому проекті" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.11: ≥3 роки консультування (структурована таблиця + текст) ---
    private DeterministicResult checkPp11Consulting(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🏢 Наукове консультування:\n");
        reasoning.append("ℹ️ Правила підрахунку: сума років консультування з усіх договорів у "
                + "scientific_consulting. Якщо yearsCount не вказано — обчислюємо з dateFrom-dateTo. Норматив: ≥3 років.\n");
        int dbYears = 0;

        if (teacher != null) {
            List<ScientificConsulting> records = scientificConsultingRepository.findByTeacherId(teacher.getId());
            for (ScientificConsulting c : records) {
                int years = c.getYearsCount() != null ? c.getYearsCount() : 0;
                if (years == 0 && c.getDateFrom() != null && c.getDateTo() != null) {
                    years = c.getDateTo().getYear() - c.getDateFrom().getYear();
                }
                dbYears += years;
                reasoning.append(String.format("  ✅ %s (договір %s) — %d років (%s — %s)\n",
                        c.getOrganizationName() != null ? c.getOrganizationName() : "—",
                        c.getContractNumber() != null ? c.getContractNumber() : "—",
                        years,
                        c.getDateFrom() != null ? c.getDateFrom().toString() : "?",
                        c.getDateTo() != null ? c.getDateTo().toString() : "теперішній час"));
            }
        }

        // Діагностика тексту (НЕ використовується для підрахунку).
        int textYears = extractYearsFromText(desc);
        if (textYears > 0 && dbYears == 0) {
            reasoning.append(String.format("  ⚠️ В описі знайдено: %d років, але немає запису в scientific_consulting — НЕ зараховано.\n", textYears));
        }

        // БД — ЄДИНЕ джерело правди.
        int years = dbYears;
        reasoning.append(String.format("Усього: %d років з %d необхідних", years, 3));

        String rec = years < 3 ? "Потрібно: ≥3 років наукового консультування за договором — внести в scientific_consulting" : "";
        return new DeterministicResult(years, 3, reasoning.toString(), rec);
    }

    // --- пп.12: ≥5 апробаційних публікацій ---
    // Кожна публікація рахується окремо. Дубль — тільки за DOI / нормалізованою назвою.
    private DeterministicResult checkPp12Approbation(String desc, List<Publication> pubs) {
        LocalDate cutoffDate = publicationClassifier.getCutoffDate();

        StringBuilder reasoning = new StringBuilder("🎤 Апробаційні / науково-популярні публікації — пп.12:\n");
        reasoning.append(String.format(
                "ℹ️ Правила (п.38(12)): APPROBATION + POPULAR_SCIENTIFIC. Дублі за DOI / нормалізованою "
                        + "назвою об'єднуються в одну публікацію (від самоплагіату). Публікації старше %d років "
                        + "не зараховуються.\n",
                COMPLIANCE_PERIOD_YEARS));

        // Свіжі публікації, що задовольняють пп.12 (вже content-deduped у filterForPp)
        List<Publication> qualified = publicationClassifier.filterForPp(pubs, 12, cutoffDate);
        // Старі — для діагностики
        List<Publication> oldOnes = pubs.stream()
                .filter(p -> p.getType() == PublicationType.APPROBATION
                          || p.getType() == PublicationType.POPULAR_SCIENTIFIC)
                .filter(p -> !publicationClassifier.isFresh(p, cutoffDate))
                .toList();

        for (Publication p : qualified) {
            String title = truncate(p.getTitle() != null ? p.getTitle() : "—", 50);
            reasoning.append(String.format("  ✅ [%s] \"%s\" (%s)\n", p.getType(), title,
                    p.getYear() != null ? p.getYear().toString() : "?"));
        }
        for (Publication p : oldOnes) {
            reasoning.append(String.format("  ⏰ [%s] \"%s\" (%d) — старше %d років\n",
                    p.getType(), truncate(p.getTitle() != null ? p.getTitle() : "—", 50),
                    p.getYear(), COMPLIANCE_PERIOD_YEARS));
        }

        int textCount = countItemsInText(desc);
        int dbCount = qualified.size();
        int count = dbCount;

        int rawTotal = (int) pubs.stream()
                .filter(p -> p.getType() == PublicationType.APPROBATION
                          || p.getType() == PublicationType.POPULAR_SCIENTIFIC)
                .count();
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Усього апробаційних/науково-популярних у БД: %d\n", rawTotal));
        if (!oldOnes.isEmpty()) {
            reasoning.append(String.format("   • Не зараховано через вік (>%d років): %d\n",
                    COMPLIANCE_PERIOD_YEARS, oldOnes.size()));
        }
        reasoning.append(String.format("   • Зараховано (свіжі, унікальні за назвою): %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі знайдено: ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d з %d необхідних", count, 5));

        String rec = count < 5 ? String.format("Потрібно ще %d апробаційних/науково-популярних публікацій", 5 - count) : "";
        return new DeterministicResult(count, 5, reasoning.toString(), rec);
    }

    // --- пп.13: ≥50 годин іноземною (структурована таблиця + текст) ---
    private DeterministicResult checkPp13ForeignLanguage(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🌐 Викладання іноземною мовою:\n");
        reasoning.append("ℹ️ Правила підрахунку: сума аудиторних годин з усіх записів foreign_language_teaching. "
                + "Норматив: ≥50 годин на навчальний рік.\n");
        int dbHours = 0;

        if (teacher != null) {
            List<ForeignLanguageTeaching> records = foreignLanguageTeachingRepository.findByTeacherId(teacher.getId());
            for (ForeignLanguageTeaching f : records) {
                int hours = f.getHours() != null ? f.getHours() : 0;
                dbHours += hours;
                reasoning.append(String.format("  ✅ %s (%s) — %d год. (%s, сем. %s)\n",
                        f.getDisciplineName() != null ? f.getDisciplineName() : "—",
                        f.getLanguage() != null ? f.getLanguage() : "?",
                        hours,
                        f.getAcademicYear() != null ? f.getAcademicYear() : "?",
                        f.getSemester() != null ? f.getSemester().toString() : "?"));
            }
        }

        // Діагностика тексту (НЕ використовується для підрахунку).
        int textHours = extractHoursFromText(desc);
        if (textHours > 0 && dbHours == 0) {
            reasoning.append(String.format("  ⚠️ В описі знайдено: %d год., але немає запису в foreign_language_teaching — НЕ зараховано.\n", textHours));
        }

        // БД — ЄДИНЕ джерело правди.
        int hours = dbHours;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d год.\n", dbHours));
        if (textHours > 0) {
            reasoning.append(String.format("   • В описі (keyword): ~%d год. — НЕ використовується для підрахунку (лише діагностика)\n",
                    textHours));
        }
        reasoning.append(String.format("Усього: %d годин з %d необхідних", hours, 50));

        String rec = hours < 50 ? String.format("Потрібно ще %d аудиторних годин занять іноземною мовою — внести в foreign_language_teaching", 50 - hours) : "";
        return new DeterministicResult(hours, 50, reasoning.toString(), rec);
    }

    // --- пп.14: Олімпіади/конкурси студентів, наукові гуртки, спорт, мистецтво ---
    private DeterministicResult checkPp14OlympiadStudent(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🏆 Олімпіади/конкурси/гуртки студентів:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з olympiad_guidance, окрім тих що рівня SCHOOL "
                + "(вони йдуть у пп.15). Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<OlympiadGuidance> records = olympiadGuidanceRepository.findByTeacherId(teacher.getId());
            // pp.14 = всі записи, окрім SCHOOL-level олімпіад (ті йдуть в pp.15)
            List<OlympiadGuidance> pp14Records = records.stream()
                    .filter(o -> o.getLevel() != OlympiadLevel.SCHOOL)
                    .collect(Collectors.toList());
            for (OlympiadGuidance o : pp14Records) {
                dbCount++;
                String name = o.getOlympiadName() != null ? o.getOlympiadName() :
                        (o.getCompetitionName() != null ? o.getCompetitionName() : "—");
                String actType = o.getActivityType() != null ? o.getActivityType().name() : "?";
                reasoning.append(String.format("  ✅ [%s] %s — %s (%s, %s)\n",
                        actType, name,
                        o.getStudentName() != null ? o.getStudentName() :
                                (o.getParticipantCount() != null ? o.getParticipantCount() + " учасн." : "—"),
                        o.getRole() != null ? o.getRole().name() : "?",
                        o.getYear() != null ? o.getYear().toString() :
                                (o.getAcademicYear() != null ? o.getAcademicYear() : "?")));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("олімпіад", "конкурс", "призов", "переможе",
                "диплом", "оргкомітет", "журі", "гурток", "мистецьк", "спорт", "товариств"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження олімпіади/конкурсу/гуртка/спортивного або мистецького заходу" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.15: Олімпіади школярів/МАН (структурована таблиця + текст) ---
    private DeterministicResult checkPp15OlympiadSchool(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🏆 Олімпіади школярів/МАН:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з olympiad_guidance рівня SCHOOL "
                + "(олімпіади школярів, МАН). Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<OlympiadGuidance> records = olympiadGuidanceRepository.findByTeacherId(teacher.getId());
            List<OlympiadGuidance> schoolRecords = records.stream()
                    .filter(o -> o.getLevel() == OlympiadLevel.SCHOOL)
                    .collect(Collectors.toList());
            for (OlympiadGuidance o : schoolRecords) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s — %s (%s, %s, %s)\n",
                        o.getStudentName() != null ? o.getStudentName() : "—",
                        o.getOlympiadName() != null ? o.getOlympiadName() : "—",
                        o.getRole() != null ? o.getRole().name() : "?",
                        o.getResult() != null ? o.getResult() : "—",
                        o.getYear() != null ? o.getYear().toString() : "?"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("школяр", "учн", "МАН", "олімпіад", "III", "IV", "етап"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження керівництва школярем-призером або участі в журі" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.16: УБД (профіль викладача — без text fallback) ---
    private DeterministicResult checkPp16CombatVeteran(String desc, Teacher teacher) {
        boolean hasUbd = teacher != null && teacher.isCombatVeteranStatus();
        boolean textUbd = containsAny(desc, List.of("УБД", "учасник бойових", "бойових дій", "ветеран"));
        // БД — ЄДИНЕ джерело правди (combatVeteranStatus у профілі).
        int count = hasUbd ? 1 : 0;

        StringBuilder reasoning = new StringBuilder("🎖️ Статус УБД:\n");
        reasoning.append("ℹ️ Правила підрахунку: прапорець combatVeteranStatus у профілі викладача. "
                + "Текст опису НЕ використовується для підрахунку.\n");
        if (hasUbd) {
            reasoning.append("  ✅ Статус УБД підтверджено в профілі викладача");
            if (teacher.getCombatVeteranDoc() != null) {
                reasoning.append(String.format(" (посвідчення %s", teacher.getCombatVeteranDoc()));
                if (teacher.getCombatVeteranDocDate() != null) {
                    reasoning.append(String.format(" від %s", teacher.getCombatVeteranDocDate()));
                }
                reasoning.append(")");
            }
            reasoning.append("\n");
        } else if (textUbd) {
            reasoning.append("  ⚠️ В описі є згадка УБД, але прапорець combatVeteranStatus у профілі = false — НЕ зараховано (треба позначити в профілі).\n");
        } else {
            reasoning.append("  ❌ Статус УБД не підтверджено\n");
        }

        String rec = count < 1 ? "Потрібно: підтверджений статус учасника бойових дій (поставити прапорець у профілі)" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.17: Миротворчі операції ООН (структурована таблиця + текст) ---
    private DeterministicResult checkPp17Peacekeeping(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🕊️ Миротворчі операції ООН:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з military_mission з типом UN_PEACEKEEPING. "
                + "Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<MilitaryMission> records = militaryMissionRepository.findByTeacherId(teacher.getId());
            List<MilitaryMission> peacekeeping = records.stream()
                    .filter(m -> m.getMissionType() == MissionType.UN_PEACEKEEPING)
                    .collect(Collectors.toList());
            for (MilitaryMission m : peacekeeping) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s — %s (%s — %s)\n",
                        m.getMissionName() != null ? m.getMissionName() : "—",
                        m.getCountry() != null ? m.getCountry() : "—",
                        m.getDateFrom() != null ? m.getDateFrom().toString() : "?",
                        m.getDateTo() != null ? m.getDateTo().toString() : "?"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("ООН", "миротворч", "UN", "peacekeep"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження участі в миротворчій операції ООН" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.18: Навчання НАТО (структурована таблиця + текст) ---
    private DeterministicResult checkPp18NatoExercise(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🛡️ Навчання НАТО:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з military_mission з типом NATO_EXERCISE. "
                + "Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<MilitaryMission> records = militaryMissionRepository.findByTeacherId(teacher.getId());
            List<MilitaryMission> nato = records.stream()
                    .filter(m -> m.getMissionType() == MissionType.NATO_EXERCISE)
                    .collect(Collectors.toList());
            for (MilitaryMission m : nato) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s — %s (%s — %s)\n",
                        m.getMissionName() != null ? m.getMissionName() : "—",
                        m.getCountry() != null ? m.getCountry() : "—",
                        m.getDateFrom() != null ? m.getDateFrom().toString() : "?",
                        m.getDateTo() != null ? m.getDateTo().toString() : "?"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("НАТО", "NATO", "навчанн", "тренуванн"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження участі в міжнародних навчаннях НАТО" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.19: Професійне об'єднання (структурована таблиця + текст) ---
    private DeterministicResult checkPp19ProfessionalAssociation(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("🤝 Професійне об'єднання:\n");
        reasoning.append("ℹ️ Правила підрахунку: записи з professional_association (IEEE, ACM, асоціації, "
                + "спілки, товариства). Достатньо ≥1 запису.\n");
        int dbCount = 0;

        if (teacher != null) {
            List<ProfessionalAssociation> records = professionalAssociationRepository.findByTeacherId(teacher.getId());
            for (ProfessionalAssociation a : records) {
                dbCount++;
                reasoning.append(String.format("  ✅ %s — %s (%s — %s)\n",
                        a.getOrganizationName() != null ? a.getOrganizationName() : "—",
                        a.getRole() != null ? a.getRole() : "—",
                        a.getDateFrom() != null ? a.getDateFrom().toString() : "?",
                        a.getDateTo() != null ? a.getDateTo().toString() : "теперішній час"));
            }
        }

        // Fallback: текст
        boolean textFound = containsAny(desc, List.of("об'єднанн", "асоціац", "спілк", "товариств",
                "професійн", "громадськ"));
        int textCount = textFound ? 1 : 0;
        if (textFound && dbCount == 0) {
            reasoning.append("  📝 Знайдено підтвердження в описі\n");
        }

        // БД — ЄДИНЕ джерело правди для підрахунку. Опис (description) генерується композитором
        // і може містити "сміття" (номери сторінок, ID, назви) → keyword-парсинг давав false positives.
        int count = dbCount;
        reasoning.append("📊 Підсумок:\n");
        reasoning.append(String.format("   • Зараховано з БД: %d\n", dbCount));
        if (textCount > 0) {
            reasoning.append(String.format("   • В описі (keyword-парсинг): ~%d — НЕ використовується для підрахунку (лише діагностика)\n",
                    textCount));
        }
        reasoning.append(String.format("Усього: %d", count));

        String rec = count < 1 ? "Потрібно: підтвердження участі у професійному або громадському об'єднанні" : "";
        return new DeterministicResult(count, 1, reasoning.toString(), rec);
    }

    // --- пп.20: ≥5 років практичного досвіду за спеціальністю ---
    // Бере ОДНЕ джерело даних: пріоритет — career_records (Послужний список,
    // зазвичай точніші дати наказів). Якщо career_records порожній — fallback
    // на practical_experience. НЕ ЗМІШУЄ дані з обох таблиць щоб не дублювати періоди.
    // Підрахунок років/місяців точний — через Period.between по merged-інтервалах.
    private DeterministicResult checkPp20PracticalExperience(String desc, Teacher teacher) {
        StringBuilder reasoning = new StringBuilder("💼 Практичний досвід:\n");
        boolean aiAvailable = qualificationMatchAiService != null
                && teacher != null
                && teacher.getDepartment() != null
                && teacher.getDepartment().getName() != null;

        Long deptId = aiAvailable ? teacher.getDepartment().getId() : null;
        String deptName = aiAvailable ? teacher.getDepartment().getName() : null;

        // Вирішуємо яке джерело використовувати
        String sourceTag = "—";
        List<CareerRecord> careerRecords = teacher != null
                ? careerRecordRepository.findByTeacherId(teacher.getId())
                : Collections.emptyList();
        List<PracticalExperience> practicalRecords = teacher != null
                ? practicalExperienceRepository.findByTeacherId(teacher.getId())
                : Collections.emptyList();

        boolean useCareer = !careerRecords.isEmpty();
        boolean usePractical = !useCareer && !practicalRecords.isEmpty();

        reasoning.append("ℹ️ Правила (п.38(20)): береться ОДНЕ джерело даних, не сума, "
                + "щоб не дублювати періоди. ");
        if (useCareer) {
            sourceTag = "career";
            reasoning.append("Джерело: career_records (Послужний список — пріоритет, бо точніші дати наказів). ");
        } else if (usePractical) {
            sourceTag = "pp20";
            reasoning.append("Джерело: practical_experience (Послужний список порожній → fallback на ППДану пп.20). ");
        } else {
            reasoning.append("Джерело: відсутнє (ні career_records, ні practical_experience). ");
        }
        reasoning.append("Виключаються педагогічні/наукові посади. ");
        if (aiAvailable) {
            reasoning.append("AI додатково перевіряє відповідність кожної посади напряму кафедри. ");
        }
        reasoning.append("Інтервали що накладаються — об'єднуються. Норматив: ≥5 років.\n");

        List<Pp20Candidate> accepted = new ArrayList<>();
        int skippedPedagogical = 0;
        int skippedNotProfile = 0;

        if (useCareer) {
            for (CareerRecord cr : careerRecords) {
                String position = cr.getPosition() != null ? cr.getPosition() : "";
                String org = cr.getOrganization() != null ? cr.getOrganization() : "";

                if (isPedagogicalOrScientific(position, org)) {
                    skippedPedagogical++;
                    reasoning.append(String.format("  ⏭ [%s] %s — %s (педагогічна/наукова)\n",
                            sourceTag, org.isEmpty() ? "—" : org, position.isEmpty() ? "—" : position));
                    continue;
                }
                if (aiAvailable
                        && !qualificationMatchAiService.checkPracticalExperienceMatch(
                                position, org, /*specialty*/ null, deptId, deptName)) {
                    skippedNotProfile++;
                    reasoning.append(String.format("  ⏭ [%s] %s — %s (AI: не за профілем кафедри)\n",
                            sourceTag, org.isEmpty() ? "—" : org, position.isEmpty() ? "—" : position));
                    continue;
                }
                LocalDate start = cr.getStartDate();
                LocalDate end = cr.getEndDate() != null ? cr.getEndDate() : LocalDate.now();
                if (start == null || !end.isAfter(start)) continue;
                accepted.add(new Pp20Candidate(sourceTag, org, position, start, end, false));
            }
        } else if (usePractical) {
            for (var pe : practicalRecords) {
                String position = pe.getPosition() != null ? pe.getPosition() : "";
                String org = pe.getOrganizationName() != null ? pe.getOrganizationName() : "";
                String specialty = pe.getSpecialtyName();

                if (isPedagogicalOrScientific(position, org)) {
                    skippedPedagogical++;
                    reasoning.append(String.format("  ⏭ [%s] %s — %s (педагогічна/наукова)\n",
                            sourceTag, org.isEmpty() ? "—" : org, position.isEmpty() ? "—" : position));
                    continue;
                }
                if (aiAvailable
                        && !qualificationMatchAiService.checkPracticalExperienceMatch(
                                position, org, specialty, deptId, deptName)) {
                    skippedNotProfile++;
                    reasoning.append(String.format("  ⏭ [%s] %s — %s (AI: не за профілем кафедри)\n",
                            sourceTag, org.isEmpty() ? "—" : org, position.isEmpty() ? "—" : position));
                    continue;
                }
                LocalDate start = pe.getDateFrom();
                LocalDate end = pe.getDateTo() != null ? pe.getDateTo() : LocalDate.now();
                if (start == null) {
                    if (pe.getYearsCount() != null && pe.getYearsCount() > 0) {
                        LocalDate synEnd = LocalDate.now();
                        LocalDate synStart = synEnd.minusYears(pe.getYearsCount());
                        accepted.add(new Pp20Candidate(sourceTag, org, position, synStart, synEnd, true));
                    }
                    continue;
                }
                if (!end.isAfter(start)) continue;
                accepted.add(new Pp20Candidate(sourceTag, org, position, start, end, false));
            }
        }

        int totalSkipped = skippedPedagogical + skippedNotProfile;
        if (accepted.isEmpty() && totalSkipped > 0) {
            reasoning.append(String.format(
                    "  ⚠ Усі знайдені записи відсіяні: %d пед./наук., %d не за профілем кафедри\n",
                    skippedPedagogical, skippedNotProfile));
        }

        // Сортуємо за start ASC для стабільного відображення
        accepted.sort(Comparator.comparing(c -> c.start));

        // Reasoning ✅ записів
        List<long[]> intervals = new ArrayList<>();
        for (Pp20Candidate c : accepted) {
            String orgDisplay = c.org.isEmpty() ? "—" : c.org;
            String posDisplay = c.position.isEmpty() ? "—" : c.position;
            java.time.Period p = java.time.Period.between(c.start, c.end);
            reasoning.append(String.format(
                    "  ✅ [%s] %s — %s (%s — %s, %d р. %d міс.)%s\n",
                    c.tag, orgDisplay, posDisplay, c.start, c.end,
                    p.getYears(), p.getMonths(),
                    c.synthetic ? " [синтетична дата з yearsCount]" : ""));
            intervals.add(new long[]{c.start.toEpochDay(), c.end.toEpochDay()});
        }

        // Merge overlapping/touching intervals → точний підсумок через Period
        var merged = mergeIntervals(intervals);
        java.time.Period total = java.time.Period.ZERO;
        for (long[] iv : merged) {
            LocalDate s = LocalDate.ofEpochDay(iv[0]);
            LocalDate e = LocalDate.ofEpochDay(iv[1]);
            total = total.plus(java.time.Period.between(s, e));
        }
        int totalDays = total.getDays();
        int totalMonths = total.getMonths() + totalDays / 30;
        totalDays = totalDays % 30;
        int totalYears = total.getYears() + totalMonths / 12;
        totalMonths = totalMonths % 12;

        // Fallback на текст
        int textYears = extractYearsFromText(desc);
        if (totalYears == 0 && totalMonths == 0 && textYears > 0) {
            totalYears = textYears;
            totalMonths = 0;
            reasoning.append(String.format(
                    "  📝 В описі знайдено: %d років практичного досвіду. БД порожня → використано текст як fallback.\n",
                    textYears));
        }

        reasoning.append(String.format("Усього: %d р. %d міс. з 5 необхідних років",
                totalYears, totalMonths));

        String rec = totalYears < 5
                ? "Потрібно: ≥5 років практичного досвіду за спеціальністю (крім педагогічної, наукової)"
                : "";
        return new DeterministicResult(totalYears, 5, reasoning.toString(), rec);
    }

    /** Внутрішня структура для аудиту записів пп.20. */
    private static final class Pp20Candidate {
        final String tag;          // "career" або "pp20" — джерело даних
        final String org;
        final String position;
        final LocalDate start;
        final LocalDate end;
        final boolean synthetic;   // інтервал відновлений з yearsCount (без точних дат)

        Pp20Candidate(String tag, String org, String position,
                      LocalDate start, LocalDate end, boolean synthetic) {
            this.tag = tag;
            this.org = org;
            this.position = position;
            this.start = start;
            this.end = end;
            this.synthetic = synthetic;
        }
    }

    /**
     * DTO зарахованого запису пп.20 — зовнішнє API для composer/експорту.
     * Містить тільки ті записи, що пройшли всі фільтри (педагогічні + AI-профіль).
     */
    public record ValidPp20Record(
            String source,        // "career" або "pp20"
            String organization,
            String position,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean synthetic) {}

    /**
     * Повертає список ✅ записів пп.20 (тільки прийняті фільтрами).
     * Використовує одне джерело: career_records → fallback на practical_experience.
     * Викликається з AchievementComposer для побудови опису та з експорту
     * щоб не показувати педагогічні/непрофільні записи.
     */
    public List<ValidPp20Record> getValidPp20Records(Teacher teacher) {
        if (teacher == null) return List.of();

        boolean aiAvailable = qualificationMatchAiService != null
                && teacher.getDepartment() != null
                && teacher.getDepartment().getName() != null;
        Long deptId = aiAvailable ? teacher.getDepartment().getId() : null;
        String deptName = aiAvailable ? teacher.getDepartment().getName() : null;

        List<CareerRecord> careerRecords = careerRecordRepository.findByTeacherId(teacher.getId());
        List<PracticalExperience> practicalRecords = practicalExperienceRepository.findByTeacherId(teacher.getId());
        boolean useCareer = !careerRecords.isEmpty();
        boolean usePractical = !useCareer && !practicalRecords.isEmpty();
        if (!useCareer && !usePractical) return List.of();

        List<ValidPp20Record> result = new ArrayList<>();
        if (useCareer) {
            for (CareerRecord cr : careerRecords) {
                String position = cr.getPosition() != null ? cr.getPosition() : "";
                String org = cr.getOrganization() != null ? cr.getOrganization() : "";
                if (isPedagogicalOrScientific(position, org)) continue;
                if (aiAvailable && !qualificationMatchAiService.checkPracticalExperienceMatch(
                        position, org, null, deptId, deptName)) continue;
                LocalDate start = cr.getStartDate();
                LocalDate end = cr.getEndDate() != null ? cr.getEndDate() : LocalDate.now();
                if (start == null || !end.isAfter(start)) continue;
                result.add(new ValidPp20Record("career", org, position, start, end, false));
            }
        } else {
            for (PracticalExperience pe : practicalRecords) {
                String position = pe.getPosition() != null ? pe.getPosition() : "";
                String org = pe.getOrganizationName() != null ? pe.getOrganizationName() : "";
                String specialty = pe.getSpecialtyName();
                if (isPedagogicalOrScientific(position, org)) continue;
                if (aiAvailable && !qualificationMatchAiService.checkPracticalExperienceMatch(
                        position, org, specialty, deptId, deptName)) continue;
                LocalDate start = pe.getDateFrom();
                LocalDate end = pe.getDateTo() != null ? pe.getDateTo() : LocalDate.now();
                if (start == null) {
                    if (pe.getYearsCount() != null && pe.getYearsCount() > 0) {
                        LocalDate synEnd = LocalDate.now();
                        LocalDate synStart = synEnd.minusYears(pe.getYearsCount());
                        result.add(new ValidPp20Record("pp20", org, position, synStart, synEnd, true));
                    }
                    continue;
                }
                if (!end.isAfter(start)) continue;
                result.add(new ValidPp20Record("pp20", org, position, start, end, false));
            }
        }
        result.sort(Comparator.comparing(ValidPp20Record::dateFrom));
        return result;
    }

    /**
     * Merge overlapping intervals → список merged-інтервалів (для точного Period-обчислення).
     */
    private List<long[]> mergeIntervals(List<long[]> intervals) {
        if (intervals == null || intervals.isEmpty()) return List.of();
        List<long[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingLong(a -> a[0]));
        List<long[]> merged = new ArrayList<>();
        long curStart = sorted.get(0)[0];
        long curEnd = sorted.get(0)[1];
        for (int i = 1; i < sorted.size(); i++) {
            long[] iv = sorted.get(i);
            if (iv[0] <= curEnd) {
                if (iv[1] > curEnd) curEnd = iv[1];
            } else {
                merged.add(new long[]{curStart, curEnd});
                curStart = iv[0];
                curEnd = iv[1];
            }
        }
        merged.add(new long[]{curStart, curEnd});
        return merged;
    }

    /**
     * Визначає, чи є посада педагогічною, науково-педагогічною або науковою.
     * Такі посади НЕ зараховуються до пп.20.
     */
    private boolean isPedagogicalOrScientific(String position, String organization) {
        String lower = (position + " " + organization).toLowerCase();
        // Педагогічні / науково-педагогічні посади
        List<String> pedagogicalKeywords = List.of(
                "викладач", "доцент", "професор", "завідувач кафедр", "декан",
                "ректор", "проректор", "старший викладач", "асистент кафедр",
                "начальник кафедр", "заступник начальника кафедр",
                "вчитель", "учитель", "педагог", "вихователь",
                "методист", "тьютор", "куратор навчальн"
        );
        // Наукові посади. ВАЖЛИВО: мають бути СПЕЦИФІЧНИМИ — інакше ламає пп.20
        // для військових/держслужбовців де "відділ" / "управління" — це звичайна
        // адміністративна структура, а не НДІ-лабораторія.
        // Раніше "завідувач відділу" помилково вважалось науковою → начальник звичайного
        // відділу інформаційних систем не зараховувався в практичний досвід.
        List<String> scientificKeywords = List.of(
                "науковий співробітник", "старший науковий", "молодший науковий",
                "провідний науковий", "головний науковий", "наук. співробітник",
                "аспірант", "докторант", "ад'юнкт",
                "завідувач лабораторії"   // лабораторія — типово наукова одиниця
                // НЕ включаємо: "дослідник" (може бути у будь-якій інженерній посаді
                // типу "інженер-дослідник"), "завідувач відділу" (звичайний відділ
                // не-наукової установи теж так зветься)
        );
        for (String kw : pedagogicalKeywords) {
            if (lower.contains(kw)) return true;
        }
        for (String kw : scientificKeywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    // --- Бінарна перевірка (є/немає) ---
    private DeterministicResult checkBinaryFromText(String desc, int required, String label,
                                                     List<String> keywords, String recommendation) {
        boolean found = containsAny(desc, keywords);
        int count = found ? 1 : 0;
        String reasoning = found
                ? label + " — знайдено підтвердження в описі"
                : label + " — не знайдено підтвердження в описі";
        return new DeterministicResult(count, required, reasoning, found ? "" : recommendation);
    }

    // ==================== TEXT UTILS ====================

    /** Підраховує кількість окремих елементів в тексті (нумеровані списки, ";", рядки) */
    /**
     * Текстовий fallback для оцінки кількості елементів коли БД ще не має структурованих даних.
     * Повертає N тільки якщо у тексті є ПОСЛІДОВНА нумерація 1, 2, 3, ..., N
     * (не просто максимальне число — це захищає від випадкових чисел типу номерів сторінок,
     * років, посилань на ДСТУ).
     */
    private int countItemsInText(String text) {
        if (text == null || text.isBlank()) return 0;

        // 1. Збираємо ВСІ числа на початку рядків, що виглядають як нумерація: "1.", "2)", "3 "
        Matcher numMatcher = Pattern.compile("(?:^|\\n)\\s*(\\d+)[.)\\s]").matcher(text);
        java.util.Set<Integer> nums = new java.util.HashSet<>();
        while (numMatcher.find()) {
            try {
                int n = Integer.parseInt(numMatcher.group(1));
                // Захист від випадкових великих чисел (номери сторінок, року, ID тощо).
                // Реалістичний максимум для досягнень одного типу — десятки, не сотні.
                if (n >= 1 && n <= 100) nums.add(n);
            } catch (NumberFormatException ignored) {}
        }
        // Шукаємо найдовшу послідовність 1, 2, ..., N — це справжня нумерація.
        int seq = 0;
        for (int i = 1; i <= 100; i++) {
            if (nums.contains(i)) seq = i;
            else break;
        }
        if (seq >= 2) return seq;

        // 2. Рахуємо елементи через ";"
        String[] parts = text.split(";");
        if (parts.length >= 2) return parts.length;

        // 3. Рахуємо рядки що виглядають як окремі записи
        String[] lines = text.split("\\n");
        int meaningful = 0;
        for (String line : lines) {
            if (line.trim().length() > 20) meaningful++;
        }
        return Math.max(meaningful, 1);
    }

    /** Витягує кількість років з тексту */
    private int extractYearsFromText(String text) {
        if (text == null) return 0;
        Matcher m = Pattern.compile("(\\d+)\\s*рок|рік|років|роки").matcher(text.toLowerCase());
        int max = 0;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }

    /** Витягує кількість годин з тексту */
    private int extractHoursFromText(String text) {
        if (text == null) return 0;
        Matcher m = Pattern.compile("(\\d+)\\s*(?:годин|год\\.?|аудиторних)").matcher(text.toLowerCase());
        int max = 0;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }

    /** Перевіряє чи текст містить будь-яке з ключових слів (case-insensitive) */
    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(k -> lower.contains(k.toLowerCase()));
    }

    /**
     * Рахує кількість елементів з урахуванням:
     * 1. Числового префіксу: "2 РПНД" → 2
     * 2. Повторних входжень: "РПНД; РПНД" → 2
     * 3. Переліку після ключового слова: "робочих програм: Дисц1 (122), Дисц2 (125)" → 2
     */
    private int countWithNumericPrefix(String text, String keyword) {
        if (text == null || text.isBlank()) return 0;
        // 1. "N keyword" (число перед ключовим словом)
        Matcher numMatcher = Pattern.compile(
                "(?i)(\\d+)\\s+" + keyword, Pattern.UNICODE_CASE).matcher(text);
        int numericCount = 0;
        while (numMatcher.find()) {
            numericCount += Integer.parseInt(numMatcher.group(1));
        }
        // 2. Окремі входження keyword
        int directCount = countMatches(text, "(?i)" + keyword);
        // 3. Перелік після "keyword[...]: item1 (...), item2 (...)"
        int listCount = countListAfterKeyword(text, keyword);
        return Math.max(Math.max(numericCount, directCount), listCount);
    }

    /**
     * Рахує елементи переліку після ключового слова + двокрапки.
     * "робочих програм навчальних дисциплін: X (122, 126), Y (125)" → 2
     */
    private int countListAfterKeyword(String text, String keyword) {
        Matcher m = Pattern.compile("(?i)" + keyword + "[^:]{0,40}:\\s*(.+?)(?:\\n|$)",
                Pattern.UNICODE_CASE).matcher(text);
        if (!m.find()) return 0;
        String listText = m.group(1).trim();
        // Рахуємо групи з дужками: "Назва (коди), Назва2 (коди)"
        int parenGroups = countMatches(listText, "\\([^)]+\\)");
        if (parenGroups >= 2) return parenGroups;
        // Рахуємо елементи через ";"
        String[] semiParts = listText.split(";");
        if (semiParts.length >= 2) return semiParts.length;
        // Рахуємо елементи через "," якщо вони без дужок (прості назви)
        if (!listText.contains("(")) {
            String[] commaParts = listText.split(",");
            if (commaParts.length >= 2) return commaParts.length;
        }
        return 1;
    }

    /** Рахує кількість неперекриваних regex-матчів (case-insensitive, Unicode) */
    private int countMatches(String text, String regex) {
        if (text == null || text.isBlank()) return 0;
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    /** Чи є публікація методичною працею (пп.4), а не підручником (пп.3) */
    private boolean isMethodicalWork(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.contains("практикум")
                || lower.contains("конспект лекцій")
                || lower.contains("методичн")
                || lower.contains("робоча програма") || lower.contains("рпнд")
                || lower.contains("електронний курс") || lower.contains("дистанційн")
                || lower.contains("для самостійної роботи")
                || lower.contains("лабораторний практикум");
    }

    // ==================== OTHER ====================

    @Transactional
    public int applyReclassifications(BatchReclassifyRequest request) {
        int updated = 0;
        for (BatchReclassifyRequest.ReclassifyItem item : request.getItems()) {
            Achievement a = achievementRepository.findById(item.getAchievementId()).orElse(null);
            if (a == null) continue;
            try {
                AchievementType oldType = a.getAchievementType();
                AchievementType newType = AchievementType.valueOf(item.getNewType());
                a.setAchievementType(newType);
                // Strip old "пп.X — " prefix from title if present
                if (a.getTitle() != null && a.getTitle().matches("^пп\\.\\d+ — .+")) {
                    a.setTitle(a.getTitle().substring(a.getTitle().indexOf(" — ") + 3));
                }
                a.setVerified(true);
                a.setVerifiedBy("AI (reclassified)");
                String note = String.format("[AI] Перекласифіковано з пп.%d (%s) на пп.%d (%s)",
                        oldType.getNumber(), oldType.name(), newType.getNumber(), newType.name());
                a.setNotes(a.getNotes() != null ? a.getNotes() + "\n" + note : note);
                achievementRepository.save(a);
                updated++;
            } catch (IllegalArgumentException e) {
                log.warn("Invalid achievement type: {}", item.getNewType());
            }
        }
        return updated;
    }

    public AchievementValidationSuggestion validateSingle(String description, String currentTypeName) {
        if (classifierService == null) return null;
        try {
            ClassificationResult result = classifierService.classify(description);
            AchievementType suggested = AchievementType.fromNumber(result.getType());
            AchievementType current = AchievementType.valueOf(currentTypeName);
            if (suggested == null) return null;

            boolean mismatch = suggested != current;
            return AchievementValidationSuggestion.builder()
                    .achievementType(current.name())
                    .ppNumber(current.getNumber())
                    .currentCount(mismatch ? 0 : 1)
                    .requiredCount(1)
                    .progress(mismatch ? 0.0 : 1.0)
                    .fulfilled(!mismatch)
                    .reasoning(result.getReasoning()
                            + (mismatch ? " [AI пропонує пп." + suggested.getNumber() + "]" : ""))
                    .build();
        } catch (Exception e) {
            log.warn("Single validation failed: {}", e.getMessage());
            return null;
        }
    }

    // ==================== LIGHTWEIGHT PROGRESS (no AI, no saving) ====================

    /**
     * Lightweight deterministic progress for all achievements of a teacher.
     * No AI call, no ValidationResult saving — just DB counts + text parsing.
     */
    @Transactional(readOnly = true)
    public List<AchievementProgressDto> getProgressForTeacher(Long teacherId) {
        List<Achievement> achievements = achievementRepository.findByTeacherId(teacherId);
        if (achievements.isEmpty()) return List.of();

        Teacher teacher = achievements.get(0).getTeacher();
        List<Publication> publications = publicationRepository.findByTeacherId(teacherId);

        List<AchievementProgressDto> result = new ArrayList<>();
        for (Achievement a : achievements) {
            int ppNum = a.getAchievementType().getNumber();
            String desc = a.getDescription() != null ? a.getDescription() : "";

            DeterministicResult det = checkDeterministic(ppNum, desc, publications, teacher);

            double progress = det.requiredCount() > 0
                    ? Math.min(1.0, (double) det.currentCount() / det.requiredCount()) : 0.0;

            result.add(AchievementProgressDto.builder()
                    .achievementId(a.getId())
                    .achievementType(a.getAchievementType().name())
                    .ppNumber(ppNum)
                    .currentCount(det.currentCount())
                    .requiredCount(det.requiredCount())
                    .progress(progress)
                    .fulfilled(det.currentCount() >= det.requiredCount())
                    .label(buildProgressLabel(ppNum, det.currentCount(), det.requiredCount()))
                    .reasoning(det.reasoning())
                    .build());
        }
        return result;
    }

    private String buildProgressLabel(int ppNum, int current, int required) {
        String unit = switch (ppNum) {
            case 1 -> "статей";
            case 2 -> "патентів/свідоцтв";
            case 3 -> "підручників";
            case 4 -> "метод. праць";
            case 5 -> "дисертація";
            case 6 -> "керівництво";
            case 7 -> "атестація";
            case 8 -> "ред./експерт";
            case 9 -> "експ. рада";
            case 10 -> "проєктів";
            case 11 -> "консультув.";
            case 12 -> "апробацій";
            case 13 -> "годин";
            case 14, 15 -> "олімпіад";
            case 16 -> "УБД";
            case 17 -> "ООН";
            case 18 -> "НАТО";
            case 19 -> "об'єднань";
            case 20 -> "років";
            default -> "";
        };
        return current + "/" + required + " " + unit;
    }

    @Transactional(readOnly = true)
    public AchievementValidationResponse getLatestResults(Long teacherId) {
        List<ValidationResult> results = validationResultRepository.findLatestByTeacherId(teacherId);
        return convertToResponse(results);
    }

    @Transactional(readOnly = true)
    public List<ValidationSessionDto> getValidationHistory(Long teacherId) {
        List<Object[]> rows = validationResultRepository.findSessionsByTeacherId(teacherId);
        List<ValidationSessionDto> sessions = new ArrayList<>();
        for (Object[] row : rows) {
            String sid = (String) row[0];
            java.time.LocalDateTime validatedAt = (java.time.LocalDateTime) row[1];

            List<ValidationResult> sessionResults = validationResultRepository.findBySessionId(sid);
            int total = sessionResults.size();
            int fulfilled = (int) sessionResults.stream().filter(ValidationResult::isFulfilled).count();

            sessions.add(ValidationSessionDto.builder()
                    .sessionId(sid)
                    .validatedAt(validatedAt)
                    .totalCount(total)
                    .fulfilledCount(fulfilled)
                    .notFulfilledCount(total - fulfilled)
                    .build());
        }
        return sessions;
    }

    @Transactional(readOnly = true)
    public AchievementValidationResponse getSessionResults(String sessionId) {
        List<ValidationResult> results = validationResultRepository.findBySessionId(sessionId);
        return convertToResponse(results);
    }

    private AchievementValidationResponse convertToResponse(List<ValidationResult> results) {
        if (results.isEmpty()) {
            return AchievementValidationResponse.builder()
                    .totalValidated(0).fulfilledCount(0).notFulfilledCount(0)
                    .sessionId("").suggestions(List.of()).build();
        }

        String sessionId = results.get(0).getSessionId();
        List<AchievementValidationSuggestion> suggestions = results.stream().map(vr -> {
            String achievementType = vr.getAchievement() != null
                    ? vr.getAchievement().getAchievementType().name() : "";
            Long achievementId = vr.getAchievement() != null ? vr.getAchievement().getId() : null;
            String teacherName = "";
            if (vr.getTeacher() != null) {
                Teacher t = vr.getTeacher();
                teacherName = (t.getLastName() != null ? t.getLastName() : "") +
                        (t.getFirstName() != null ? " " + t.getFirstName().charAt(0) + "." : "") +
                        (t.getPatronymic() != null ? t.getPatronymic().charAt(0) + "." : "");
            }
            return AchievementValidationSuggestion.builder()
                    .achievementId(achievementId)
                    .teacherName(teacherName.trim())
                    .achievementType(achievementType)
                    .ppNumber(vr.getPpNumber())
                    .currentCount(vr.getCurrentCount())
                    .requiredCount(vr.getRequiredCount())
                    .progress(vr.getProgress())
                    .fulfilled(vr.isFulfilled())
                    .reasoning(vr.getReasoning())
                    .descriptionPreview(vr.getDescriptionPreview())
                    .build();
        }).collect(Collectors.toList());

        int fulfilled = (int) results.stream().filter(ValidationResult::isFulfilled).count();
        return AchievementValidationResponse.builder()
                .totalValidated(results.size())
                .fulfilledCount(fulfilled)
                .notFulfilledCount(results.size() - fulfilled)
                .sessionId(sessionId)
                .suggestions(suggestions)
                .build();
    }

    private List<Achievement> loadAchievements(AchievementValidationRequest request) {
        if (request.getAchievementIds() != null && !request.getAchievementIds().isEmpty()) {
            return achievementRepository.findAllById(request.getAchievementIds());
        } else if (request.getTeacherId() != null) {
            return achievementRepository.findByTeacherId(request.getTeacherId());
        }
        throw new IllegalArgumentException("Потрібно вказати achievementIds або teacherId");
    }

    private String formatTeacherName(Achievement a) {
        if (a.getTeacher() == null) return "N/A";
        StringBuilder sb = new StringBuilder();
        if (a.getTeacher().getLastName() != null) sb.append(a.getTeacher().getLastName());
        if (a.getTeacher().getFirstName() != null)
            sb.append(" ").append(a.getTeacher().getFirstName().charAt(0)).append(".");
        if (a.getTeacher().getPatronymic() != null)
            sb.append(a.getTeacher().getPatronymic().charAt(0)).append(".");
        return sb.toString().trim();
    }
}
