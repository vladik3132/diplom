package ua.edu.teacherlicence.publication.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.service.AchievementComposer;
import ua.edu.teacherlicence.ai.service.PublicationRelevanceAiService;
import ua.edu.teacherlicence.compliance.events.ComplianceEvents;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.fakhove.dto.VerificationResult;
import ua.edu.teacherlicence.fakhove.model.JournalCategory;
import ua.edu.teacherlicence.fakhove.service.FakhovyiJournalService;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.publication.model.ApprobationSubtype;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.MethodicalSubtype;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationStatus;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import java.util.stream.Collectors;
import ua.edu.teacherlicence.scopus.ScopusApiService;
import ua.edu.teacherlicence.scopus.ScopusVerificationResult;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final DstuCitationGenerator dstuGenerator;
    private final AchievementComposer achievementComposer;
    private final ApplicationEventPublisher events;
    private final FakhovyiJournalService fakhovyiJournalService;
    private final EducationalProgramRepository educationalProgramRepository;
    private final PublicationClassifier classifier;

    @Autowired(required = false)
    private PublicationRelevanceAiService relevanceAiService;

    @Autowired(required = false)
    private ScopusApiService scopusApiService;

    public List<Publication> findAll() {
        List<Publication> pubs = publicationRepository.findAll();
        pubs.sort(publicationPriorityComparator());
        return pubs;
    }

    public Publication findById(Long id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Публікацію не знайдено: " + id));
    }

    public List<Publication> findByTeacherId(Long teacherId) {
        List<Publication> pubs = publicationRepository.findByTeacherId(teacherId);
        pubs.sort(publicationPriorityComparator());
        return pubs;
    }

    /**
     * Сортування: Scopus → WoS → Категорія А → Б → решта, потім за роком (спадання).
     */
    private Comparator<Publication> publicationPriorityComparator() {
        return Comparator
                .comparingInt((Publication p) -> categoryPriority(p.getArticleCategory()))
                .thenComparing(Comparator.comparingInt(
                        (Publication p) -> p.getYear() != null ? p.getYear() : 0).reversed());
    }

    private int categoryPriority(ArticleCategory cat) {
        if (cat == null) return 99;
        return switch (cat) {
            case SCOPUS -> 0;
            case WOS -> 1;
            case CATEGORY_A -> 2;
            case CATEGORY_B -> 3;
        };
    }

    @Transactional
    public Publication create(Publication publication) {
        validateAndEnrich(publication);
        Publication saved = publicationRepository.save(publication);
        recomposeAchievements(saved.getTeacher());
        publishPublicationChanged(saved.getTeacher());
        return saved;
    }

    @Transactional
    public Publication update(Long id, Publication updated) {
        Publication existing = findById(id);
        existing.setTitle(updated.getTitle());
        existing.setType(updated.getType());
        existing.setArticleCategory(updated.getArticleCategory());
        existing.setJournalName(updated.getJournalName());
        existing.setYear(updated.getYear());
        // publicationDate: якщо оновлений запис має дату — беремо її,
        // інакше якщо змінився рік — синхронізуємо publicationDate на YYYY-01-01,
        // щоб не лишилось десинхронізованим.
        if (updated.getPublicationDate() != null) {
            existing.setPublicationDate(updated.getPublicationDate());
            if (updated.getYear() == null) {
                existing.setYear(updated.getPublicationDate().getYear());
            }
        } else if (updated.getYear() != null) {
            existing.setPublicationDate(LocalDate.of(updated.getYear(), 1, 1));
        } else {
            existing.setPublicationDate(null);
        }
        existing.setVolume(updated.getVolume());
        existing.setPages(updated.getPages());
        existing.setDoi(updated.getDoi());
        existing.setUrl(updated.getUrl());
        existing.setAuthors(updated.getAuthors());
        existing.setPublisher(updated.getPublisher());
        existing.setCity(updated.getCity());
        existing.setTotalPages(updated.getTotalPages());
        existing.setIsbn(updated.getIsbn());
        existing.setIssn(updated.getIssn());
        existing.setConferenceInfo(updated.getConferenceInfo());
        existing.setAuthorSheetCount(updated.getAuthorSheetCount());
        existing.setDstuCitation(updated.getDstuCitation());
        existing.setDocumentUrl(updated.getDocumentUrl());
        existing.setPpType(updated.getPpType());
        existing.setSourceSection(updated.getSourceSection());
        existing.setMethodicalSubtype(updated.getMethodicalSubtype());
        existing.setApprobationSubtype(updated.getApprobationSubtype());
        validateAndEnrich(existing);
        Publication saved = publicationRepository.save(existing);
        recomposeAchievements(saved.getTeacher());
        publishPublicationChanged(saved.getTeacher());
        return saved;
    }

    private void publishPublicationChanged(Teacher teacher) {
        if (teacher != null && teacher.getId() != null) {
            events.publishEvent(new ComplianceEvents.PublicationChanged(teacher.getId()));
        }
    }

    /**
     * Повна валідація та збагачення публікації:
     * 1. Перевірка актуальності (5 років)
     * 2. Верифікація журналу у базі фахових/Scopus
     * 3. Автоматичне визначення ppType
     * 4. Генерація ДСТУ
     * 5. Встановлення статусу
     */
    private void validateAndEnrich(Publication p) {
        // Скидаємо статус для повної ревалідації
        p.setStatus(null);

        // 0. Перекласифікація: proceedings/CEUR/тези → APPROBATION
        reclassifyConferenceProceedings(p);

        // 1. Перевірка Scopus для будь-якого типу (ARTICLE, APPROBATION)
        // Scopus має пріоритет — перевіряємо завжди
        checkScopusCategory(p);

        // 2. Верифікація журналу у БД фахових (тільки для ARTICLE без категорії)
        verifyJournalCategory(p);

        // 2.5. Автодетект підтипів (якщо не вказано вручну)
        autoAssignSubtype(p);

        // 3. Автоматичне визначення ppType
        autoAssignPpType(p);

        // 4. Генерація ДСТУ 8302:2015
        autoGenerateDstu(p);

        // 5. Встановлення статусу валідації
        // Якщо verifyJournalCategory не встановив NEEDS_ATTENTION — значить все ок
        if (p.getStatus() == null) {
            p.setStatus(PublicationStatus.AI_VALIDATED);
            log.info("Publication validated: '{}' → status=AI_VALIDATED, ppType={}, articleCategory={}",
                    truncate(p.getTitle(), 50), p.getPpType(), p.getArticleCategory());
        }

        // 6. Перевірка відповідності напряму кафедри (для фахових/Scopus публікацій)
        checkFieldRelevance(p);

        // 7. Перевірка актуальності (5 років) — ПІСЛЯ всього,
        // щоб OUTDATED мав найвищий пріоритет
        checkFreshness(p);
    }

    /**
     * Перевірка актуальності публікації (5 років).
     * Якщо publicationDate раніше за now-5y — OUTDATED.
     * Якщо publicationDate відсутня — fallback на year.
     */
    private void checkFreshness(Publication p) {
        LocalDate effective = p.effectiveDate();
        if (effective == null) return;
        LocalDate cutoff = LocalDate.now().minusYears(5);
        if (effective.isBefore(cutoff)) {
            p.setStatus(PublicationStatus.OUTDATED);
            log.info("Publication '{}' (date={}) is OUTDATED (cutoff={})",
                    truncate(p.getTitle(), 40), effective, cutoff);
        }
    }

    /**
     * Перекласифікація: якщо journalName/publisher містить "CEUR", "proceedings",
     * "матеріали конференції", "тези" тощо → ARTICLE → APPROBATION.
     * Викликається ДО Scopus-перевірки, щоб правильно обробити конференційні публікації.
     */
    private void reclassifyConferenceProceedings(Publication p) {
        if (p.getType() != PublicationType.ARTICLE) return;

        String journalLower = p.getJournalName() != null ? p.getJournalName().toLowerCase() : "";
        String publisherLower = p.getPublisher() != null ? p.getPublisher().toLowerCase() : "";
        String titleLower = p.getTitle() != null ? p.getTitle().toLowerCase() : "";

        boolean isProceedings = false;

        // "proceedings" в назві журналу (але НЕ CEUR — там бувають і статті, і тези)
        if (!journalLower.contains("ceur") && !publisherLower.contains("ceur")
                && (journalLower.contains("proceedings") || journalLower.contains("proceeding"))) {
            isProceedings = true;
        } else if (journalLower.contains("матеріали") && (journalLower.contains("конференц")
                || journalLower.contains("семінар") || journalLower.contains("симпозіум"))) {
            isProceedings = true;
        } else if (titleLower.startsWith("тези") || journalLower.contains("тези доповідей")
                || journalLower.contains("тез доповідей") || journalLower.contains("збірник тез")) {
            isProceedings = true;
        }

        if (isProceedings) {
            log.info("Reclassified ARTICLE → APPROBATION: '{}' (journal: '{}')",
                    truncate(p.getTitle(), 50), truncate(p.getJournalName(), 40));
            p.setType(PublicationType.APPROBATION);
            // Очищаємо articleCategory — для APPROBATION використовується approbationSubtype
            p.setArticleCategory(null);
        }

        // CEUR Workshop Proceedings → автоматично Scopus
        if (journalLower.contains("ceur") || publisherLower.contains("ceur")) {
            if (p.getType() == PublicationType.APPROBATION) {
                p.setApprobationSubtype(ApprobationSubtype.SCOPUS_WOS);
                log.info("Auto-marked APPROBATION as SCOPUS_WOS (CEUR): '{}'", truncate(p.getTitle(), 50));
            } else if (p.getArticleCategory() != ArticleCategory.SCOPUS) {
                p.setArticleCategory(ArticleCategory.SCOPUS);
                log.info("Auto-marked as Scopus (CEUR): '{}'", truncate(p.getTitle(), 50));
            }
        }
    }

    /**
     * Автоматичне визначення підтипів для METHODICAL та APPROBATION/POPULAR_SCIENTIFIC.
     * Не перезаписує якщо підтип вже вказаний вручну.
     */
    private void autoAssignSubtype(Publication p) {
        if (p.getType() == PublicationType.METHODICAL && p.getMethodicalSubtype() == null) {
            String text = ((p.getTitle() != null ? p.getTitle() : "") + " "
                    + (p.getRawText() != null ? p.getRawText() : "")).toLowerCase();
            p.setMethodicalSubtype(detectMethodicalSubtype(text));
            log.info("Auto-assigned methodicalSubtype={} for '{}'",
                    p.getMethodicalSubtype(), truncate(p.getTitle(), 50));
        }
        if ((p.getType() == PublicationType.APPROBATION || p.getType() == PublicationType.POPULAR_SCIENTIFIC)
                && p.getApprobationSubtype() == null) {
            String text = ((p.getTitle() != null ? p.getTitle() : "") + " "
                    + (p.getRawText() != null ? p.getRawText() : "")
                    + " " + (p.getJournalName() != null ? p.getJournalName() : "")
                    + " " + (p.getConferenceInfo() != null ? p.getConferenceInfo() : "")).toLowerCase();
            p.setApprobationSubtype(detectApprobationSubtype(text));
            log.info("Auto-assigned approbationSubtype={} for '{}'",
                    p.getApprobationSubtype(), truncate(p.getTitle(), 50));
        }
    }

    /**
     * Перевірка Scopus для ARTICLE та APPROBATION.
     * Scopus має пріоритет — перевіряємо завжди, навіть якщо вже є категорія фахового.
     * Для APPROBATION: НЕ змінює тип (залишається APPROBATION+pp.12), ставить approbationSubtype=SCOPUS_WOS.
     */
    private void checkScopusCategory(Publication p) {
        if (p.getType() != PublicationType.ARTICLE && p.getType() != PublicationType.APPROBATION) return;
        // Для ARTICLE — перевіряємо articleCategory
        // Для APPROBATION — ставимо approbationSubtype=SCOPUS_WOS (не articleCategory)
        if (p.getType() == PublicationType.ARTICLE && p.getArticleCategory() == ArticleCategory.SCOPUS) return;
        if (p.getType() == PublicationType.APPROBATION && p.getApprobationSubtype() == ApprobationSubtype.SCOPUS_WOS) return;

        // 1. Scopus API (якщо увімкнено)
        if (scopusApiService != null && p.getTeacher() != null) {
            ScopusVerificationResult scopusResult = scopusApiService.verifyPublication(
                    p.getTitle(),
                    p.getDoi(),
                    p.getTeacher().getScopusId(),
                    p.getTeacher().getLastName(),
                    p.getTeacher().getFirstName()
            );
            if (scopusResult.isConfirmed()) {
                log.info("Scopus confirmed: '{}' by {} (type={}, method: {}, scopusTitle: '{}')",
                        truncate(p.getTitle(), 50),
                        p.getTeacher().getLastName(),
                        p.getType(),
                        scopusResult.getSearchMethod(),
                        truncate(scopusResult.getScopusTitle(), 50));
                if (p.getType() == PublicationType.APPROBATION) {
                    // Для апробацій — рівень видання, не articleCategory
                    p.setApprobationSubtype(ApprobationSubtype.SCOPUS_WOS);
                } else {
                    p.setArticleCategory(ArticleCategory.SCOPUS);
                }
                return;
            } else if (scopusResult.isFound() && !scopusResult.isAuthorConfirmed()) {
                log.warn("Scopus: article found but author NOT confirmed for '{}' by {}",
                        truncate(p.getTitle(), 50), p.getTeacher().getLastName());
            }
        }
    }

    /**
     * Верифікація журналу у базі фахових видань (тільки для ARTICLE).
     * Викликається ПІСЛЯ checkScopusCategory.
     * WOS довіряємо (немає API для перевірки) — не скидаємо.
     * SCOPUS вже оброблено в checkScopusCategory — пропускаємо.
     */
    private void verifyJournalCategory(Publication p) {
        if (p.getType() != PublicationType.ARTICLE) return;
        // Scopus/WoS вже встановлено — довіряємо, не перевіряємо через фахову БД
        if (p.getArticleCategory() == ArticleCategory.SCOPUS
                || p.getArticleCategory() == ArticleCategory.WOS) return;

        // Перевіряємо фахові видання (локальна БД)
        ArticleCategory verified = verifyFakhoviField(p.getJournalName(), p.getIssn());
        if (verified == null) {
            verified = verifyFakhoviField(p.getPublisher(), null);
        }

        if (verified != null) {
            if (p.getArticleCategory() != verified) {
                log.info("Journal verification: '{}' → {} (was: {})",
                        p.getJournalName(), verified, p.getArticleCategory());
            }
            p.setArticleCategory(verified);
        } else if (p.getArticleCategory() != null) {
            // Користувач поставив CATEGORY_A/B вручну, але журнал не знайдено — скидаємо
            log.warn("Journal '{}' (ISSN: {}) not found in fakhovi DB. " +
                            "User claimed category={}. Resetting to null, status=NEEDS_ATTENTION.",
                    p.getJournalName(), p.getIssn(), p.getArticleCategory());
            p.setArticleCategory(null);
            p.setStatus(PublicationStatus.NEEDS_ATTENTION);
        } else {
            log.info("Journal '{}' not found in any DB — status=NEEDS_ATTENTION.", p.getJournalName());
            p.setStatus(PublicationStatus.NEEDS_ATTENTION);
        }
    }

    /**
     * Перевіряє назву/ISSN у базі фахових видань.
     * Повертає категорію (CATEGORY_A, CATEGORY_B) або null.
     * Scopus перевіряється виключно через Scopus Elsevier API (checkScopusCategory).
     */
    private ArticleCategory verifyFakhoviField(String name, String issn) {
        if ((name == null || name.isBlank()) && (issn == null || issn.isBlank())) return null;
        try {
            VerificationResult vr = fakhovyiJournalService.verifyJournal(name, issn);
            if (vr.isFakhove() && vr.category() != null) {
                return vr.category() == JournalCategory.CATEGORY_A
                        ? ArticleCategory.CATEGORY_A : ArticleCategory.CATEGORY_B;
            }
        } catch (Exception e) {
            log.warn("Journal verification failed for '{}': {}", name, e.getMessage());
        }
        return null;
    }

    /**
     * Автоматично визначає ppType на основі типу публікації,
     * якщо ppType ще не встановлено вручну.
     */
    /**
     * Встановлює канонічний {@code ppType} через {@link PublicationClassifier}.
     * Використовує full-publication логіку (TEXTBOOK з методичним заголовком → пп.4 замість пп.3,
     * POPULAR_SCIENTIFIC → пп.12 замість пп.1).
     */
    private void autoAssignPpType(Publication p) {
        if (p.getType() == null) return;
        AchievementType target = classifier.inferPpType(p);
        if (target == null) {
            log.debug("Classifier returned null pp for publication type={} title='{}'",
                    p.getType(), truncate(p.getTitle(), 50));
            return;
        }
        AchievementType current = p.getPpType();
        if (current != target) {
            log.info("ppType update via classifier: type={} '{}' → was {} → now {}",
                    p.getType(), truncate(p.getTitle(), 40), current, target);
            p.setPpType(target);
            p.setSourceSection("pp." + target.getNumber());
        }
    }

    /**
     * @deprecated Використовуйте {@link PublicationClassifier#inferPpNumber(Publication)}
     * — він враховує заголовок (methodical keywords) і POPULAR_SCIENTIFIC→пп.12.
     * Залишено для backward compat — викликається з PublicationController.classify().
     */
    @Deprecated
    public static int inferPpFromType(PublicationType type) {
        if (type == null) return 1;
        return switch (type) {
            case ARTICLE -> 1;
            case POPULAR_SCIENTIFIC -> 12;
            case PATENT, DECLARATIVE_PATENT, COPYRIGHT -> 2;
            case TEXTBOOK, STUDY_GUIDE, MONOGRAPH -> 3;
            case METHODICAL -> 4;
            case APPROBATION -> 12;
            case OTHER -> 0;
        };
    }

    /**
     * Автоматично генерує dstuCitation якщо поле порожнє або було автозгенеровано.
     * Якщо користувач вручну відредагував — не перезаписує.
     */
    private void autoGenerateDstu(Publication p) {
        String generated = dstuGenerator.generate(p);
        if (generated == null) return;

        // Якщо поле порожнє — завжди генеруємо
        if (p.getDstuCitation() == null || p.getDstuCitation().isBlank()) {
            p.setDstuCitation(generated);
        }
    }

    /**
     * Перевірка відповідності публікації напряму діяльності кафедри.
     * Працює для фахових/Scopus статей (ppType = PP_1 або ARTICLE з категорією).
     * Використовує AI якщо увімкнено, інакше — null (не перевірено).
     */
    private void checkFieldRelevance(Publication p) {
        // Перевіряємо статті та апробації з категорією (Scopus/фахові)
        boolean hasCategory = p.getArticleCategory() != null;
        boolean isRelevantType = p.getType() == PublicationType.ARTICLE || p.getType() == PublicationType.APPROBATION;
        if (!isRelevantType || !hasCategory) {
            p.setFieldRelevant(null); // Не стосується
            return;
        }

        if (relevanceAiService == null) {
            p.setFieldRelevant(null); // AI недоступний
            return;
        }

        Teacher teacher = p.getTeacher();
        if (teacher == null || teacher.getDepartment() == null) {
            p.setFieldRelevant(null);
            return;
        }

        Department dept = teacher.getDepartment();
        List<String> specialties = educationalProgramRepository.findByDepartmentId(dept.getId())
                .stream()
                .map(EducationalProgram::getSpecialty)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        boolean relevant = relevanceAiService.checkRelevance(
                p.getTitle(), dept.getName(), dept.getId(), specialties
        );
        p.setFieldRelevant(relevant);

        if (!relevant) {
            log.warn("Publication '{}' does NOT match department '{}' field of activity",
                    truncate(p.getTitle(), 50), dept.getName());
            // Якщо не відповідає — NEEDS_ATTENTION (якщо ще не OUTDATED)
            if (p.getStatus() != PublicationStatus.OUTDATED) {
                p.setStatus(PublicationStatus.NEEDS_ATTENTION);
            }
        } else {
            log.info("Publication '{}' matches department '{}' field", truncate(p.getTitle(), 50), dept.getName());
        }
    }

    /**
     * Зберегти публікацію напряму (без перевалідації).
     * Використовується для оновлення статусу начальником.
     */
    @Transactional
    public Publication saveDirectly(Publication publication) {
        Publication saved = publicationRepository.save(publication);
        recomposeAchievements(saved.getTeacher());
        publishPublicationChanged(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Publication pub = findById(id);
        Teacher teacher = pub.getTeacher();
        publicationRepository.deleteById(id);
        recomposeAchievements(teacher);
        publishPublicationChanged(teacher);
    }

    /**
     * Перегенерація досягнень після зміни публікацій.
     */
    private void recomposeAchievements(Teacher teacher) {
        if (teacher == null) return;
        try {
            achievementComposer.recomposeForTeacher(teacher);
            log.info("Recomposed achievements for {} after publication change", teacher.getLastName());
        } catch (Exception e) {
            log.warn("Failed to recompose achievements for {}: {}", teacher.getLastName(), e.getMessage());
        }
    }

    /**
     * Перекласифікація ВСІХ публікацій: перевизначає підтипи для METHODICAL та APPROBATION/POPULAR_SCIENTIFIC
     * на основі аналізу назви. Перезаписує навіть існуючі підтипи (для виправлення помилок).
     */
    @Transactional
    public int reclassifySubtypes() {
        return reclassifySubtypes(null);
    }

    /**
     * Перекласифікація з опціональною фільтрацією по кафедрі.
     *
     * @param departmentId ID кафедри (null = усі викладачі)
     * @return кількість змінених записів (subtypes + ppType)
     */
    @Transactional
    public int reclassifySubtypes(Long departmentId) {
        java.util.function.Predicate<Publication> deptFilter = (departmentId == null)
                ? p -> true
                : p -> p != null && p.getTeacher() != null
                        && p.getTeacher().getDepartment() != null
                        && departmentId.equals(p.getTeacher().getDepartment().getId());

        int count = 0;

        // 1. Методичні — перекласифікувати subtype
        List<Publication> methodicals = publicationRepository.findByType(PublicationType.METHODICAL).stream()
                .filter(deptFilter).collect(Collectors.toList());
        for (Publication pub : methodicals) {
            String title = (pub.getTitle() != null ? pub.getTitle() : "") + " "
                    + (pub.getRawText() != null ? pub.getRawText() : "");
            MethodicalSubtype newSub = detectMethodicalSubtype(title);
            if (pub.getMethodicalSubtype() != newSub) {
                pub.setMethodicalSubtype(newSub);
                count++;
            }
        }
        publicationRepository.saveAll(methodicals);

        // 2. Апробації — перекласифікувати subtype
        List<Publication> approbations = publicationRepository.findByType(PublicationType.APPROBATION).stream()
                .filter(deptFilter).collect(Collectors.toList());
        for (Publication pub : approbations) {
            String text = (pub.getTitle() != null ? pub.getTitle() : "") + " "
                    + (pub.getRawText() != null ? pub.getRawText() : "") + " "
                    + (pub.getJournalName() != null ? pub.getJournalName() : "") + " "
                    + (pub.getConferenceInfo() != null ? pub.getConferenceInfo() : "");
            ApprobationSubtype newSub = detectApprobationSubtype(text);
            if (pub.getApprobationSubtype() != newSub) {
                pub.setApprobationSubtype(newSub);
                count++;
            }
        }
        publicationRepository.saveAll(approbations);

        // 3. Науково-популярні — subtype + reassign ppType до 12 (а не 1)
        List<Publication> popularSci = publicationRepository.findByType(PublicationType.POPULAR_SCIENTIFIC).stream()
                .filter(deptFilter).collect(Collectors.toList());
        for (Publication pub : popularSci) {
            String text = (pub.getTitle() != null ? pub.getTitle() : "") + " "
                    + (pub.getRawText() != null ? pub.getRawText() : "") + " "
                    + (pub.getJournalName() != null ? pub.getJournalName() : "");
            ApprobationSubtype newSub = detectApprobationSubtype(text);
            if (pub.getApprobationSubtype() != newSub) {
                pub.setApprobationSubtype(newSub);
                count++;
            }
        }
        publicationRepository.saveAll(popularSci);

        // 4. Re-assign ppType для всіх публікацій (з фільтром по кафедрі) через canonical classifier.
        //    Виправляє стару логіку (POPULAR_SCIENTIFIC→1 → тепер →12,
        //    TEXTBOOK з методичним заголовком→3 → тепер →4 тощо).
        int ppReclassified = 0;
        List<Publication> all = publicationRepository.findAll().stream()
                .filter(deptFilter).collect(Collectors.toList());
        for (Publication pub : all) {
            AchievementType canonical = classifier.inferPpType(pub);
            if (canonical == null) continue;
            if (pub.getPpType() != canonical) {
                AchievementType prev = pub.getPpType();
                pub.setPpType(canonical);
                pub.setSourceSection("pp." + canonical.getNumber());
                ppReclassified++;
                log.info("ppType reclassified: '{}' was={} → now={} (type={})",
                        truncate(pub.getTitle(), 50), prev, canonical, pub.getType());
            }
        }
        if (ppReclassified > 0) publicationRepository.saveAll(all);
        count += ppReclassified;

        // 5. Recompose ВСІХ achievement-описів — щоб title/description відображав
        //    актуальні (deduped + qualified) публікації, узгоджено з progress.
        java.util.Set<Long> affectedTeacherIds = new java.util.HashSet<>();
        for (Publication p : methodicals) if (p.getTeacher() != null) affectedTeacherIds.add(p.getTeacher().getId());
        for (Publication p : approbations) if (p.getTeacher() != null) affectedTeacherIds.add(p.getTeacher().getId());
        for (Publication p : popularSci) if (p.getTeacher() != null) affectedTeacherIds.add(p.getTeacher().getId());
        for (Publication p : all) if (p.getTeacher() != null) affectedTeacherIds.add(p.getTeacher().getId());

        int recomposed = 0;
        for (Long tid : affectedTeacherIds) {
            try {
                Publication anyPub = all.stream().filter(p -> p.getTeacher() != null
                        && p.getTeacher().getId().equals(tid)).findFirst().orElse(null);
                if (anyPub != null && anyPub.getTeacher() != null) {
                    achievementComposer.recomposeForTeacher(anyPub.getTeacher());
                    recomposed++;
                }
            } catch (Exception e) {
                log.warn("recomposeForTeacher failed for teacherId={}: {}", tid, e.getMessage());
            }
        }
        log.info("Reclassified {} entries (subtypes + ppType={}). Recomposed achievements for {} teachers.",
                count, ppReclassified, recomposed);
        return count;
    }

    private MethodicalSubtype detectMethodicalSubtype(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("практикум")) return MethodicalSubtype.PRACTICUM;
        if (lower.contains("електронний курс") || lower.contains("електронні курси")
                || lower.contains("е-курс") || lower.contains("е курс") || lower.contains("екурс")
                || lower.contains("дистанційн") || lower.contains("on-line курс")
                || lower.contains("online курс") || lower.contains("онлайн курс")
                || lower.contains("онлайн-курс") || lower.contains("moodle"))
            return MethodicalSubtype.E_COURSE;
        if (lower.contains("конспект лекцій") || lower.contains("конспект лекції")
                || lower.contains("конспекти лекцій") || lower.contains("курс лекцій")
                || lower.contains("курс лекції") || lower.contains("тексти лекцій"))
            return MethodicalSubtype.LECTURE_NOTES;
        if (lower.contains("робоча програма") || lower.contains("робочі програми")
                || lower.contains("рпнд") || lower.contains("силабус") || lower.contains("навчальна програма"))
            return MethodicalSubtype.WORK_PROGRAM;
        if (lower.contains("методичн") && (lower.contains("вказів") || lower.contains("рекоменд")
                || lower.contains("забезпечення") || lower.contains("розробк")))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (lower.contains("метод.") && (lower.contains("вказів") || lower.contains("рекоменд")))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (lower.contains("для самостійної роботи") || lower.contains("для самост. роботи")
                || lower.contains("завдання для практичн") || lower.contains("завдання для лаборатор"))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (lower.contains("методичн") || lower.contains("метод."))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        return MethodicalSubtype.LECTURE_NOTES;
    }

    private ApprobationSubtype detectApprobationSubtype(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("scopus") || lower.contains("web of science") || lower.contains("wos")
                || lower.contains("ceur"))
            return ApprobationSubtype.SCOPUS_WOS;
        if (lower.contains("ieee") || lower.contains("springer") || lower.contains("elsevier")
                || lower.contains("wiley") || lower.contains("acm ") || lower.contains("mdpi")
                || lower.contains("taylor & francis") || lower.contains("de gruyter")
                || lower.contains("cambridge university") || lower.contains("oxford university")
                || lower.contains("nato "))
            return ApprobationSubtype.INTERNATIONAL;
        return ApprobationSubtype.DOMESTIC;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "?";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
