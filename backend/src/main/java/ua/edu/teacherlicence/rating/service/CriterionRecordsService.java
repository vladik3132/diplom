package ua.edu.teacherlicence.rating.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.publication.model.*;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.qualification.model.MilitaryCourseLevel;
import ua.edu.teacherlicence.qualification.model.QualificationCategory;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;
import ua.edu.teacherlicence.rating.dto.CriterionRecordDto;
import ua.edu.teacherlicence.rating.model.*;
import ua.edu.teacherlicence.rating.repository.*;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.MilitaryEducationLevel;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Сервіс для отримання записів-джерел, за якими нараховано бали за певний критерій рейтингу.
 * Повторює логіку фільтрації з RatingCalculationService, але повертає DTO записів замість балів.
 */
@Service
@RequiredArgsConstructor
public class CriterionRecordsService {

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
    private final RatingPeriodRepository periodRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public List<CriterionRecordDto> getRecords(Long periodId, Long teacherId, RatingCriterion criterion) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Викладача не знайдено: " + teacherId));
        RatingPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Період не знайдено: " + periodId));
        LocalDate from = period.getStartDate();
        LocalDate to = period.getEndDate();

        return switch (criterion) {
            // пп.1 — Публікації
            case SCOPUS_ARTICLE -> publicationRecords(teacher, from, to, ArticleCategory.SCOPUS);
            case WOS_ARTICLE -> publicationRecords(teacher, from, to, ArticleCategory.WOS);
            case CATEGORY_A_ARTICLE -> publicationRecords(teacher, from, to, ArticleCategory.CATEGORY_A);
            case CATEGORY_B_ARTICLE -> publicationRecords(teacher, from, to, ArticleCategory.CATEGORY_B);

            // пп.2 — Патенти
            case PATENT -> pubTypeRecords(teacher, from, to, PublicationType.PATENT);
            case DECLARATIVE_PATENT -> pubTypeRecords(teacher, from, to, PublicationType.DECLARATIVE_PATENT);
            case COPYRIGHT -> pubTypeRecords(teacher, from, to, PublicationType.COPYRIGHT);

            // пп.3 — Підручники / монографії
            case TEXTBOOK -> pubTypeRecords(teacher, from, to, PublicationType.TEXTBOOK);
            case MONOGRAPH -> pubTypeRecords(teacher, from, to, PublicationType.MONOGRAPH);
            case STUDY_GUIDE -> pubTypeRecords(teacher, from, to, PublicationType.STUDY_GUIDE);

            // пп.4 — Методичні праці
            case PRACTICUM -> methodicalRecords(teacher, from, to, MethodicalSubtype.PRACTICUM);
            case METHODICAL_GUIDELINES -> methodicalRecords(teacher, from, to, MethodicalSubtype.METHODICAL_GUIDELINES);
            case E_COURSE -> methodicalRecords(teacher, from, to, MethodicalSubtype.E_COURSE);
            case LECTURE_NOTES -> methodicalRecords(teacher, from, to, MethodicalSubtype.LECTURE_NOTES);

            // пп.5 — Захист дисертації
            case DOCTORAL_DEFENSE, PHD_DEFENSE -> dissertationRecords(teacher, from, to, criterion);

            // пп.6 — Наукове керівництво
            case DOCTORAL_SUPERVISION -> supervisionRecords(teacher, from, to, true);
            case PHD_SUPERVISION -> supervisionRecords(teacher, from, to, false);

            // пп.7 — Атестація (голова разової спецради НЕ рейтингується)
            case OFFICIAL_OPPONENT -> attestationRecords(teacher, from, to, AttestationRole.OPPONENT);
            case REVIEWER -> attestationRecords(teacher, from, to, AttestationRole.REVIEWER);
            case COUNCIL_MEMBER -> attestationRecords(teacher, from, to, AttestationRole.COUNCIL_MEMBER);

            // пп.8 — Редколегія
            case EDITORIAL_BOARD -> editorialRecords(teacher, from, to);

            // пп.9 — Експертна рада
            case EXPERT_COUNCIL -> expertCouncilRecords(teacher, from, to);

            // пп.10 — Міжнародні проєкти
            case INTERNATIONAL_PROJECT -> internationalRecords(teacher, from, to);

            // пп.11 — Наукове консультування
            case SCIENTIFIC_CONSULTING -> consultingRecords(teacher, from, to);

            // пп.12 — Апробації
            case APPROBATION_SCOPUS -> approbationRecords(teacher, from, to, ApprobationSubtype.SCOPUS_WOS);
            case APPROBATION_INTERNATIONAL -> approbationRecords(teacher, from, to, ApprobationSubtype.INTERNATIONAL);
            case APPROBATION_DOMESTIC -> approbationRecords(teacher, from, to, ApprobationSubtype.DOMESTIC);

            // пп.13 — Іноземна мова
            case FOREIGN_LANGUAGE_TEACHING -> foreignLangRecords(teacher, from, to);

            // пп.14-15 — Олімпіади
            case OLYMPIAD_INTERNATIONAL_PRIZE, OLYMPIAD_NATIONAL_PRIZE,
                 SCIENCE_GROUP_LEADER -> olympiadRecords(teacher, from, to, criterion);

            // пп.16 — УБД
            case COMBAT_VETERAN, COMBAT_EXPERIENCE -> combatRecords(teacher, from, to, criterion);

            // пп.17-18 — Миротворчі/НАТО
            case UN_PEACEKEEPING -> militaryMissionRecords(teacher, from, to, MissionType.UN_PEACEKEEPING);
            case NATO_EXERCISES -> militaryMissionRecords(teacher, from, to, MissionType.NATO_EXERCISE);

            // пп.19 — Професійні об'єднання
            case PROFESSIONAL_ASSOCIATION -> profAssociationRecords(teacher, from, to);

            // Вчене звання
            case PROFESSOR_TITLE, DOCENT_TITLE -> academicTitleRecords(teacher, from, to, criterion);

            // ПК
            case QUALIFICATION_CREDIT -> qualificationCreditRecords(teacher, from, to);
            case FOREIGN_INTERNSHIP -> foreignInternshipRecords(teacher, from, to);

            // Курси ВО
            case MILITARY_COURSE_3_6 -> militaryCourseRecords(teacher, from, to, 3, 6);
            case MILITARY_COURSE_6_10 -> militaryCourseRecords(teacher, from, to, 6, 10);
            case MILITARY_COURSE_10_PLUS -> militaryCourseRecords(teacher, from, to, 10, Integer.MAX_VALUE);

            // СМР
            case SMR_LEVEL_1 -> smrRecords(teacher, from, to, 1);
            case SMR_LEVEL_2 -> smrRecords(teacher, from, to, 2);
            case SMR_LEVEL_3 -> smrRecords(teacher, from, to, 3);

            // Решта
            case OPEN_LESSON -> openLessonRecords(teacher, from, to);
            case METHODOLOGICAL_EXPERIMENT -> experimentRecords(teacher, from, to);
            case ACADEMIC_MOBILITY -> mobilityRecords(teacher, from, to);
            case WORKING_GROUP_CHAIR -> workingGroupRecords(teacher, from, to, WorkingGroupRole.CHAIR);
            case WORKING_GROUP_MEMBER -> workingGroupRecords(teacher, from, to, WorkingGroupRole.MEMBER);

            // Рівень ВО
            case MILITARY_ED_OPERATIONAL, MILITARY_ED_STRATEGIC -> militaryEducationRecords(teacher, from, to, criterion);
        };
    }

    // ══════════════════════════════════════════════
    //  Конкретні методи
    // ══════════════════════════════════════════════

    private List<CriterionRecordDto> publicationRecords(Teacher teacher, LocalDate from, LocalDate to, ArticleCategory cat) {
        // Content-dedup ВСІХ статей (без фільтра категорії) → переможець-дубль може мати ВИЩУ
        // категорію. Тоді стаття з cat=CategoryA, у якої є дубль-переможець Scopus, НЕ покажеться
        // у списку CategoryA. Узгоджено з RatingCalculationService.calculatePublications.
        // Volume-dedup ВИДАЛЕНО — кожна публікація рахується окремо.
        List<Publication> all = publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> p.getType() == PublicationType.ARTICLE && p.getArticleCategory() != null)
                .filter(p -> inPeriodPublication(p, from, to))
                .collect(Collectors.toList());
        List<Publication> contentDeduped = publicationClassifier.deduplicateByContent(all);
        return contentDeduped.stream()
                .filter(p -> p.getArticleCategory() == cat)
                .map(this::pubDto)
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> pubTypeRecords(Teacher teacher, LocalDate from, LocalDate to, PublicationType type) {
        return publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> p.getType() == type)
                .filter(p -> inPeriodPublication(p, from, to))
                .map(p -> pubDto(p))
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> methodicalRecords(Teacher teacher, LocalDate from, LocalDate to, MethodicalSubtype sub) {
        return publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> p.getType() == PublicationType.METHODICAL)
                .filter(p -> inPeriodPublication(p, from, to))
                .filter(p -> p.getMethodicalSubtype() == sub)
                .map(p -> pubDto(p))
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> approbationRecords(Teacher teacher, LocalDate from, LocalDate to, ApprobationSubtype sub) {
        // Content-dedup ВСІХ апробацій до filter за subtype (один контент може бути двічі
        // з різними subtypes — тоді переможе вища категорія).
        // Volume-dedup ВИДАЛЕНО — кожна теза/публікація рахується окремо.
        List<Publication> all = publicationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> p.getType() == PublicationType.APPROBATION || p.getType() == PublicationType.POPULAR_SCIENTIFIC)
                .filter(p -> inPeriodPublication(p, from, to))
                .collect(Collectors.toList());
        List<Publication> contentDeduped = publicationClassifier.deduplicateByContent(all);
        return contentDeduped.stream()
                .filter(p -> p.getApprobationSubtype() == sub)
                .map(this::pubDto)
                .collect(Collectors.toList());
    }

    private CriterionRecordDto pubDto(Publication p) {
        String subtitle = Stream_of(
                p.getJournalName(),
                p.getYear() != null ? String.valueOf(p.getYear()) : null,
                p.getAuthors()
        );
        return CriterionRecordDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .subtitle(subtitle)
                .entityType("PUBLICATION")
                .build();
    }

    private List<CriterionRecordDto> dissertationRecords(Teacher teacher, LocalDate from, LocalDate to, RatingCriterion criterion) {
        List<CriterionRecordDto> result = new ArrayList<>();

        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());
        for (var d : degrees) {
            if (d.getDiplomaDate() == null || !inPeriod(d.getDiplomaDate(), from, to)) continue;
            if (d.getDegree() == null) continue;
            boolean isDocSci = ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking.isDoctorOfScience(d.getDegree());
            if ((criterion == RatingCriterion.DOCTORAL_DEFENSE && isDocSci)
                    || (criterion == RatingCriterion.PHD_DEFENSE && !isDocSci)) {
                String subtitle = "Диплом: " + fmtDate(d.getDiplomaDate());
                if (d.getSpeciality() != null && !d.getSpeciality().isBlank()) {
                    subtitle += " · " + d.getSpeciality();
                }
                result.add(CriterionRecordDto.builder()
                        .id(d.getId())
                        .title(d.getDegree())
                        .subtitle(subtitle)
                        .entityType("ACADEMIC_DEGREE")
                        .build());
            }
        }
        return result;
    }

    private List<CriterionRecordDto> supervisionRecords(Teacher teacher, LocalDate from, LocalDate to, boolean doctoral) {
        return supervisionRepository.findByTeacherId(teacher.getId()).stream()
                .filter(s -> s.getDefenseDate() != null && inPeriod(s.getDefenseDate(), from, to))
                .filter(s -> doctoral
                        ? (s.getDegreeType() == DegreeType.DSC || s.getDegreeType() == DegreeType.DOCTOR)
                        : (s.getDegreeType() == DegreeType.PHD || s.getDegreeType() == DegreeType.CANDIDATE))
                .map(s -> CriterionRecordDto.builder()
                        .id(s.getId())
                        .title(s.getStudentName())
                        .subtitle(Stream_of(s.getTopic(), fmtDate(s.getDefenseDate())))
                        .entityType("SUPERVISION")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> attestationRecords(Teacher teacher, LocalDate from, LocalDate to, AttestationRole role) {
        return attestationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(a -> a.getDefenseDate() != null && inPeriod(a.getDefenseDate(), from, to))
                .filter(a -> a.getRole() == role)
                .map(a -> CriterionRecordDto.builder()
                        .id(a.getId())
                        .title(a.getStudentName())
                        .subtitle(Stream_of(a.getCouncilName(), fmtDate(a.getDefenseDate())))
                        .entityType("ATTESTATION")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> editorialRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return editorialRepository.findByTeacherId(teacher.getId()).stream()
                .filter(e -> overlaps(e.getDateFrom(), e.getDateTo(), from, to))
                .map(e -> CriterionRecordDto.builder()
                        .id(e.getId())
                        .title(e.getJournalOrProjectName())
                        .subtitle(Stream_of(e.getRole() != null ? e.getRole().name() : null, datePeriod(e.getDateFrom(), e.getDateTo())))
                        .entityType("EDITORIAL")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> expertCouncilRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return expertCouncilRepository.findByTeacherId(teacher.getId()).stream()
                .filter(e -> overlaps(e.getDateFrom(), e.getDateTo(), from, to))
                .map(e -> CriterionRecordDto.builder()
                        .id(e.getId())
                        .title(e.getCouncilName())
                        .subtitle(Stream_of(e.getRole(), datePeriod(e.getDateFrom(), e.getDateTo())))
                        .entityType("EXPERT_COUNCIL")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> internationalRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return internationalRepository.findByTeacherId(teacher.getId()).stream()
                .filter(p -> overlaps(p.getDateFrom(), p.getDateTo(), from, to))
                .map(p -> CriterionRecordDto.builder()
                        .id(p.getId())
                        .title(p.getProjectName())
                        .subtitle(Stream_of(p.getProgram() != null ? p.getProgram().name() : null, p.getRole(), datePeriod(p.getDateFrom(), p.getDateTo())))
                        .entityType("INTERNATIONAL_PROJECT")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> consultingRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return consultingRepository.findByTeacherId(teacher.getId()).stream()
                .filter(c -> overlaps(c.getDateFrom(), c.getDateTo(), from, to))
                .map(c -> CriterionRecordDto.builder()
                        .id(c.getId())
                        .title(c.getOrganizationName())
                        .subtitle(Stream_of(c.getContractNumber(), datePeriod(c.getDateFrom(), c.getDateTo())))
                        .entityType("CONSULTING")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> foreignLangRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return foreignLangTeachingRepository.findByTeacherId(teacher.getId()).stream()
                .filter(f -> academicYearOverlaps(f.getAcademicYear(), from, to))
                .map(f -> CriterionRecordDto.builder()
                        .id(f.getId())
                        .title(f.getDisciplineName())
                        .subtitle(Stream_of(f.getLanguage(), f.getHours() != null ? f.getHours() + " год." : null, f.getAcademicYear()))
                        .entityType("FOREIGN_LANG_TEACHING")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> olympiadRecords(Teacher teacher, LocalDate from, LocalDate to, RatingCriterion criterion) {
        return olympiadRepository.findByTeacherId(teacher.getId()).stream()
                .filter(o -> o.getYear() != null && inPeriodByYear(o.getYear(), from, to))
                .filter(o -> matchesOlympiadCriterion(o, criterion))
                .map(o -> CriterionRecordDto.builder()
                        .id(o.getId())
                        .title(o.getOlympiadName())
                        .subtitle(Stream_of(o.getStudentName(), o.getResult(), o.getYear() != null ? String.valueOf(o.getYear()) : null))
                        .entityType("OLYMPIAD")
                        .build())
                .collect(Collectors.toList());
    }

    private boolean matchesOlympiadCriterion(OlympiadGuidance o, RatingCriterion criterion) {
        String res = o.getResult() != null ? o.getResult().toLowerCase() : "";
        String name = o.getOlympiadName() != null ? o.getOlympiadName().toLowerCase() : "";

        // Наукові гуртки
        if (criterion == RatingCriterion.SCIENCE_GROUP_LEADER) {
            return o.getActivityType() == Pp14ActivityType.SCIENTIFIC_GROUP;
        }
        // Гуртки не підпадають під олімпіадні критерії
        if (o.getActivityType() == Pp14ActivityType.SCIENTIFIC_GROUP) return false;

        // Визначаємо масштаб: з поля competitionScope, інакше з тексту
        CompetitionScope scope = o.getCompetitionScope();
        if (scope == null) {
            scope = detectCompetitionScope(res + " " + name);
        }

        return switch (criterion) {
            case OLYMPIAD_INTERNATIONAL_PRIZE -> scope == CompetitionScope.INTERNATIONAL;
            case OLYMPIAD_NATIONAL_PRIZE -> scope == CompetitionScope.NATIONAL;
            default -> false;
        };
    }

    /** Автовизначення масштабу заходу з тексту (фолбек для старих записів) */
    private CompetitionScope detectCompetitionScope(String text) {
        if (text.contains("міжнарод") || text.contains("international")
                || text.contains("ieee") || text.contains("acm ") || text.contains("nato"))
            return CompetitionScope.INTERNATIONAL;
        return CompetitionScope.NATIONAL;
    }

    private List<CriterionRecordDto> combatRecords(Teacher teacher, LocalDate from, LocalDate to, RatingCriterion criterion) {
        List<CriterionRecordDto> result = new ArrayList<>();
        if (criterion == RatingCriterion.COMBAT_VETERAN) {
            if (teacher.isCombatVeteranStatus() && teacher.getCombatVeteranDocDate() != null
                    && inPeriod(teacher.getCombatVeteranDocDate(), from, to)) {
                result.add(CriterionRecordDto.builder()
                        .id(teacher.getId())
                        .title("Посвідчення УБД")
                        .subtitle("Дата видачі: " + fmtDate(teacher.getCombatVeteranDocDate()))
                        .entityType("TEACHER")
                        .build());
            }
        } else if (criterion == RatingCriterion.COMBAT_EXPERIENCE) {
            if (teacher.getCombatExperienceDates() != null && !teacher.getCombatExperienceDates().isBlank()
                    && parseCombatDatesOverlap(teacher.getCombatExperienceDates(), from, to)) {
                result.add(CriterionRecordDto.builder()
                        .id(teacher.getId())
                        .title("Бойовий досвід")
                        .subtitle(teacher.getCombatExperienceDates())
                        .entityType("TEACHER")
                        .build());
            }
        }
        return result;
    }

    private List<CriterionRecordDto> militaryMissionRecords(Teacher teacher, LocalDate from, LocalDate to, MissionType type) {
        return militaryMissionRepository.findByTeacherId(teacher.getId()).stream()
                .filter(m -> m.getMissionType() == type)
                .filter(m -> overlaps(m.getDateFrom(), m.getDateTo(), from, to))
                .map(m -> CriterionRecordDto.builder()
                        .id(m.getId())
                        .title(m.getMissionName())
                        .subtitle(Stream_of(m.getCountry(), datePeriod(m.getDateFrom(), m.getDateTo())))
                        .entityType("MILITARY_MISSION")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> profAssociationRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return profAssociationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(a -> overlaps(a.getDateFrom(), a.getDateTo(), from, to))
                .map(a -> CriterionRecordDto.builder()
                        .id(a.getId())
                        .title(a.getOrganizationName())
                        .subtitle(Stream_of(a.getRole(), datePeriod(a.getDateFrom(), a.getDateTo())))
                        .entityType("PROFESSIONAL_ASSOCIATION")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> academicTitleRecords(Teacher teacher, LocalDate from, LocalDate to, RatingCriterion criterion) {
        List<CriterionRecordDto> result = new ArrayList<>();

        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacher.getId());
        for (var t : titles) {
            if (t.getAttestatDate() == null || !inPeriod(t.getAttestatDate(), from, to)) continue;
            String name = t.getTitleName();
            if (name == null) continue;
            String lower = name.toLowerCase();
            boolean isProf = lower.contains("професор");
            boolean isDoc = lower.contains("доцент");
            if ((criterion == RatingCriterion.PROFESSOR_TITLE && isProf)
                    || (criterion == RatingCriterion.DOCENT_TITLE && isDoc)) {
                String subtitle = "Атестат: " + fmtDate(t.getAttestatDate());
                if (t.getAttestat() != null && !t.getAttestat().isBlank()) {
                    subtitle += " · " + t.getAttestat();
                }
                result.add(CriterionRecordDto.builder()
                        .id(t.getId())
                        .title(name)
                        .subtitle(subtitle)
                        .entityType("ACADEMIC_TITLE")
                        .build());
            }
        }
        return result;
    }

    private List<CriterionRecordDto> qualificationCreditRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return qualificationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(q -> overlaps(q.getStartDate(), q.getEndDate(), from, to))
                .filter(q -> q.getCategory() != QualificationCategory.MILITARY_COURSE)
                .filter(q -> q.getCredits() != null && q.getCredits() > 0)
                .map(q -> CriterionRecordDto.builder()
                        .id(q.getId())
                        .title(q.getTitle())
                        .subtitle(Stream_of(q.getOrganization(),
                                q.getCredits() != null ? q.getCredits() + " кр." : null,
                                datePeriod(q.getStartDate(), q.getEndDate())))
                        .entityType("QUALIFICATION")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> foreignInternshipRecords(Teacher teacher, LocalDate from, LocalDate to) {
        // Тепер джерело — окрема сутність ForeignInternship (вкладка "Інші досягнення"),
        // а не qualification_improvements за полем country.
        return foreignInternshipRepository.findByTeacherIdOrderByDateFromDesc(teacher.getId()).stream()
                .filter(fi -> overlaps(fi.getDateFrom(), fi.getDateTo(), from, to))
                .map(fi -> CriterionRecordDto.builder()
                        .id(fi.getId())
                        .title(fi.getProgramName() != null ? fi.getProgramName() : "Міжнародне стажування")
                        .subtitle(Stream_of(fi.getInstitution(), fi.getCountry(),
                                datePeriod(fi.getDateFrom(), fi.getDateTo())))
                        .entityType("FOREIGN_INTERNSHIP")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> militaryCourseRecords(Teacher teacher, LocalDate from, LocalDate to, int minMonths, int maxMonths) {
        MilitaryCourseLevel targetLevel = minMonths >= 10 ? MilitaryCourseLevel.L4
                : minMonths >= 6 ? MilitaryCourseLevel.L3 : MilitaryCourseLevel.L2;
        return qualificationRepository.findByTeacherId(teacher.getId()).stream()
                .filter(q -> overlaps(q.getStartDate(), q.getEndDate(), from, to))
                .filter(q -> q.getCategory() == QualificationCategory.MILITARY_COURSE)
                .filter(q -> {
                    if (q.getMilitaryCourseLevel() != null) {
                        return q.getMilitaryCourseLevel() == targetLevel;
                    }
                    // Фолбек за тривалістю
                    long months = 0;
                    if (q.getStartDate() != null && q.getEndDate() != null) {
                        months = ChronoUnit.MONTHS.between(q.getStartDate(), q.getEndDate());
                    }
                    return months >= minMonths && months < maxMonths;
                })
                .map(q -> CriterionRecordDto.builder()
                        .id(q.getId())
                        .title(q.getTitle())
                        .subtitle(Stream_of(q.getOrganization(),
                                q.getMilitaryCourseLevel() != null ? q.getMilitaryCourseLevel().name() : null,
                                datePeriod(q.getStartDate(), q.getEndDate())))
                        .entityType("QUALIFICATION")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> smrRecords(Teacher teacher, LocalDate from, LocalDate to, int level) {
        return languageSkillRepository.findByTeacherId(teacher.getId()).stream()
                .filter(ls -> ls.getCertificateDate() != null && inPeriod(ls.getCertificateDate(), from, to))
                .filter(ls -> {
                    Integer smr = ls.getSmrLevel();
                    return switch (level) {
                        case 3 -> smr != null && smr >= 3;
                        case 2 -> smr != null && smr >= 2 && smr < 3;
                        case 1 -> smr != null && smr >= 1 && smr < 2;
                        default -> false;
                    };
                })
                .map(ls -> CriterionRecordDto.builder()
                        .id(ls.getId())
                        .title(ls.getLanguage() + " — СМР-" + ls.getSmrLevel())
                        .subtitle(Stream_of(ls.getCertificateOrganization(), fmtDate(ls.getCertificateDate())))
                        .entityType("LANGUAGE_SKILL")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> openLessonRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return openLessonRepository.findByTeacherIdOrderByDateDesc(teacher.getId()).stream()
                .filter(l -> l.getDate() != null && inPeriod(l.getDate(), from, to))
                .map(l -> CriterionRecordDto.builder()
                        .id(l.getId())
                        .title(l.getTopic())
                        .subtitle(Stream_of(l.getHostDepartment(), fmtDate(l.getDate())))
                        .entityType("OPEN_LESSON")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> experimentRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return experimentRepository.findByTeacherIdOrderByDateDesc(teacher.getId()).stream()
                .filter(e -> e.getDate() != null && inPeriod(e.getDate(), from, to))
                .map(e -> CriterionRecordDto.builder()
                        .id(e.getId())
                        .title(e.getTitle())
                        .subtitle(fmtDate(e.getDate()))
                        .entityType("EXPERIMENT")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> mobilityRecords(Teacher teacher, LocalDate from, LocalDate to) {
        return mobilityRepository.findByTeacherIdOrderByDateFromDesc(teacher.getId()).stream()
                .filter(m -> overlaps(m.getDateFrom(), m.getDateTo(), from, to))
                .map(m -> CriterionRecordDto.builder()
                        .id(m.getId())
                        .title(m.getProgramName())
                        .subtitle(Stream_of(m.getInstitution(), m.getCountry(), datePeriod(m.getDateFrom(), m.getDateTo())))
                        .entityType("MOBILITY")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> workingGroupRecords(Teacher teacher, LocalDate from, LocalDate to, WorkingGroupRole role) {
        return workingGroupRepository.findByTeacherId(teacher.getId()).stream()
                .filter(g -> g.getOrderDate() != null && inPeriod(g.getOrderDate(), from, to))
                .filter(g -> g.getRole() == role)
                .map(g -> CriterionRecordDto.builder()
                        .id(g.getId())
                        .title(g.getProgram() != null ? g.getProgram().getName() : "—")
                        .subtitle(Stream_of(g.getOrderNumber(), fmtDate(g.getOrderDate())))
                        .entityType("WORKING_GROUP")
                        .build())
                .collect(Collectors.toList());
    }

    private List<CriterionRecordDto> militaryEducationRecords(Teacher teacher, LocalDate from, LocalDate to, RatingCriterion criterion) {
        List<CriterionRecordDto> result = new ArrayList<>();
        if (teacher.getMilitaryEducationLevel() != null && teacher.getMilitaryEducationDiplomaDate() != null
                && inPeriod(teacher.getMilitaryEducationDiplomaDate(), from, to)) {
            boolean match = (criterion == RatingCriterion.MILITARY_ED_STRATEGIC && teacher.getMilitaryEducationLevel() == MilitaryEducationLevel.STRATEGIC)
                    || (criterion == RatingCriterion.MILITARY_ED_OPERATIONAL && teacher.getMilitaryEducationLevel() == MilitaryEducationLevel.OPERATIONAL);
            if (match) {
                result.add(CriterionRecordDto.builder()
                        .id(teacher.getId())
                        .title("Рівень ВО: " + teacher.getMilitaryEducationLevel().name())
                        .subtitle("Диплом: " + fmtDate(teacher.getMilitaryEducationDiplomaDate()))
                        .entityType("TEACHER")
                        .build());
            }
        }
        return result;
    }

    // ══════════════════════════════════════════════
    //  Helpers (mirrors RatingCalculationService)
    // ══════════════════════════════════════════════

    private boolean inPeriod(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private boolean inPeriodByYear(Integer year, LocalDate from, LocalDate to) {
        if (year == null) return false;
        return year >= from.getYear() && year <= to.getYear();
    }

    /** Чи публікація потрапляє в період (за повною датою). */
    private boolean inPeriodPublication(ua.edu.teacherlicence.publication.model.Publication p, LocalDate from, LocalDate to) {
        LocalDate d = p.effectiveDate();
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    private boolean overlaps(LocalDate dateFrom, LocalDate dateTo, LocalDate from, LocalDate to) {
        if (dateFrom == null && dateTo == null) return false;
        LocalDate start = dateFrom != null ? dateFrom : dateTo;
        LocalDate end = dateTo != null ? dateTo : dateFrom;
        return !start.isAfter(to) && !end.isBefore(from);
    }

    private boolean academicYearOverlaps(String academicYear, LocalDate from, LocalDate to) {
        if (academicYear == null) return false;
        try {
            String[] parts = academicYear.split("-");
            int startYear = Integer.parseInt(parts[0].trim());
            LocalDate acadStart = LocalDate.of(startYear, 9, 1);
            LocalDate acadEnd = LocalDate.of(startYear + 1, 6, 30);
            return !acadStart.isAfter(to) && !acadEnd.isBefore(from);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean parseCombatDatesOverlap(String dates, LocalDate from, LocalDate to) {
        if (dates == null || dates.isBlank()) return false;

        // Спочатку шукаємо повні діапазони: dd.MM.yyyy–dd.MM.yyyy
        Pattern rangePattern = Pattern.compile(
                "(\\d{2}\\.\\d{2}\\.\\d{4})\\s*[–—-]\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
        Matcher rangeMatcher = rangePattern.matcher(dates);
        boolean foundAnyRange = false;

        while (rangeMatcher.find()) {
            foundAnyRange = true;
            try {
                LocalDate rangeStart = LocalDate.parse(rangeMatcher.group(1), DATE_FMT);
                LocalDate rangeEnd = LocalDate.parse(rangeMatcher.group(2), DATE_FMT);
                if (!rangeEnd.isBefore(from) && !rangeStart.isAfter(to)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        if (foundAnyRange) return false;

        // Фолбек по роках
        Pattern yearPattern = Pattern.compile("(\\d{4})");
        Matcher matcher = yearPattern.matcher(dates);
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year >= from.getYear() && year <= to.getYear()) return true;
        }
        return false;
    }

    private String fmtDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : null;
    }

    private String datePeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) return null;
        if (from != null && to != null) return fmtDate(from) + " — " + fmtDate(to);
        return fmtDate(from != null ? from : to);
    }

    /** Join non-null, non-blank parts with " | " */
    private String Stream_of(String... parts) {
        return Arrays.stream(parts)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" | "));
    }
}
