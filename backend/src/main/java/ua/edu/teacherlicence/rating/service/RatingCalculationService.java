package ua.edu.teacherlicence.rating.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.publication.model.*;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.qualification.model.QualificationCategory;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;
import ua.edu.teacherlicence.rating.model.*;
import ua.edu.teacherlicence.rating.repository.*;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.MilitaryEducationLevel;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ua.edu.teacherlicence.rating.model.RatingCriterion.*;

/**
 * Сервіс підрахунку балів рейтингу НПП за критеріями Додатку 1.
 * Кожен метод calculate* обчислює бали для одного критерію та повертає записи TeacherRating.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingCalculationService {

    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository academicTitleRepository;
    private final PublicationRepository publicationRepository;
    private final ua.edu.teacherlicence.publication.service.PublicationClassifier publicationClassifier;
    private final ScientificSupervisionRepository supervisionRepository;
    private final AttestationActivityRepository attestationRepository;
    private final EditorialActivityRepository editorialRepository;
    private final ExpertCouncilRepository expertCouncilRepository;
    private final InternationalProjectRepository internationalRepository;
    private final ScientificConsultingRepository consultingRepository;
    private final ForeignLanguageTeachingRepository foreignLangTeachingRepository;
    private final OlympiadGuidanceRepository olympiadRepository;
    private final MilitaryMissionRepository militaryMissionRepository;
    private final ProfessionalAssociationRepository profAssociationRepository;
    private final QualificationImprovementRepository qualificationRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final OpenLessonRepository openLessonRepository;
    private final MethodologicalExperimentRepository experimentRepository;
    private final AcademicMobilityRepository mobilityRepository;
    private final ua.edu.teacherlicence.rating.repository.ForeignInternshipRepository foreignInternshipRepository;
    private final ProgramWorkingGroupRepository workingGroupRepository;
    private final TeacherRatingRepository teacherRatingRepository;
    private final RatingPeriodRepository periodRepository;

    /**
     * Обрахувати рейтинг для одного викладача за вказаний період.
     */
    @Transactional
    public List<TeacherRating> calculateForTeacher(Long teacherId, Long periodId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Викладача не знайдено: " + teacherId));
        RatingPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Період не знайдено: " + periodId));

        // Видаляємо попередні розрахунки
        teacherRatingRepository.deleteByPeriodIdAndTeacherId(periodId, teacherId);

        List<TeacherRating> ratings = new ArrayList<>();
        LocalDate from = period.getStartDate();
        LocalDate to = period.getEndDate();

        // ── пп.1 — Публікації ──
        ratings.addAll(calculatePublications(teacher, period, from, to));

        // ── пп.2 — Патенти ──
        ratings.addAll(calculatePatents(teacher, period, from, to));

        // ── пп.3 — Підручники / монографії ──
        ratings.addAll(calculateBooks(teacher, period, from, to));

        // ── пп.4 — Методичні праці ──
        ratings.addAll(calculateMethodical(teacher, period, from, to));

        // ── пп.5 — Захист дисертації ──
        ratings.addAll(calculateDissertation(teacher, period, from, to));

        // ── пп.6 — Наукове керівництво ──
        ratings.addAll(calculateSupervision(teacher, period, from, to));

        // ── пп.7 — Атестація ──
        ratings.addAll(calculateAttestation(teacher, period, from, to));

        // ── пп.8 — Редколегія ──
        ratings.addAll(calculateEditorial(teacher, period, from, to));

        // ── пп.9 — Експертна рада ──
        ratings.addAll(calculateExpertCouncil(teacher, period, from, to));

        // ── пп.10 — Міжнародні проєкти ──
        ratings.addAll(calculateInternational(teacher, period, from, to));

        // ── пп.11 — Наукове консультування ──
        ratings.addAll(calculateConsulting(teacher, period, from, to));

        // ── пп.12 — Апробації ──
        ratings.addAll(calculateApprobation(teacher, period, from, to));

        // ── пп.13 — Іноземна мова ──
        ratings.addAll(calculateForeignLangTeaching(teacher, period, from, to));

        // ── пп.14-15 — Олімпіади ──
        ratings.addAll(calculateOlympiads(teacher, period, from, to));

        // ── пп.16 — УБД ──
        ratings.addAll(calculateCombatVeteran(teacher, period, from, to));

        // ── пп.17-18 — Миротворчі/НАТО ──
        ratings.addAll(calculateMilitary(teacher, period, from, to));

        // ── пп.19 — Професійні об'єднання ──
        ratings.addAll(calculateProfAssociation(teacher, period, from, to));

        // ── Вчене звання ──
        ratings.addAll(calculateAcademicTitle(teacher, period, from, to));

        // ── Підвищення кваліфікації + стажування за кордоном + курси ВО ──
        ratings.addAll(calculateQualification(teacher, period, from, to));

        // ── Мовні сертифікати (СМР) ──
        ratings.addAll(calculateLanguageCertificate(teacher, period, from, to));

        // ── Відкриті заняття ──
        ratings.addAll(calculateOpenLessons(teacher, period, from, to));

        // ── Методичні експерименти ──
        ratings.addAll(calculateExperiments(teacher, period, from, to));

        // ── Академічна мобільність ──
        ratings.addAll(calculateMobility(teacher, period, from, to));

        // ── Міжнародне стажування ──
        ratings.addAll(calculateForeignInternships(teacher, period, from, to));

        // ── Робочі групи ОПП ──
        ratings.addAll(calculateWorkingGroups(teacher, period, from, to));

        // ── Рівень воєнної освіти ──
        ratings.addAll(calculateMilitaryEducation(teacher, period, from, to));

        // Зберігаємо
        List<TeacherRating> saved = teacherRatingRepository.saveAll(ratings);
        log.info("Calculated {} rating entries for teacher {} (period {}), total score = {}",
                saved.size(), teacherId, period.getName(),
                saved.stream().mapToInt(TeacherRating::getScore).sum());
        return saved;
    }

    /**
     * Обрахувати рейтинг для всіх викладачів за вказаний період.
     * <p>Виключаються викладачі кафедр з {@code rating_excluded=true}
     * (наприклад, "віртуальні" кафедри управління 0 / 888).
     */
    @Transactional
    public int calculateForAll(Long periodId) {
        List<Teacher> teachers = teacherRepository.findByDepartmentRatingExcludedFalse();
        int count = 0;
        for (Teacher t : teachers) {
            calculateForTeacher(t.getId(), periodId);
            count++;
        }
        log.info("Calculated ratings for {} teachers (excluded departments skipped) (period {})",
                count, periodId);
        return count;
    }

    // ══════════════════════════════════════════════
    //  Calculation methods
    // ══════════════════════════════════════════════

    private List<TeacherRating> calculatePublications(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<Publication> pubs = publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> p.getType() == PublicationType.ARTICLE && p.getArticleCategory() != null)
                .filter(p -> inPeriodPublication(p, from, to))
                .toList();

        // Content-dedup: одна стаття додана двічі (Cat A + Scopus після індексування)
        // → залишаємо тільки переможця (вища категорія / пізніша дата).
        // Volume-dedup ВИДАЛЕНО — кожна публікація рахується окремо.
        List<Publication> contentDeduped = publicationClassifier.deduplicateByContent(pubs);

        addIfPositive(result, teacher, period, SCOPUS_ARTICLE,
                (int) contentDeduped.stream().filter(p -> p.getArticleCategory() == ArticleCategory.SCOPUS).count());
        addIfPositive(result, teacher, period, WOS_ARTICLE,
                (int) contentDeduped.stream().filter(p -> p.getArticleCategory() == ArticleCategory.WOS).count());
        addIfPositive(result, teacher, period, CATEGORY_A_ARTICLE,
                (int) contentDeduped.stream().filter(p -> p.getArticleCategory() == ArticleCategory.CATEGORY_A).count());
        addIfPositive(result, teacher, period, CATEGORY_B_ARTICLE,
                (int) contentDeduped.stream().filter(p -> p.getArticleCategory() == ArticleCategory.CATEGORY_B).count());
        return result;
    }

    private List<TeacherRating> calculatePatents(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<Publication> pubs = publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> inPeriodPublication(p, from, to))
                .toList();

        addIfPositive(result, teacher, period, PATENT,
                (int) pubs.stream().filter(p -> p.getType() == PublicationType.PATENT).count());
        addIfPositive(result, teacher, period, DECLARATIVE_PATENT,
                (int) pubs.stream().filter(p -> p.getType() == PublicationType.DECLARATIVE_PATENT).count());
        addIfPositive(result, teacher, period, COPYRIGHT,
                (int) pubs.stream().filter(p -> p.getType() == PublicationType.COPYRIGHT).count());
        return result;
    }

    private List<TeacherRating> calculateBooks(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<Publication> pubs = publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> inPeriodPublication(p, from, to))
                .toList();

        addIfPositive(result, teacher, period, TEXTBOOK,
                (int) pubs.stream().filter(p -> p.getType() == PublicationType.TEXTBOOK).count());
        addIfPositive(result, teacher, period, MONOGRAPH,
                (int) pubs.stream().filter(p -> p.getType() == PublicationType.MONOGRAPH).count());
        addIfPositive(result, teacher, period, STUDY_GUIDE,
                (int) pubs.stream().filter(p -> p.getType() == PublicationType.STUDY_GUIDE).count());
        return result;
    }

    private List<TeacherRating> calculateMethodical(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<Publication> pubs = publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> p.getType() == PublicationType.METHODICAL)
                .filter(p -> inPeriodPublication(p, from, to))
                .toList();

        // Автоматично класифікуємо публікації без підтипу за назвою
        for (Publication pub : pubs) {
            if (pub.getMethodicalSubtype() == null) {
                pub.setMethodicalSubtype(autoDetectMethodicalSubtype(pub));
                publicationRepository.save(pub);
            }
        }

        addIfPositive(result, teacher, period, PRACTICUM,
                (int) pubs.stream().filter(p -> p.getMethodicalSubtype() == MethodicalSubtype.PRACTICUM).count());
        addIfPositive(result, teacher, period, METHODICAL_GUIDELINES,
                (int) pubs.stream().filter(p -> p.getMethodicalSubtype() == MethodicalSubtype.METHODICAL_GUIDELINES).count());
        addIfPositive(result, teacher, period, E_COURSE,
                (int) pubs.stream().filter(p -> p.getMethodicalSubtype() == MethodicalSubtype.E_COURSE).count());
        addIfPositive(result, teacher, period, LECTURE_NOTES,
                (int) pubs.stream().filter(p -> p.getMethodicalSubtype() == MethodicalSubtype.LECTURE_NOTES).count());
        return result;
    }

    /** Автовизначення підтипу методичної праці за назвою/rawText */
    private MethodicalSubtype autoDetectMethodicalSubtype(Publication pub) {
        String text = ((pub.getTitle() != null ? pub.getTitle() : "") + " "
                + (pub.getRawText() != null ? pub.getRawText() : "")).toLowerCase();
        if (text.contains("практикум")) return MethodicalSubtype.PRACTICUM;
        if (text.contains("електронний курс") || text.contains("електронні курси")
                || text.contains("е-курс") || text.contains("е курс") || text.contains("екурс")
                || text.contains("дистанційн") || text.contains("on-line курс")
                || text.contains("online курс") || text.contains("онлайн курс")
                || text.contains("онлайн-курс") || text.contains("moodle"))
            return MethodicalSubtype.E_COURSE;
        if (text.contains("конспект лекцій") || text.contains("конспект лекції")
                || text.contains("конспекти лекцій") || text.contains("курс лекцій")
                || text.contains("курс лекції") || text.contains("тексти лекцій"))
            return MethodicalSubtype.LECTURE_NOTES;
        if (text.contains("робоча програма") || text.contains("робочі програми")
                || text.contains("рпнд") || text.contains("силабус") || text.contains("навчальна програма"))
            return MethodicalSubtype.WORK_PROGRAM;
        if (text.contains("методичн") && (text.contains("вказів") || text.contains("рекоменд")
                || text.contains("забезпечення") || text.contains("розробк")))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (text.contains("метод.") && (text.contains("вказів") || text.contains("рекоменд")))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (text.contains("для самостійної роботи") || text.contains("для самост. роботи")
                || text.contains("завдання для практичн") || text.contains("завдання для лаборатор"))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (text.contains("методичн") || text.contains("метод."))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        return MethodicalSubtype.LECTURE_NOTES;
    }

    private List<TeacherRating> calculateDissertation(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();

        // Беремо ВСІ ступені викладача — кожен захист у звітному періоді нараховує окремо.
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());

        int doctoral = 0;
        int phd = 0;

        for (var d : degrees) {
            if (d.getDiplomaDate() == null || !inPeriod(d.getDiplomaDate(), from, to)) continue;
            if (d.getDegree() == null) continue;
            if (ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking.isDoctorOfScience(d.getDegree())) doctoral++;
            else phd++;
        }

        addIfPositive(result, teacher, period, DOCTORAL_DEFENSE, doctoral);
        addIfPositive(result, teacher, period, PHD_DEFENSE, phd);
        return result;
    }

    private List<TeacherRating> calculateSupervision(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<ScientificSupervision> items = supervisionRepository.findByTeacherId(teacher.getId()).stream()
                .filter(s -> s.getDefenseDate() != null && inPeriod(s.getDefenseDate(), from, to))
                .toList();

        int doctoral = (int) items.stream().filter(s ->
                s.getDegreeType() == DegreeType.DSC || s.getDegreeType() == DegreeType.DOCTOR
        ).count();
        int phd = (int) items.stream().filter(s ->
                s.getDegreeType() == DegreeType.PHD || s.getDegreeType() == DegreeType.CANDIDATE
        ).count();

        addIfPositive(result, teacher, period, DOCTORAL_SUPERVISION, doctoral);
        addIfPositive(result, teacher, period, PHD_SUPERVISION, phd);
        return result;
    }

    private List<TeacherRating> calculateAttestation(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<AttestationActivity> items = attestationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(a -> a.getDefenseDate() != null && inPeriod(a.getDefenseDate(), from, to))
                .toList();

        // Голова разової спецради (CHAIR) — НЕ враховується в рейтингу за керівними документами.
        int opponents = (int) items.stream().filter(a -> a.getRole() == AttestationRole.OPPONENT).count();
        int reviewers = (int) items.stream().filter(a -> a.getRole() == AttestationRole.REVIEWER).count();
        int members = (int) items.stream().filter(a -> a.getRole() == AttestationRole.COUNCIL_MEMBER).count();

        addIfPositive(result, teacher, period, OFFICIAL_OPPONENT, opponents);
        addIfPositive(result, teacher, period, REVIEWER, reviewers);
        addIfPositive(result, teacher, period, COUNCIL_MEMBER, members);
        return result;
    }

    private List<TeacherRating> calculateEditorial(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = editorialRepository.findByTeacherId(teacher.getId()).stream()
                .filter(e -> overlaps(e.getDateFrom(), e.getDateTo(), from, to))
                .count();
        addIfPositive(result, teacher, period, EDITORIAL_BOARD, (int) count);
        return result;
    }

    private List<TeacherRating> calculateExpertCouncil(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = expertCouncilRepository.findByTeacherId(teacher.getId()).stream()
                .filter(e -> overlaps(e.getDateFrom(), e.getDateTo(), from, to))
                .count();
        addIfPositive(result, teacher, period, EXPERT_COUNCIL, (int) count);
        return result;
    }

    private List<TeacherRating> calculateInternational(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = internationalRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> overlaps(p.getDateFrom(), p.getDateTo(), from, to))
                .count();
        addIfPositive(result, teacher, period, INTERNATIONAL_PROJECT, (int) count);
        return result;
    }

    private List<TeacherRating> calculateConsulting(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = consultingRepository.findByTeacherId(teacher.getId()).stream()
                .filter(c -> overlaps(c.getDateFrom(), c.getDateTo(), from, to))
                .count();
        addIfPositive(result, teacher, period, SCIENTIFIC_CONSULTING, (int) count);
        return result;
    }

    private List<TeacherRating> calculateApprobation(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        // пп.12: апробаційні + науково-популярні + консультаційні + науково-експертні
        // Бали визначаються рівнем видання: Scopus/WoS=5, Міжнародний=3, Вітчизняний=2
        List<Publication> pubs = publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> inPeriodPublication(p, from, to))
                .filter(p -> p.getType() == PublicationType.APPROBATION
                        || p.getType() == PublicationType.POPULAR_SCIENTIFIC)
                .toList();

        // Автоматично класифікуємо публікації без підтипу за назвою
        for (Publication pub : pubs) {
            if (pub.getApprobationSubtype() == null) {
                pub.setApprobationSubtype(autoDetectApprobationSubtype(pub));
                publicationRepository.save(pub);
            }
        }

        // Content-dedup (одна теза додана двічі — об'єднуємо за DOI/назвою).
        // Volume-dedup ВИДАЛЕНО — кожна теза/публікація рахується окремо.
        List<Publication> contentDeduped = publicationClassifier.deduplicateByContent(pubs);

        // Scopus / Web of Science
        int scopus = (int) contentDeduped.stream().filter(p ->
                p.getApprobationSubtype() == ApprobationSubtype.SCOPUS_WOS
        ).count();
        // Міжнародний журнал
        int international = (int) contentDeduped.stream().filter(p ->
                p.getApprobationSubtype() == ApprobationSubtype.INTERNATIONAL
        ).count();
        // Вітчизняний журнал
        int domestic = (int) contentDeduped.stream().filter(p ->
                p.getApprobationSubtype() == ApprobationSubtype.DOMESTIC
        ).count();

        addIfPositive(result, teacher, period, APPROBATION_SCOPUS, scopus);
        addIfPositive(result, teacher, period, APPROBATION_INTERNATIONAL, international);
        addIfPositive(result, teacher, period, APPROBATION_DOMESTIC, domestic);
        return result;
    }

    /** Автовизначення рівня видання для апробаційних публікацій */
    private ApprobationSubtype autoDetectApprobationSubtype(Publication pub) {
        String text = ((pub.getTitle() != null ? pub.getTitle() : "") + " "
                + (pub.getRawText() != null ? pub.getRawText() : "")
                + " " + (pub.getJournalName() != null ? pub.getJournalName() : "")
                + " " + (pub.getConferenceInfo() != null ? pub.getConferenceInfo() : "")).toLowerCase();
        if (text.contains("scopus") || text.contains("web of science") || text.contains("wos")
                || text.contains("ceur"))
            return ApprobationSubtype.SCOPUS_WOS;
        if (text.contains("ieee") || text.contains("springer") || text.contains("elsevier")
                || text.contains("wiley") || text.contains("acm ") || text.contains("mdpi")
                || text.contains("taylor & francis") || text.contains("de gruyter")
                || text.contains("cambridge university") || text.contains("oxford university")
                || text.contains("nato "))
            return ApprobationSubtype.INTERNATIONAL;
        return ApprobationSubtype.DOMESTIC;
    }

    private List<TeacherRating> calculateForeignLangTeaching(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        // Сумуємо години за академічні роки, що перетинаються з рейтинговим періодом
        int totalHours = foreignLangTeachingRepository.findByTeacherId(teacher.getId()).stream()
                .filter(f -> academicYearOverlaps(f.getAcademicYear(), from, to))
                .mapToInt(f -> f.getHours() != null ? f.getHours() : 0)
                .sum();
        if (totalHours >= 50) {
            addIfPositive(result, teacher, period, FOREIGN_LANGUAGE_TEACHING, 1);
        }
        return result;
    }

    private List<TeacherRating> calculateOlympiads(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<OlympiadGuidance> items = olympiadRepository.findByTeacherId(teacher.getId()).stream()
                .filter(o -> o.getYear() != null && inPeriodByYear(o.getYear(), from, to))
                .toList();

        for (OlympiadGuidance o : items) {
            String res = o.getResult() != null ? o.getResult().toLowerCase() : "";
            String name = o.getOlympiadName() != null ? o.getOlympiadName().toLowerCase() : "";

            // Наукові гуртки — окремий критерій (2 бали)
            if (o.getActivityType() == Pp14ActivityType.SCIENTIFIC_GROUP) {
                addIfPositive(result, teacher, period, SCIENCE_GROUP_LEADER, 1);
                continue;
            }

            // Визначаємо масштаб: з поля competitionScope, інакше з тексту
            CompetitionScope scope = o.getCompetitionScope();
            if (scope == null) {
                scope = detectCompetitionScope(res + " " + name);
            }

            // Бали лише за досягнення результатів
            if (scope == CompetitionScope.INTERNATIONAL) {
                addIfPositive(result, teacher, period, OLYMPIAD_INTERNATIONAL_PRIZE, 1);
            } else {
                addIfPositive(result, teacher, period, OLYMPIAD_NATIONAL_PRIZE, 1);
            }
        }
        return result;
    }

    /** Автовизначення масштабу заходу з тексту (фолбек для старих записів) */
    private CompetitionScope detectCompetitionScope(String text) {
        if (text.contains("міжнарод") || text.contains("international")
                || text.contains("ieee") || text.contains("acm ") || text.contains("nato"))
            return CompetitionScope.INTERNATIONAL;
        return CompetitionScope.NATIONAL;
    }

    private List<TeacherRating> calculateCombatVeteran(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        // УБД — по даті видачі посвідчення
        if (teacher.isCombatVeteranStatus() && teacher.getCombatVeteranDocDate() != null
                && inPeriod(teacher.getCombatVeteranDocDate(), from, to)) {
            addIfPositive(result, teacher, period, COMBAT_VETERAN, 1);
        }
        // Бойовий досвід — парсимо combatExperienceDates
        if (teacher.getCombatExperienceDates() != null && !teacher.getCombatExperienceDates().isBlank()) {
            if (parseCombatDatesOverlap(teacher.getCombatExperienceDates(), from, to)) {
                addIfPositive(result, teacher, period, COMBAT_EXPERIENCE, 1);
            }
        }
        return result;
    }

    private List<TeacherRating> calculateMilitary(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<MilitaryMission> items = militaryMissionRepository.findByTeacherId(teacher.getId()).stream()
                .filter(m -> overlaps(m.getDateFrom(), m.getDateTo(), from, to))
                .toList();

        int un = (int) items.stream().filter(m -> m.getMissionType() == MissionType.UN_PEACEKEEPING).count();
        int nato = (int) items.stream().filter(m -> m.getMissionType() == MissionType.NATO_EXERCISE).count();

        addIfPositive(result, teacher, period, UN_PEACEKEEPING, un);
        addIfPositive(result, teacher, period, NATO_EXERCISES, nato);
        return result;
    }

    private List<TeacherRating> calculateProfAssociation(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = profAssociationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(a -> overlaps(a.getDateFrom(), a.getDateTo(), from, to))
                .count();
        addIfPositive(result, teacher, period, PROFESSIONAL_ASSOCIATION, (int) count);
        return result;
    }

    private List<TeacherRating> calculateAcademicTitle(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();

        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacher.getId());
        int profCount = 0, docentCount = 0;

        for (var t : titles) {
            if (t.getAttestatDate() == null || !inPeriod(t.getAttestatDate(), from, to)) continue;
            String name = t.getTitleName();
            if (name == null) continue;
            String lower = name.toLowerCase();
            if (lower.contains("професор")) profCount++;
            else if (lower.contains("доцент")) docentCount++;
        }

        addIfPositive(result, teacher, period, PROFESSOR_TITLE, profCount);
        addIfPositive(result, teacher, period, DOCENT_TITLE, docentCount);
        return result;
    }

    private List<TeacherRating> calculateQualification(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<QualificationImprovement> items = qualificationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(q -> overlaps(q.getStartDate(), q.getEndDate(), from, to))
                .toList();

        // Загальне ПК — кредити. Поле country тут інформативне — не впливає на рейтинг.
        // Міжнародні стажування винесені в окрему сутність ForeignInternship
        // (вкладка "Інші досягнення"), див. метод calculateForeignInternships.
        double totalCredits = items.stream()
                .filter(q -> q.getCategory() != QualificationCategory.MILITARY_COURSE)
                .mapToDouble(q -> q.getCredits() != null ? q.getCredits() : 0)
                .sum();
        if (totalCredits > 0) {
            addIfPositive(result, teacher, period, QUALIFICATION_CREDIT, (int) Math.floor(totalCredits));
        }

        // Курси ВО — за рівнем (L2=5, L3=10, L4=15), з фолбеком на тривалість
        items.stream()
                .filter(q -> q.getCategory() == QualificationCategory.MILITARY_COURSE)
                .forEach(q -> {
                    ua.edu.teacherlicence.qualification.model.MilitaryCourseLevel level = q.getMilitaryCourseLevel();
                    if (level != null) {
                        // Визначаємо за явно вказаним рівнем
                        switch (level) {
                            case L4 -> addIfPositive(result, teacher, period, MILITARY_COURSE_10_PLUS, 1);
                            case L3 -> addIfPositive(result, teacher, period, MILITARY_COURSE_6_10, 1);
                            case L2 -> addIfPositive(result, teacher, period, MILITARY_COURSE_3_6, 1);
                        }
                    } else {
                        // Фолбек: за тривалістю (для старих записів)
                        long months = 0;
                        if (q.getStartDate() != null && q.getEndDate() != null) {
                            months = ChronoUnit.MONTHS.between(q.getStartDate(), q.getEndDate());
                        }
                        if (months >= 10) {
                            addIfPositive(result, teacher, period, MILITARY_COURSE_10_PLUS, 1);
                        } else if (months >= 6) {
                            addIfPositive(result, teacher, period, MILITARY_COURSE_6_10, 1);
                        } else if (months >= 3) {
                            addIfPositive(result, teacher, period, MILITARY_COURSE_3_6, 1);
                        }
                    }
                });

        return result;
    }

    private List<TeacherRating> calculateLanguageCertificate(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        languageSkillRepository.findByTeacherId(teacher.getId()).stream()
                .filter(ls -> ls.getCertificateDate() != null && inPeriod(ls.getCertificateDate(), from, to))
                .forEach(ls -> {
                    Integer smrLevel = ls.getSmrLevel();
                    if (smrLevel != null) {
                        if (smrLevel >= 3) {
                            addIfPositive(result, teacher, period, SMR_LEVEL_3, 1);
                        } else if (smrLevel >= 2) {
                            addIfPositive(result, teacher, period, SMR_LEVEL_2, 1);
                        } else if (smrLevel >= 1) {
                            addIfPositive(result, teacher, period, SMR_LEVEL_1, 1);
                        }
                    }
                });
        return result;
    }

    private List<TeacherRating> calculateOpenLessons(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = openLessonRepository.findByTeacherIdOrderByDateDesc(teacher.getId()).stream()
                .filter(l -> l.getDate() != null && inPeriod(l.getDate(), from, to))
                .count();
        addIfPositive(result, teacher, period, OPEN_LESSON, (int) count);
        return result;
    }

    private List<TeacherRating> calculateExperiments(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = experimentRepository.findByTeacherIdOrderByDateDesc(teacher.getId()).stream()
                .filter(e -> e.getDate() != null && inPeriod(e.getDate(), from, to))
                .count();
        addIfPositive(result, teacher, period, METHODOLOGICAL_EXPERIMENT, (int) count);
        return result;
    }

    private List<TeacherRating> calculateMobility(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = mobilityRepository.findByTeacherIdOrderByDateFromDesc(teacher.getId()).stream()
                .filter(m -> overlaps(m.getDateFrom(), m.getDateTo(), from, to))
                .count();
        addIfPositive(result, teacher, period, ACADEMIC_MOBILITY, (int) count);
        return result;
    }

    /**
     * Міжнародне стажування — окрема сутність {@code foreign_internships}.
     * Раніше критерій нараховувався з {@code qualification_improvements.country},
     * але це некоректно — курси ПК — це різна активність. Тепер це окремий
     * запис на вкладці "Інші досягнення" профілю викладача.
     */
    private List<TeacherRating> calculateForeignInternships(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        long count = foreignInternshipRepository.findByTeacherIdOrderByDateFromDesc(teacher.getId()).stream()
                .filter(fi -> overlaps(fi.getDateFrom(), fi.getDateTo(), from, to))
                .count();
        addIfPositive(result, teacher, period, FOREIGN_INTERNSHIP, (int) count);
        return result;
    }

    private List<TeacherRating> calculateWorkingGroups(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        List<ProgramWorkingGroup> groups = workingGroupRepository.findByTeacherId(teacher.getId()).stream()
                .filter(g -> g.getOrderDate() != null && inPeriod(g.getOrderDate(), from, to))
                .toList();

        int chairs = (int) groups.stream().filter(g -> g.getRole() == WorkingGroupRole.CHAIR).count();
        int members = (int) groups.stream().filter(g -> g.getRole() == WorkingGroupRole.MEMBER).count();

        addIfPositive(result, teacher, period, WORKING_GROUP_CHAIR, chairs);
        addIfPositive(result, teacher, period, WORKING_GROUP_MEMBER, members);
        return result;
    }

    private List<TeacherRating> calculateMilitaryEducation(Teacher teacher, RatingPeriod period, LocalDate from, LocalDate to) {
        List<TeacherRating> result = new ArrayList<>();
        if (teacher.getMilitaryEducationLevel() != null && teacher.getMilitaryEducationDiplomaDate() != null
                && inPeriod(teacher.getMilitaryEducationDiplomaDate(), from, to)) {
            if (teacher.getMilitaryEducationLevel() == MilitaryEducationLevel.STRATEGIC) {
                addIfPositive(result, teacher, period, MILITARY_ED_STRATEGIC, 1);
            } else {
                addIfPositive(result, teacher, period, MILITARY_ED_OPERATIONAL, 1);
            }
        }
        return result;
    }

    // ══════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════

    private void addIfPositive(List<TeacherRating> list, Teacher teacher, RatingPeriod period,
                                RatingCriterion criterion, int count) {
        if (count <= 0) return;
        // Шукаємо чи вже є запис з таким criterion — якщо є, збільшуємо count/score
        for (TeacherRating existing : list) {
            if (existing.getCriterion() == criterion) {
                existing.setCount(existing.getCount() + count);
                existing.setScore(existing.getCount() * criterion.getPoints());
                return;
            }
        }
        list.add(TeacherRating.builder()
                .teacher(teacher)
                .period(period)
                .criterion(criterion)
                .count(count)
                .score(count * criterion.getPoints())
                .build());
    }

    /** Чи дата потрапляє в період [from, to] */
    private boolean inPeriod(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    /** Чи рік потрапляє в період рейтингування (для сутностей без повної дати — OpenLesson, OlympiadGuidance тощо). */
    private boolean inPeriodByYear(Integer year, LocalDate from, LocalDate to) {
        if (year == null) return false;
        return year >= from.getYear() && year <= to.getYear();
    }

    /** Чи публікація потрапляє в період рейтингування (за повною датою або, якщо її нема, — за роком). */
    private boolean inPeriodPublication(Publication p, LocalDate from, LocalDate to) {
        LocalDate effective = p.effectiveDate();
        if (effective == null) return false;
        return !effective.isBefore(from) && !effective.isAfter(to);
    }

    /** Чи діапазон [dateFrom, dateTo] перетинається з [from, to] */
    private boolean overlaps(LocalDate dateFrom, LocalDate dateTo, LocalDate from, LocalDate to) {
        if (dateFrom == null && dateTo == null) return false;
        LocalDate start = dateFrom != null ? dateFrom : dateTo;
        LocalDate end = dateTo != null ? dateTo : dateFrom;
        return !start.isAfter(to) && !end.isBefore(from);
    }

    /** Чи навчальний рік "2024-2025" перетинається з рейтинговим періодом */
    private boolean academicYearOverlaps(String academicYear, LocalDate from, LocalDate to) {
        if (academicYear == null) return false;
        try {
            String[] parts = academicYear.split("-");
            int startYear = Integer.parseInt(parts[0].trim());
            // Навчальний рік: вересень startYear – червень startYear+1
            LocalDate acadStart = LocalDate.of(startYear, 9, 1);
            LocalDate acadEnd = LocalDate.of(startYear + 1, 6, 30);
            return !acadStart.isAfter(to) && !acadEnd.isBefore(from);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Парсинг рядка combatExperienceDates на перетин з періодом рейтингу [from, to].
     *
     * <p>Підтримувані формати діапазону між двома датами dd.MM.yyyy:
     * <ul>
     *   <li>{@code dd.MM.yyyy – dd.MM.yyyy} (en-dash, em-dash, hyphen)</li>
     *   <li>{@code dd.MM.yyyy / dd.MM.yyyy} (слеш)</li>
     *   <li>{@code dd.MM.yyyy по dd.MM.yyyy}</li>
     *   <li>Кілька діапазонів через кому, крапку з комою або в квадратних дужках.</li>
     * </ul>
     *
     * <p>Фолбек: якщо у тексті не знайдено жодного валідного повного діапазону,
     * перевіряємо самотні роки — але ТІЛЬКИ якщо рік СТРОГО входить в [from, to]
     * (а не лише {@code year == from.year}), щоб запис з лютого 2025 не зараховувався
     * у період, що стартує 21.06.2025.
     */
    private boolean parseCombatDatesOverlap(String dates, LocalDate from, LocalDate to) {
        if (dates == null || dates.isBlank()) return false;

        // Спочатку шукаємо повні діапазони dat. Роздільник: –, —, -, /, "по"
        Pattern rangePattern = Pattern.compile(
                "(\\d{2}\\.\\d{2}\\.\\d{4})\\s*(?:[–—\\-/]|по)\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
        Matcher rangeMatcher = rangePattern.matcher(dates);
        boolean foundAnyRange = false;
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

        while (rangeMatcher.find()) {
            foundAnyRange = true;
            try {
                LocalDate rangeStart = LocalDate.parse(rangeMatcher.group(1), fmt);
                LocalDate rangeEnd = LocalDate.parse(rangeMatcher.group(2), fmt);
                // Перевіряємо перетин діапазонів: [rangeStart, rangeEnd] ∩ [from, to]
                if (!rangeEnd.isBefore(from) && !rangeStart.isAfter(to)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // Якщо знайшли діапазони, але жоден не перетинається з періодом — НЕ зараховуємо.
        if (foundAnyRange) return false;

        // Фолбек по рокам (для старих/неструктурованих записів). Строго:
        // якщо період [from..to] припадає на один рік — рік має дорівнювати тому року;
        // якщо період охоплює кілька років — рік має бути СТРОГО МІЖ ними (а not лише на межі).
        // Інакше дата на кшталт "лютий 2025" зарахувалася б у період, що почався у червні 2025.
        Pattern yearPattern = Pattern.compile("(\\d{4})");
        Matcher matcher = yearPattern.matcher(dates);
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year > from.getYear() && year < to.getYear()) {
                return true; // рік повністю всередині періоду — точно перетин є
            }
        }
        return false;
    }
}
