package ua.edu.teacherlicence.achievement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.ai.service.QualificationMatchAiService;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.teacher.repository.EducationRepository;

import ua.edu.teacherlicence.achievement.dto.AchievementProgressDto;

import ua.edu.teacherlicence.publication.model.PublicationType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplianceService {

    private static final int REQUIRED_ACHIEVEMENT_TYPES = 4;
    private static final int COMPLIANCE_PERIOD_YEARS = 5;
    private static final int EXEMPT_EXPERIENCE_YEARS = 3;
    private static final double EXEMPT_PART_TIME_LOAD = 0.25;

    private final TeacherRepository teacherRepository;
    private final AchievementService achievementService;
    private final AchievementValidationService achievementValidationService;
    private final PublicationRepository publicationRepository;
    private final EducationRepository educationRepository;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository academicTitleRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;

    @Autowired(required = false)
    private QualificationMatchAiService qualificationMatchAiService;

    /**
     * Перевірка відповідності пункту 38 для одного викладача
     */
    public ComplianceReportDto checkCompliance(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Викладач не знайдений: " + teacherId));
        return checkCompliance(teacher);
    }

    /**
     * Перевірка відповідності для всіх викладачів
     */
    public List<ComplianceReportDto> checkComplianceAll() {
        return teacherRepository.findAll().stream()
                .map(this::checkCompliance)
                .collect(Collectors.toList());
    }

    /**
     * Перевірка відповідності для викладачів кафедри
     */
    public List<ComplianceReportDto> checkComplianceByDepartment(Long departmentId) {
        return teacherRepository.findByDepartmentId(departmentId).stream()
                .map(this::checkCompliance)
                .collect(Collectors.toList());
    }

    private ComplianceReportDto checkCompliance(Teacher teacher) {
        String fullName = teacher.getLastName() + " " + teacher.getFirstName()
                + (teacher.getPatronymic() != null ? " " + teacher.getPatronymic() : "");

        // 1. Перевірка винятків
        String exemption = checkExemptions(teacher);

        // 2. Кількість публікацій пп.1 (статті з фахових/Scopus/WoS)
        int pubCount = publicationRepository.countByTeacherIdAndTypeAndArticleCategoryIsNotNull(
                teacher.getId(), PublicationType.ARTICLE);

        // 2b. Кількість фахових статей за напрямком кафедри (fieldRelevant=true)
        int relevantPubCount = publicationRepository.countByTeacherIdAndTypeAndArticleCategoryIsNotNullAndFieldRelevant(
                teacher.getId(), PublicationType.ARTICLE, true);

        // 2c. Перевірка відповідності кваліфікації напряму кафедри
        boolean diplomaMatch = false;
        boolean degreeMatch = false;
        boolean titleMatch = false;
        if (teacher.getDepartment() != null) {
            var matchResult = checkDepartmentQualification(teacher);
            diplomaMatch = matchResult.diplomaMatches();
            degreeMatch = matchResult.degreeMatches();
            titleMatch = checkTitleQualificationForDepartment(teacher);
        }

        // 3. Зібрати досягнення за останні 5 років
        LocalDate cutoffDate = LocalDate.now().minusYears(COMPLIANCE_PERIOD_YEARS);
        List<Achievement> achievements = achievementService.findByTeacherId(teacher.getId())
                .stream()
                .filter(a -> a.getDateAchieved() == null || a.getDateAchieved().isAfter(cutoffDate))
                .toList();

        // 3. Підрахувати унікальні типи ТІЛЬКИ серед повністю виконаних досягнень
        //    (наприклад, пп.1 потребує 5 публікацій — 3/5 НЕ рахується)
        Set<String> fulfilledTypes = getFulfilledTypes(teacher.getId());
        List<String> uniqueTypes = achievements.stream()
                .map(Achievement::getAchievementType)
                .distinct()
                .filter(type -> fulfilledTypes.contains(type.name()))
                .map(AchievementType::name)
                .collect(Collectors.toList());

        int uniqueTypeCount = uniqueTypes.size();

        // 4. Визначити статус
        // Якщо викладач має виняток (стаж < 3 років, сумісник), але відповідає вимогам — показуємо COMPLIANT
        ComplianceStatus status;
        if (uniqueTypeCount >= REQUIRED_ACHIEVEMENT_TYPES) {
            status = ComplianceStatus.COMPLIANT;
        } else if (exemption != null) {
            // Не відповідає, але звільнений від вимог
            return ComplianceReportDto.builder()
                    .teacherId(teacher.getId())
                    .teacherName(fullName)
                    .status(ComplianceStatus.EXEMPT)
                    .exemptionReason(exemption)
                    .achievementCount(achievements.size())
                    .uniqueTypeCount(uniqueTypeCount)
                    .achievementTypes(uniqueTypes)
                    .publicationsCount(pubCount)
                    .diplomaMatchesDepartment(diplomaMatch)
                    .degreeMatchesDepartment(degreeMatch)
                    .qualificationMatchesDepartment(diplomaMatch || degreeMatch)
                    .titleMatchesDepartment(titleMatch)
                    .relevantPublicationsCount(relevantPubCount)
                    .position(teacherPositionService.getEffectivePosition(teacher))
                    .militaryRank(teacher.getMilitaryRank())
                    .employmentType(teacher.getEmploymentType())
                    .bootstrappedPosition(isPrimaryPositionBootstrapped(teacher))
                    .build();
        } else if (uniqueTypeCount == REQUIRED_ACHIEVEMENT_TYPES - 1) {
            status = ComplianceStatus.WARNING;
        } else {
            status = ComplianceStatus.NON_COMPLIANT;
        }

        // 5. Сформувати рекомендації
        List<String> missingInfo = new ArrayList<>();
        if (status != ComplianceStatus.COMPLIANT) {
            int needed = REQUIRED_ACHIEVEMENT_TYPES - uniqueTypeCount;
            missingInfo.add("Потрібно ще " + needed + " тип(и) досягнень для відповідності п.38");
        }

        return ComplianceReportDto.builder()
                .teacherId(teacher.getId())
                .teacherName(fullName)
                .status(status)
                .achievementCount(achievements.size())
                .uniqueTypeCount(uniqueTypeCount)
                .achievementTypes(uniqueTypes)
                .missingInfo(missingInfo)
                .publicationsCount(pubCount)
                .diplomaMatchesDepartment(diplomaMatch)
                .degreeMatchesDepartment(degreeMatch)
                .qualificationMatchesDepartment(diplomaMatch || degreeMatch)
                .titleMatchesDepartment(titleMatch)
                .relevantPublicationsCount(relevantPubCount)
                .position(teacherPositionService.getEffectivePosition(teacher))
                .militaryRank(teacher.getMilitaryRank())
                .employmentType(teacher.getEmploymentType())
                .bootstrappedPosition(isPrimaryPositionBootstrapped(teacher))
                .build();
    }

    /**
     * Чи primary штатна позиція цього викладача має прапорець bootstrapped=true.
     * Викликається лише для одного teacher'а, тож одиничний batch-запит у сервісі.
     */
    private boolean isPrimaryPositionBootstrapped(Teacher teacher) {
        if (teacher == null) return false;
        java.util.Map<Long, Boolean> flags =
                teacherPositionService.getBootstrappedPositionFlags(java.util.List.of(teacher));
        return Boolean.TRUE.equals(flags.get(teacher.getId()));
    }

    /**
     * Перевірка відповідності кваліфікації викладача напряму діяльності кафедри.
     *
     * Перебирає {@link Education} ✕ {@link AcademicDegree} — якщо хоча б одна
     * комбінація диплом/ступінь відповідає кафедрі, повертає true.
     *
     * Раніше передавався один academicDegree (flat-поле Teacher). Тепер у викладача
     * може бути кілька ступенів — перевіряємо КОЖЕН.
     */
    private QualificationMatchAiService.MatchResult checkDepartmentQualification(Teacher teacher) {
        String departmentName = teacher.getDepartment().getName();
        Long departmentId = teacher.getDepartment().getId();
        boolean a1 = false, a2 = false;

        if (qualificationMatchAiService != null) {
            var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
            var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());

            // Якщо у викладача немає жодного ступеня — A2 неможливо задовольнити.
            if (degrees.isEmpty()) {
                return new QualificationMatchAiService.MatchResult(
                        diplomaMatchFromEducations(educations, teacher), false);
            }

            List<DegreeView> degreeViews = degrees.stream()
                    .map(d -> new DegreeView(d.getId(),
                            d.getDegree(),
                            d.getDissertationTopic(),
                            d.getSpeciality()))
                    .toList();

            outer:
            if (!educations.isEmpty()) {
                for (var edu : educations) {
                    for (DegreeView dv : degreeViews) {
                        var result = qualificationMatchAiService.checkDepartmentMatch(
                                teacher.getId(),
                                cacheKey(departmentId, edu.getId(), dv.id()),
                                edu.getSpeciality(),
                                dv.degree(),
                                dv.topic(),
                                dv.speciality(),
                                departmentName
                        );
                        if (result.diplomaMatches()) a1 = true;
                        if (result.degreeMatches()) a2 = true;
                        if (a1 && a2) break outer;
                    }
                }
            } else {
                for (DegreeView dv : degreeViews) {
                    var result = qualificationMatchAiService.checkDepartmentMatch(
                            teacher.getId(), cacheKey(departmentId, 0L, dv.id()),
                            teacher.getUniversitySpeciality(),
                            dv.degree(),
                            dv.topic(),
                            dv.speciality(),
                            departmentName
                    );
                    if (result.diplomaMatches()) a1 = true;
                    if (result.degreeMatches()) a2 = true;
                    if (a1 && a2) break;
                }
            }
        } else {
            // Fallback без AI — перевіряємо наявність даних
            var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
            a1 = diplomaMatchFromEducations(educations, teacher);
            a2 = !academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId()).isEmpty();
        }

        return new QualificationMatchAiService.MatchResult(a1, a2);
    }

    /** Чи є у викладача хоча б одна спеціальність освіти (для fallback без AI). */
    private boolean diplomaMatchFromEducations(
            List<ua.edu.teacherlicence.teacher.model.Education> educations, Teacher teacher) {
        boolean a1 = educations.stream().anyMatch(e -> e.getSpeciality() != null && !e.getSpeciality().isBlank());
        if (!a1) {
            a1 = teacher.getUniversitySpeciality() != null && !teacher.getUniversitySpeciality().isBlank();
        }
        return a1;
    }

    private static long cacheKey(Long deptId, Long eduId, Long degreeId) {
        long d = deptId == null ? 0 : deptId;
        long e = eduId == null ? 0 : eduId;
        long g = degreeId == null ? 0 : degreeId;
        return d * 1_000_000L + e * 1000L + g;
    }

    /** Compact внутрішній record для перебору ступенів з flat-fallback. */
    private record DegreeView(Long id, String degree, String topic, String speciality) {}

    /**
     * Перевірка чи бодай одне вчене звання викладача відповідає напряму кафедри.
     * Питання до AI: «Доцент кафедри ... відповідає кафедрі X?».
     *
     * Закон про вищу освіту вимагає ≥3 особи зі ступенем АБО званням за напрямом —
     * тому це поле зберігається окремо від degreeMatchesDepartment.
     */
    private boolean checkTitleQualificationForDepartment(Teacher teacher) {
        if (qualificationMatchAiService == null) return false;
        if (teacher.getDepartment() == null) return false;
        String departmentName = teacher.getDepartment().getName();
        Long departmentId = teacher.getDepartment().getId();

        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacher.getId());
        List<String> titleNames = titles.stream()
                .map(t -> t.getTitleName())
                .filter(s -> s != null && !s.isBlank())
                .toList();

        for (String name : titleNames) {
            boolean matches = qualificationMatchAiService.checkTitleMatch(
                    teacher.getId(),
                    departmentId,
                    QualificationMatchAiService.TitleTargetKind.DEPARTMENT,
                    name,
                    departmentName
            );
            if (matches) return true;
        }
        return false;
    }

    /**
     * Визначає які типи досягнень повністю виконані (currentCount >= requiredCount).
     * Використовує детерміністичну перевірку без виклику AI.
     */
    private Set<String> getFulfilledTypes(Long teacherId) {
        try {
            List<AchievementProgressDto> progress =
                    achievementValidationService.getProgressForTeacher(teacherId);
            return progress.stream()
                    .filter(AchievementProgressDto::isFulfilled)
                    .map(AchievementProgressDto::getAchievementType)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            // Fallback: якщо прогрес не вдалося отримати — вважаємо всі виконаними
            // (щоб не зламати існуючу поведінку)
            return achievementService.findByTeacherId(teacherId).stream()
                    .map(a -> a.getAchievementType().name())
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Перевірка винятків згідно пункту 38:
     * - стаж < 3 років
     * - сумісник <= 0.25 ставки або <= 150 годин
     *
     * Примітка: для військових ВНЗ статус УБД НЕ звільняє від вимог п.38.
     */
    private String checkExemptions(Teacher teacher) {
        // Стаж менше 3 років (обчислюємо від experienceStartDate)
        if (teacher.getExperienceStartDate() != null) {
            int years = java.time.Period.between(teacher.getExperienceStartDate(), java.time.LocalDate.now()).getYears();
            if (years < EXEMPT_EXPERIENCE_YEARS) {
                return "Стаж науково-педагогічної роботи менше " + EXEMPT_EXPERIENCE_YEARS + " років";
            }
        }

        // Сумісник з навантаженням <= 0.25
        if ("PART_TIME".equals(teacher.getEmploymentType())) {
            return "Працює на умовах сумісництва";
        }

        return null;
    }
}
