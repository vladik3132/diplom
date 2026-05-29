package ua.edu.teacherlicence.department.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;
import ua.edu.teacherlicence.compliance.model.DepartmentComplianceSummary;
import ua.edu.teacherlicence.compliance.service.ComplianceCacheService;
import ua.edu.teacherlicence.compliance.service.DepartmentSummaryService;
import ua.edu.teacherlicence.department.dto.DepartmentComplianceSummaryDto;
import ua.edu.teacherlicence.department.dto.Point37TeacherBrief;
import ua.edu.teacherlicence.department.dto.PublicationYearBucket;
import ua.edu.teacherlicence.department.dto.TeacherDegreeBrief;
import ua.edu.teacherlicence.department.dto.TeacherTitleBrief;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.rating.dto.DepartmentRatingSummaryDto;
import ua.edu.teacherlicence.rating.repository.RatingPeriodRepository;
import ua.edu.teacherlicence.rating.service.RatingService;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.AcademicTitle;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Читає зведену інформацію про compliance по кафедрам.
 *
 * Тепер працює на основі:
 *  - materialized view {@code department_compliance_summary} (агрегати п.35/п.38)
 *  - {@code teacher_compliance_cache} (для поля teacherReports коли треба)
 *
 * 1 SELECT замість 2400+ SQL, що були у попередній реалізації.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentComplianceService {

    private final DepartmentSummaryService departmentSummary;
    private final ComplianceCacheService complianceCache;
    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository academicTitleRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final ua.edu.teacherlicence.department.repository.StaffPositionRepository staffPositionRepository;
    private final PublicationRepository publicationRepository;
    private final RatingPeriodRepository ratingPeriodRepository;
    private final RatingService ratingService;
    private final ua.edu.teacherlicence.achievement.service.AchievementValidationService achievementValidationService;
    private final ua.edu.teacherlicence.ppdata.repository.ScientificSupervisionRepository scientificSupervisionRepository;
    private final ua.edu.teacherlicence.department.repository.DepartmentRepository departmentRepository;

    /** AI-сервіс для перевірки тематики наукового керівництва. Optional — у dev без AI null. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ua.edu.teacherlicence.ai.service.QualificationMatchAiService qualificationMatchAiService;

    /** Скільки років публікаційного графіка показуємо. */
    private static final int PUBLICATIONS_YEARS_WINDOW = 5;

    /** Закон про вищу освіту: ≥ 3 особи зі ступенем за спеціальністю кафедри. */
    private static final int DEFENDED_IN_SPECIALTY_REQUIRED = 3;

    /** Зведення для однієї кафедри з повним списком teacherReports (для drill-in сторінок). */
    public DepartmentComplianceSummaryDto getSummary(Long departmentId) {
        DepartmentComplianceSummary mv = departmentSummary.findById(departmentId).orElse(null);
        if (mv == null) return null;
        List<ComplianceReportDto> reports = complianceCache.getByDepartmentId(departmentId);
        return toDto(mv, reports);
    }

    /**
     * Для списку кафедр.
     * @param includeTeacherReports якщо true — додає teacherReports (важкий payload!).
     *                              Для списку сторінки зазвичай false, для drill-in — true.
     */
    public List<DepartmentComplianceSummaryDto> getAllSummaries(boolean includeTeacherReports) {
        return departmentSummary.findAll().stream()
                .map(mv -> toDto(mv, includeTeacherReports
                        ? complianceCache.getByDepartmentId(mv.getDepartmentId())
                        : List.of()))
                .toList();
    }

    /** За замовчуванням — без teacherReports (легкий payload для /departments). */
    public List<DepartmentComplianceSummaryDto> getAllSummaries() {
        return getAllSummaries(false);
    }

    private DepartmentComplianceSummaryDto toDto(DepartmentComplianceSummary mv, List<ComplianceReportDto> reports) {
        // reports може бути порожнім (для slim-payload списку кафедр).
        // У такому разі дочитаємо з кеша окремо — це 1 SELECT, не AI-виклик.
        List<ComplianceReportDto> source = !reports.isEmpty()
                ? reports
                : complianceCache.getByDepartmentId(mv.getDepartmentId());
        List<String> defended = computeDefendedInSpecialty(mv.getDepartmentId(), source);

        // Збагачуємо reports live-полями зі staff_position та Teacher entity:
        // position (effectivePosition), militaryRank, employmentType, bootstrappedPosition.
        // Cache не зберігає ці поля — вони можуть змінитись без refresh-у compliance.
        // Сортуємо за seniority (Начальник → Доцент → Викладач → ...).
        if (!reports.isEmpty()) {
            reports = enrichAndSortReports(mv.getDepartmentId(), reports);
        }

        // Розширена статистика — рахуємо "сирим способом" по teacher-таблиці кафедри.
        DegreeStats stats = computeDegreeStats(mv.getDepartmentId());
        // Деталізований breakdown по employmentType (для нового UI: 2 ряди карток).
        DetailedBreakdown detailed = computeDetailedBreakdown(mv.getDepartmentId(), source);
        // Військові / цивільні (по всіх викладачах кафедри, незалежно від employmentType).
        MilitaryBreakdown military = computeMilitaryBreakdown(mv.getDepartmentId());
        // Середній вік (за Teacher.dateOfBirth).
        Double avgAge = computeAverageAge(mv.getDepartmentId());
        // Вакантні штатні позиції.
        int vacant = (int) staffPositionRepository
                .findByDepartmentIdOrderByOrderNumber(mv.getDepartmentId()).stream()
                .filter(sp -> sp.getTeacher() == null)
                .count();
        // Місце в рейтингу активного періоду.
        DepartmentRatingInfo ratingInfo = computeRatingInfo(mv.getDepartmentId());
        // Публікації за останні 5 років (накопичувально по категоріях).
        List<PublicationYearBucket> publicationsByYear = computePublicationsByYear(mv.getDepartmentId());
        // Укомплектованість штатного розпису (за ставками).
        StaffingStats staffing = computeStaffing(mv.getDepartmentId());
        // Відповідність п.37 (виключно MAIN, А = А1||А2||А3||А4, п.37 = А && Б).
        Point37Stats point37 = computePoint37(mv.getDepartmentId(), source);

        return DepartmentComplianceSummaryDto.builder()
                .departmentId(mv.getDepartmentId())
                .departmentNumber(mv.getDepartmentNumber())
                .departmentName(mv.getDepartmentName())
                .facultyName(mv.getFacultyName())
                .totalTeachers(nvl(mv.getTotalTeachers()))
                .mainEmploymentTeachers(nvl(mv.getMainEmploymentTeachers()))
                .partTimeTeachers(nvl(mv.getPartTimeTeachers()))
                .withDegreeAndMainCount(nvl(mv.getWithDegreeAndMainCount()))
                .withDegreeAndMainPercent(nvld(mv.getWithDegreeAndMainPercent()))
                .point35Compliant(mv.isPoint35Compliant())
                .doctorsOrProfessorsCount(nvl(mv.getDoctorsOrProfessorsCount()))
                .doctorsOrProfessorsPercent(nvld(mv.getDoctorsOrProfessorsPercent()))
                .phdCount(stats.phd)
                .doctorOfScienceCount(stats.doctorOfScience)
                .docentOrSeniorResearcherCount(stats.docentOrSenior)
                .professorTitleCount(stats.professorTitle)
                .defendedInSpecialtyCount(defended.size())
                .defendedInSpecialtyRequirement(DEFENDED_IN_SPECIALTY_REQUIRED)
                .defendedInSpecialtyCompliant(defended.size() >= DEFENDED_IN_SPECIALTY_REQUIRED)
                .defendedInSpecialtyTeachers(defended)
                .point38Compliant(nvl(mv.getPoint38Compliant()))
                .point38Warning(nvl(mv.getPoint38Warning()))
                .point38NonCompliant(nvl(mv.getPoint38NonCompliant()))
                .point38Exempt(nvl(mv.getPoint38Exempt()))
                .overallStatus(mv.overallStatus())
                .teacherReports(reports)
                // ── Row 1: MAIN ──
                .mainAllTeachers(detailed.main.all)
                .mainTotalTeachers(detailed.main.all.size())
                .mainPhdTeachers(detailed.main.phd)
                .mainCandidateTeachers(detailed.main.candidate)
                .mainDoctorOfScienceTeachers(detailed.main.doctorOfScience)
                .mainDocentTeachers(detailed.main.docent)
                .mainSnsTeachers(detailed.main.sns)
                .mainSndTeachers(detailed.main.snd)
                .mainProfessorTitleTeachers(detailed.main.professorTitle)
                .mainPoint38CompliantCount(detailed.main.point38CompliantTeachers.size())
                .mainPoint38CompliantTeachers(detailed.main.point38CompliantTeachers)
                // ── Row 2: PART_TIME ──
                .partTimeAllTeachers(detailed.partTime.all)
                .partTimeTotalTeachers(detailed.partTime.all.size())
                .partTimePhdTeachers(detailed.partTime.phd)
                .partTimeCandidateTeachers(detailed.partTime.candidate)
                .partTimeDoctorOfScienceTeachers(detailed.partTime.doctorOfScience)
                .partTimeDocentTeachers(detailed.partTime.docent)
                .partTimeSnsTeachers(detailed.partTime.sns)
                .partTimeSndTeachers(detailed.partTime.snd)
                .partTimeProfessorTitleTeachers(detailed.partTime.professorTitle)
                .partTimePoint38CompliantCount(detailed.partTime.point38CompliantTeachers.size())
                .partTimePoint38CompliantTeachers(detailed.partTime.point38CompliantTeachers)
                // ── Row 3 ──
                .militaryCount(military.militaryTeachers.size())
                .militaryTeachers(military.militaryTeachers)
                .civilianCount(military.civilianTeachers.size())
                .civilianTeachers(military.civilianTeachers)
                .averageAgeYears(avgAge)
                .vacantPositionsCount(vacant)
                .departmentRatingRank(ratingInfo.rank)
                .departmentRatingTotalDepts(ratingInfo.totalDepts)
                .departmentRatingTotalScore(ratingInfo.totalScore)
                .publicationsByYear(publicationsByYear)
                .totalStaffRate(staffing.total)
                .occupiedStaffRate(staffing.occupied)
                .staffingPercent(staffing.percent)
                .point37CompliantCount(point37.compliantCount)
                .point37TeachersDetail(point37.teachers)
                .build();
    }

    /**
     * Збагачує reports полями position/militaryRank/employmentType/bootstrappedPosition
     * через batch-запит (один SQL по staff_positions замість N+1) і сортує:
     *
     * <ol>
     *   <li><b>Військові зверху, працівники ЗСУ знизу</b> — за полем
     *       {@code StaffPosition.militaryRankCategory}: значення "Працівник ЗСУ"
     *       або null = цивільний. Fallback на {@code Teacher.militaryRank} —
     *       якщо ШПК не заповнено (типово для bootstrapped записів),
     *       але людина має військове звання — рахуємо її військовою.</li>
     *   <li><b>Усередині групи — за seniority посади</b>
     *       (Начальник кафедри → Доцент → Викладач → ...).</li>
     *   <li>При рівних — за ПІБ.</li>
     * </ol>
     *
     * <p>Вхідний список може бути immutable (з {@code Stream.toList()} у кеш-сервісі).
     * Тому копіюємо у нову ArrayList перед sort.
     */
    private List<ComplianceReportDto> enrichAndSortReports(Long departmentId, List<ComplianceReportDto> reports) {
        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId);
        Map<Long, Teacher> byId = teachers.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(Teacher::getId, t -> t, (a, b) -> a));

        Map<Long, ua.edu.teacherlicence.department.model.StaffPosition> primaryByTeacher =
                teacherPositionService.getPrimaryStaffPositions(teachers);

        // Mutable копія — щоб sort працював, навіть якщо викликач передав immutable list.
        List<ComplianceReportDto> mutable = new java.util.ArrayList<>(reports);
        // teacherId → isCivilian (для sort нижче)
        Map<Long, Boolean> civilianMap = new HashMap<>();
        for (ComplianceReportDto r : mutable) {
            Teacher t = byId.get(r.getTeacherId());
            if (t == null) continue;
            var primary = primaryByTeacher.get(t.getId());
            String ep = primary != null ? primary.getPositionTitle() : null;
            // staff_positions — єдине джерело правди. null якщо немає primary.
            r.setPosition(ep != null && !ep.isBlank() ? ep : null);
            r.setMilitaryRank(t.getMilitaryRank());
            r.setEmploymentType(t.getEmploymentType());
            r.setBootstrappedPosition(primary != null && Boolean.TRUE.equals(primary.getBootstrapped()));
            civilianMap.put(t.getId(), isCivilian(primary, t));
        }

        // Спершу військові (isCivilian=false), потім цивільні (true).
        // Усередині — за seniority посади, при рівних — за ПІБ.
        mutable.sort(java.util.Comparator
                .comparing((ComplianceReportDto r) -> civilianMap.getOrDefault(r.getTeacherId(), true))
                .thenComparingInt(r ->
                        ua.edu.teacherlicence.common.PositionSeniority.rankOf(r.getPosition()))
                .thenComparing(ComplianceReportDto::getTeacherName,
                        java.util.Comparator.nullsLast(String::compareTo)));
        return mutable;
    }

    /**
     * Чи викладач "цивільний" (Працівник ЗСУ) для сортування.
     * <ul>
     *   <li>Якщо primary ШПК = "Працівник ЗСУ" → цивільний.</li>
     *   <li>Якщо primary ШПК заповнений іншим значенням (наприклад "підполковник") → військовий.</li>
     *   <li>Якщо ШПК не заповнений (типово для bootstrapped) — fallback:
     *       викладач з {@code Teacher.militaryRank} вважається військовим,
     *       без звання — цивільним.</li>
     * </ul>
     */
    private boolean isCivilian(ua.edu.teacherlicence.department.model.StaffPosition primary, Teacher t) {
        String shpk = primary != null ? primary.getMilitaryRankCategory() : null;
        if (shpk != null && !shpk.isBlank()) {
            return "Працівник ЗСУ".equalsIgnoreCase(shpk.trim());
        }
        // Fallback на звання людини (для bootstrapped позицій з ШПК=null)
        String rank = t != null ? t.getMilitaryRank() : null;
        return rank == null || rank.isBlank();
    }

    /**
     * Закон про вищу освіту вимагає ≥3 викладачів кафедри:
     *   - з науковим СТУПЕНЕМ за напрямом кафедри, АБО
     *   - з вченим ЗВАННЯМ за напрямом кафедри.
     *
     * Обидві перевірки виконуються AI-сервісом {@link ua.edu.teacherlicence.ai.service.QualificationMatchAiService}
     * і кешуються в {@code teacher_compliance_cache} (поля {@code degreeMatchesDepartment}
     * та {@code titleMatchesDepartment}). Тут ми лише читаємо кеш — без додаткових AI-викликів.
     *
     * Викладач зараховується якщо:
     *   1. (degreeMatchesDepartment = true) AND має хоч один науковий ступінь
     *      → "ступінь відповідає"
     *   2. ABO (titleMatchesDepartment = true) AND має хоч одне вчене звання
     *      → "звання відповідає"
     */
    private List<String> computeDefendedInSpecialty(Long departmentId, List<ComplianceReportDto> reports) {
        if (departmentId == null || reports == null || reports.isEmpty()) return List.of();

        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId);
        Map<Long, Teacher> byId = teachers.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(Teacher::getId, t -> t, (a, b) -> a));

        return reports.stream()
                .filter(r -> r.isDegreeMatchesDepartment() || r.isTitleMatchesDepartment())
                .map(r -> {
                    Teacher t = byId.get(r.getTeacherId());
                    if (t == null) return null;
                    // Маємо мати хоч щось у відповідній категорії, щоб вимога Закону була покрита.
                    boolean ok = (r.isDegreeMatchesDepartment() && hasDegree(t))
                            || (r.isTitleMatchesDepartment() && hasTitle(t));
                    return ok ? t : null;
                })
                .filter(Objects::nonNull)
                .map(this::fullName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean hasTitle(Teacher t) {
        return !academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId()).isEmpty();
    }

    private boolean hasDegree(Teacher t) {
        return !academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).isEmpty();
    }

    /** Підсумкові лічильники за ступенями і вченими званнями кафедри. */
    private record DegreeStats(int phd, int doctorOfScience, int docentOrSenior, int professorTitle) {}

    private DegreeStats computeDegreeStats(Long departmentId) {
        if (departmentId == null) return new DegreeStats(0, 0, 0, 0);
        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId);
        int phd = 0, dsc = 0, docent = 0, prof = 0;
        for (Teacher t : teachers) {
            var degreesList = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
            List<String> degreeStrings = degreesList.stream()
                    .map(d -> d.getDegree())
                    .filter(Objects::nonNull)
                    .toList();

            // Якщо хоч один з ступенів — Доктор наук, рахуємо у dsc;
            // якщо є Доктор філософії / кандидат — у phd.
            // Викладач може потрапити в обидва (якщо має і PhD, і DSc).
            boolean hasDsc = false, hasPhd = false;
            for (String degree : degreeStrings) {
                String d = lower(degree);
                if (d == null) continue;
                if (containsAny(d, "доктор наук", "д.т.н", "д.в.н", "д.е.н", "д.ф.-м.н",
                        "д.ю.н", "д.і.н", "д.ф.н", "д.с.-г.н", "д.б.н", "д.мед.н", "д.психол.н",
                        "доктор технічних", "доктор військових", "доктор економічних", "доктор політичних",
                        "доктор фізико-математичних", "доктор юридичних", "доктор історичних", "доктор філософських",
                        "доктор філологічних", "доктор медичних", "доктор біологічних", "доктор педагогічних",
                        "доктор сільськогосподарських", "доктор психологічних")) {
                    hasDsc = true;
                } else if (containsAny(d, "доктор філософії", "phd",
                        "кандидат", "к.т.н", "к.в.н", "к.е.н", "к.ф.-м.н", "к.ю.н",
                        "к.і.н", "к.ф.н", "к.с.-г.н", "к.б.н", "к.мед.н", "к.психол.н")) {
                    hasPhd = true;
                }
            }
            if (hasDsc) dsc++;
            if (hasPhd) phd++;

            var titlesList = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
            List<String> titleStrings = titlesList.stream()
                    .map(at -> at.getTitleName())
                    .filter(Objects::nonNull)
                    .toList();

            boolean hasProf = false, hasDocent = false;
            for (String tn : titleStrings) {
                String tl = lower(tn);
                if (tl == null) continue;
                if (containsAny(tl, "професор")) hasProf = true;
                if (containsAny(tl, "доцент",
                        "снс", "старший науковий співробітник",
                        "снд", "старший дослідник")) {
                    hasDocent = true;
                }
            }
            if (hasProf) prof++;
            if (hasDocent) docent++;
        }
        return new DegreeStats(phd, dsc, docent, prof);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private static String lower(String s) {
        if (s == null || s.isBlank()) return null;
        return s.toLowerCase().trim();
    }

    private String fullName(Teacher t) {
        StringBuilder sb = new StringBuilder();
        if (t.getLastName() != null) sb.append(t.getLastName());
        if (t.getFirstName() != null) sb.append(' ').append(t.getFirstName());
        if (t.getPatronymic() != null) sb.append(' ').append(t.getPatronymic());
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private int nvl(Integer i) { return i != null ? i : 0; }
    private double nvld(Double d) { return d != null ? d : 0.0; }

    // ═══════════════════════════════════════════════════════════════
    //  ДЕТАЛІЗОВАНИЙ BREAKDOWN — для нових карток на сторінці кафедри
    // ═══════════════════════════════════════════════════════════════

    /** Контейнер списків Brief по одному employmentType. */
    private static class EmploymentGroupBreakdown {
        final List<TeacherDegreeBrief> all = new ArrayList<>();
        final List<TeacherDegreeBrief> phd = new ArrayList<>();
        final List<TeacherDegreeBrief> candidate = new ArrayList<>();
        final List<TeacherDegreeBrief> doctorOfScience = new ArrayList<>();
        final List<TeacherTitleBrief> docent = new ArrayList<>();
        final List<TeacherTitleBrief> sns = new ArrayList<>();
        final List<TeacherTitleBrief> snd = new ArrayList<>();
        final List<TeacherTitleBrief> professorTitle = new ArrayList<>();
        final List<String> point38CompliantTeachers = new ArrayList<>();
    }

    /** Контейнер двох груп: MAIN і PART_TIME. */
    private static class DetailedBreakdown {
        final EmploymentGroupBreakdown main = new EmploymentGroupBreakdown();
        final EmploymentGroupBreakdown partTime = new EmploymentGroupBreakdown();
    }

    /**
     * Розраховує деталізований breakdown для нових карток сторінки кафедри:
     * паралельно по MAIN і PART_TIME, з розділенням ступенів на
     * PhD/Candidate/DSc і звань на Docent/SNS/SND/Professor.
     *
     * <p>{@code reports} — використовується щоб знайти ПІБ викладачів,
     * що відповідають п.38 (status=COMPLIANT).
     */
    private DetailedBreakdown computeDetailedBreakdown(Long departmentId, List<ComplianceReportDto> reports) {
        DetailedBreakdown result = new DetailedBreakdown();
        if (departmentId == null) return result;

        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId);
        // teacherId → COMPLIANT? (для виявлення хто з MAIN/PART_TIME відповідає)
        Map<Long, Boolean> compliantMap = new HashMap<>();
        if (reports != null) {
            for (ComplianceReportDto r : reports) {
                if (r.getTeacherId() != null) {
                    compliantMap.put(r.getTeacherId(),
                            r.getStatus() == ComplianceReportDto.ComplianceStatus.COMPLIANT);
                }
            }
        }

        for (Teacher t : teachers) {
            boolean isMain = "MAIN".equals(t.getEmploymentType());
            EmploymentGroupBreakdown group = isMain ? result.main : result.partTime;

            String shortName = shortName(t);
            String fullDegreeListAsLabel = primaryDegreeLabel(t);
            group.all.add(TeacherDegreeBrief.builder()
                    .teacherId(t.getId())
                    .fullName(shortName)
                    .degreeName(fullDegreeListAsLabel)
                    .speciality(primarySpeciality(t))
                    .build());

            // Класифікація ступенів — один викладач може потрапити в кілька списків
            // (якщо має і PhD, і DSc). Для tooltip-ів це OK — кожна картка покаже своїх.
            List<AcademicDegree> degrees = academicDegreeRepository
                    .findByTeacherIdOrderByDiplomaDateAsc(t.getId());
            boolean hasDsc = false, hasPhd = false, hasCandidate = false;
            for (AcademicDegree d : degrees) {
                String lower = lower(d.getDegree());
                if (lower == null) continue;
                if (containsAny(lower, "доктор наук", "д.т.н", "д.в.н", "д.е.н", "д.ф.-м.н",
                        "д.ю.н", "д.і.н", "д.ф.н", "д.с.-г.н", "д.б.н", "д.мед.н", "д.психол.н",
                        "доктор технічних", "доктор військових", "доктор економічних", "доктор політичних",
                        "доктор фізико-математичних", "доктор юридичних", "доктор історичних",
                        "доктор філософських", "доктор філологічних", "доктор медичних",
                        "доктор біологічних", "доктор педагогічних",
                        "доктор сільськогосподарських", "доктор психологічних")) {
                    hasDsc = true;
                } else if (containsAny(lower, "доктор філософії", "phd")) {
                    hasPhd = true;
                } else if (containsAny(lower, "кандидат", "к.т.н", "к.в.н", "к.е.н", "к.ф.-м.н",
                        "к.ю.н", "к.і.н", "к.ф.н", "к.с.-г.н", "к.б.н", "к.мед.н", "к.психол.н")) {
                    hasCandidate = true;
                }
            }
            if (hasDsc) addDegreeBrief(group.doctorOfScience, t, degrees, this::isDoctorOfScience);
            if (hasPhd) addDegreeBrief(group.phd, t, degrees, this::isPhd);
            if (hasCandidate) addDegreeBrief(group.candidate, t, degrees, this::isCandidate);

            // Класифікація звань
            List<AcademicTitle> titles = academicTitleRepository
                    .findByTeacherIdOrderByAttestatDateAsc(t.getId());
            boolean hasProf = false, hasDocent = false, hasSns = false, hasSnd = false;
            for (AcademicTitle at : titles) {
                String lower = lower(at.getTitleName());
                if (lower == null) continue;
                if (containsAny(lower, "професор")) hasProf = true;
                if (containsAny(lower, "доцент")) hasDocent = true;
                if (containsAny(lower, "снс", "старший науковий співробітник")) hasSns = true;
                if (containsAny(lower, "снд", "старший дослідник")) hasSnd = true;
            }
            if (hasProf) addTitleBrief(group.professorTitle, t, titles, "професор");
            if (hasDocent) addTitleBrief(group.docent, t, titles, "доцент");
            if (hasSns) addTitleBrief(group.sns, t, titles, "снс", "старший науковий співробітник");
            if (hasSnd) addTitleBrief(group.snd, t, titles, "снд", "старший дослідник");

            if (Boolean.TRUE.equals(compliantMap.get(t.getId()))) {
                group.point38CompliantTeachers.add(shortName);
            }
        }
        return result;
    }

    private boolean isDoctorOfScience(AcademicDegree d) {
        String lower = lower(d.getDegree());
        if (lower == null) return false;
        return containsAny(lower, "доктор наук", "д.т.н", "д.в.н", "д.е.н", "д.ф.-м.н",
                "д.ю.н", "д.і.н", "д.ф.н", "д.с.-г.н", "д.б.н", "д.мед.н", "д.психол.н",
                "доктор технічних", "доктор військових", "доктор економічних", "доктор політичних",
                "доктор фізико-математичних", "доктор юридичних", "доктор історичних",
                "доктор філософських", "доктор філологічних", "доктор медичних",
                "доктор біологічних", "доктор педагогічних",
                "доктор сільськогосподарських", "доктор психологічних");
    }

    private boolean isPhd(AcademicDegree d) {
        String lower = lower(d.getDegree());
        if (lower == null) return false;
        return containsAny(lower, "доктор філософії", "phd");
    }

    private boolean isCandidate(AcademicDegree d) {
        String lower = lower(d.getDegree());
        if (lower == null) return false;
        return containsAny(lower, "кандидат", "к.т.н", "к.в.н", "к.е.н", "к.ф.-м.н",
                "к.ю.н", "к.і.н", "к.ф.н", "к.с.-г.н", "к.б.н", "к.мед.н", "к.психол.н");
    }

    private void addDegreeBrief(List<TeacherDegreeBrief> bucket, Teacher t,
                                 List<AcademicDegree> degrees,
                                 java.util.function.Predicate<AcademicDegree> matches) {
        AcademicDegree matched = degrees.stream().filter(matches).findFirst().orElse(null);
        if (matched == null) return;
        bucket.add(TeacherDegreeBrief.builder()
                .teacherId(t.getId())
                .fullName(shortName(t))
                .degreeName(matched.getDegree())
                .speciality(matched.getSpeciality())
                .build());
    }

    private void addTitleBrief(List<TeacherTitleBrief> bucket, Teacher t,
                                List<AcademicTitle> titles, String... matchKeywords) {
        AcademicTitle matched = titles.stream()
                .filter(at -> {
                    String lower = lower(at.getTitleName());
                    if (lower == null) return false;
                    for (String kw : matchKeywords) if (lower.contains(kw)) return true;
                    return false;
                })
                .findFirst().orElse(null);
        if (matched == null) return;
        bucket.add(TeacherTitleBrief.builder()
                .teacherId(t.getId())
                .fullName(shortName(t))
                .titleName(matched.getTitleName())
                .build());
    }

    /** Скорочений ПІБ — "Прізвище І.П." */
    private String shortName(Teacher t) {
        if (t == null) return "—";
        StringBuilder sb = new StringBuilder();
        if (t.getLastName() != null) sb.append(t.getLastName());
        if (t.getFirstName() != null && !t.getFirstName().isEmpty()) {
            sb.append(' ').append(t.getFirstName().charAt(0)).append('.');
        }
        if (t.getPatronymic() != null && !t.getPatronymic().isEmpty()) {
            sb.append(t.getPatronymic().charAt(0)).append('.');
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    /** Назва primary (першого за рангом) ступеня, для tooltip "Всього". */
    private String primaryDegreeLabel(Teacher t) {
        var list = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        return list.stream().findFirst().map(AcademicDegree::getDegree).orElse(null);
    }

    private String primarySpeciality(Teacher t) {
        var list = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        return list.stream().findFirst().map(AcademicDegree::getSpeciality).orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Військові vs Цивільні (Працівники ЗСУ)
    // ═══════════════════════════════════════════════════════════════

    private static class MilitaryBreakdown {
        final List<String> militaryTeachers = new ArrayList<>();
        final List<String> civilianTeachers = new ArrayList<>();
    }

    /**
     * Визначає військових і цивільних серед усіх викладачів кафедри
     * (за такою ж логікою як sort на цій сторінці): primary StaffPosition.militaryRankCategory,
     * fallback на Teacher.militaryRank.
     */
    private MilitaryBreakdown computeMilitaryBreakdown(Long departmentId) {
        MilitaryBreakdown result = new MilitaryBreakdown();
        if (departmentId == null) return result;
        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId);
        Map<Long, ua.edu.teacherlicence.department.model.StaffPosition> primaryByTeacher =
                teacherPositionService.getPrimaryStaffPositions(teachers);

        for (Teacher t : teachers) {
            var primary = primaryByTeacher.get(t.getId());
            String shpk = primary != null ? primary.getMilitaryRankCategory() : null;
            boolean civilian;
            if (shpk != null && !shpk.isBlank()) {
                civilian = "Працівник ЗСУ".equalsIgnoreCase(shpk.trim());
            } else {
                // ШПК не заповнено → fallback на Teacher.militaryRank
                String rank = t.getMilitaryRank();
                civilian = rank == null || rank.isBlank();
            }
            String name = shortName(t);
            if (civilian) result.civilianTeachers.add(name);
            else result.militaryTeachers.add(name);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Середній вік
    // ═══════════════════════════════════════════════════════════════

    private Double computeAverageAge(Long departmentId) {
        if (departmentId == null) return null;
        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId);
        LocalDate today = LocalDate.now();
        double sum = 0;
        int count = 0;
        for (Teacher t : teachers) {
            LocalDate dob = t.getDateOfBirth();
            if (dob == null) continue;
            int years = Period.between(dob, today).getYears();
            if (years < 16 || years > 100) continue; // sanity check
            sum += years;
            count++;
        }
        if (count == 0) return null;
        return Math.round((sum / count) * 10.0) / 10.0;   // 1 десятковий знак
    }

    // ═══════════════════════════════════════════════════════════════
    //  Місце в рейтингу (активний період)
    // ═══════════════════════════════════════════════════════════════

    private static class DepartmentRatingInfo {
        Integer rank;
        Integer totalDepts;
        Integer totalScore;
    }

    private DepartmentRatingInfo computeRatingInfo(Long departmentId) {
        DepartmentRatingInfo info = new DepartmentRatingInfo();
        try {
            var active = ratingPeriodRepository.findByActiveTrue();
            if (active.isEmpty()) return info;
            List<DepartmentRatingSummaryDto> rankings =
                    ratingService.getDepartmentRankings(active.get().getId());
            info.totalDepts = rankings.size();
            for (DepartmentRatingSummaryDto d : rankings) {
                if (Objects.equals(d.getDepartmentId(), departmentId)) {
                    info.rank = d.getRank();
                    info.totalScore = d.getTotalScore();
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to compute rating info for department {}: {}", departmentId, e.getMessage());
        }
        return info;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Публікації за 5 років (накопичувально по категоріях)
    // ═══════════════════════════════════════════════════════════════

    private List<PublicationYearBucket> computePublicationsByYear(Long departmentId) {
        List<PublicationYearBucket> result = new ArrayList<>();
        if (departmentId == null) return result;
        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId);
        if (teachers.isEmpty()) return preFillEmptyYears();
        List<Long> teacherIds = teachers.stream()
                .map(Teacher::getId).filter(Objects::nonNull).toList();
        if (teacherIds.isEmpty()) return preFillEmptyYears();

        int currentYear = LocalDate.now().getYear();
        int fromYear = currentYear - (PUBLICATIONS_YEARS_WINDOW - 1);

        // Підготовка buckets — заповнюємо порожніми, щоб були всі роки 2021..2025 навіть якщо у когось 0.
        Map<Integer, int[]> byYear = new LinkedHashMap<>(); // year → [scopus, wos, catA, catB]
        for (int y = fromYear; y <= currentYear; y++) {
            byYear.put(y, new int[4]);
        }

        List<Publication> pubs = publicationRepository.findByTeacherIdIn(teacherIds);
        for (Publication p : pubs) {
            if (p.getType() != PublicationType.ARTICLE) continue;
            ArticleCategory cat = p.getArticleCategory();
            if (cat == null) continue;
            // ОБЕРЕЖНО: ternary з int + Integer робить unboxing — NPE якщо p.getYear()==null.
            // Тому використовуємо явні Integer.valueOf / if-else.
            LocalDate d = p.effectiveDate();
            Integer year;
            if (d != null) {
                year = d.getYear();
            } else {
                year = p.getYear();
            }
            if (year == null) continue;
            int[] bucket = byYear.get(year);
            if (bucket == null) continue; // поза вікном
            switch (cat) {
                case SCOPUS -> bucket[0]++;
                case WOS -> bucket[1]++;
                case CATEGORY_A -> bucket[2]++;
                case CATEGORY_B -> bucket[3]++;
            }
        }
        for (var e : byYear.entrySet()) {
            int[] b = e.getValue();
            result.add(PublicationYearBucket.builder()
                    .year(e.getKey())
                    .scopus(b[0])
                    .wos(b[1])
                    .categoryA(b[2])
                    .categoryB(b[3])
                    .build());
        }
        return result;
    }

    private List<PublicationYearBucket> preFillEmptyYears() {
        List<PublicationYearBucket> result = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();
        int fromYear = currentYear - (PUBLICATIONS_YEARS_WINDOW - 1);
        for (int y = fromYear; y <= currentYear; y++) {
            result.add(PublicationYearBucket.builder()
                    .year(y).scopus(0).wos(0).categoryA(0).categoryB(0).build());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Укомплектованість штатного розпису (за ставками)
    // ═══════════════════════════════════════════════════════════════

    private static class StaffingStats {
        Double total;
        Double occupied;
        Double percent;
    }

    private StaffingStats computeStaffing(Long departmentId) {
        StaffingStats s = new StaffingStats();
        if (departmentId == null) return s;
        var positions = staffPositionRepository.findByDepartmentIdOrderByOrderNumber(departmentId);
        if (positions.isEmpty()) return s;
        double total = 0, occupied = 0;
        for (var sp : positions) {
            double rate = sp.getRate() != null ? sp.getRate() : 1.0;
            total += rate;
            if (sp.getTeacher() != null) occupied += rate;
        }
        s.total = Math.round(total * 100.0) / 100.0;
        s.occupied = Math.round(occupied * 100.0) / 100.0;
        s.percent = total > 0 ? Math.round((occupied / total * 100.0) * 10.0) / 10.0 : null;
        return s;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Відповідність п.37 (MAIN-викладачі кафедри)
    // ═══════════════════════════════════════════════════════════════

    private static class Point37Stats {
        int compliantCount;
        List<Point37TeacherBrief> teachers = new ArrayList<>();
    }

    /**
     * Рахує відповідність п.37 для всіх MAIN-викладачів кафедри.
     *
     * <p>Логіка:
     * <ul>
     *   <li>А1 = {@code diplomaMatchesDepartment} (кеш ComplianceReport)</li>
     *   <li>А2 = {@code degreeMatchesDepartment} (кеш ComplianceReport)</li>
     *   <li>А3 = пп.20 fulfilled (з {@link
     *       ua.edu.teacherlicence.achievement.service.AchievementValidationService#getProgressForTeacher})</li>
     *   <li>А4 = хоча б одне наукове керівництво ПРОФІЛЬНЕ для кафедри
     *       (AI-перевірка теми дисертації проти напряму кафедри)</li>
     *   <li>Б = пп.1 fulfilled (≥5 свіжих публікацій)</li>
     *   <li>п.37 = (А1 ∨ А2 ∨ А3 ∨ А4) ∧ Б</li>
     * </ul>
     */
    private Point37Stats computePoint37(Long departmentId, List<ComplianceReportDto> reports) {
        Point37Stats stats = new Point37Stats();
        if (departmentId == null) return stats;
        var deptOpt = departmentRepository.findById(departmentId);
        if (deptOpt.isEmpty()) return stats;
        String departmentName = deptOpt.get().getName();

        List<Teacher> teachers = teacherRepository.findByDepartmentId(departmentId).stream()
                .filter(t -> "MAIN".equals(t.getEmploymentType()))
                .toList();

        // teacherId → ComplianceReport (з кешу для A1/A2)
        Map<Long, ComplianceReportDto> reportsByTeacher = new HashMap<>();
        if (reports != null) {
            for (var r : reports) {
                if (r.getTeacherId() != null) reportsByTeacher.put(r.getTeacherId(), r);
            }
        }

        for (Teacher t : teachers) {
            ComplianceReportDto r = reportsByTeacher.get(t.getId());
            boolean a1 = r != null && r.isDiplomaMatchesDepartment();
            boolean a2 = r != null && r.isDegreeMatchesDepartment();

            // А3 і Б — з AchievementProgressDto
            boolean a3 = false, blockB = false;
            try {
                var progress = achievementValidationService.getProgressForTeacher(t.getId());
                for (var p : progress) {
                    if (p.getPpNumber() == 20 && p.isFulfilled()) a3 = true;
                    if (p.getPpNumber() == 1 && p.isFulfilled()) blockB = true;
                }
            } catch (Exception e) {
                log.warn("getProgressForTeacher failed for teacher {}: {}", t.getId(), e.getMessage());
            }

            // А4 — наукове керівництво ПРОФІЛЬНЕ для кафедри
            boolean a4 = hasSupervisionMatchingDepartment(t.getId(), departmentId, departmentName);

            boolean blockA = a1 || a2 || a3 || a4;
            boolean point37 = blockA && blockB;
            if (point37) stats.compliantCount++;

            stats.teachers.add(Point37TeacherBrief.builder()
                    .teacherId(t.getId())
                    .fullName(shortName(t))
                    .a1Diploma(a1)
                    .a2Degree(a2)
                    .a3Practical(a3)
                    .a4Supervision(a4)
                    .blockB(blockB)
                    .point37Compliant(point37)
                    .build());
        }
        return stats;
    }

    /**
     * Чи серед керівництв викладача (scientific_supervision) є хоча б одне
     * за тематикою, що відповідає напряму кафедри. AI-перевірка через
     * {@code checkDisciplineMatch} (передаємо назву кафедри як "discipline" і
     * тему дисертації як "topic").
     */
    private boolean hasSupervisionMatchingDepartment(Long teacherId, Long departmentId, String departmentName) {
        if (qualificationMatchAiService == null) return false;
        if (teacherId == null || departmentId == null || departmentName == null) return false;
        var supervisions = scientificSupervisionRepository.findByTeacherId(teacherId);
        if (supervisions.isEmpty()) return false;
        for (var s : supervisions) {
            if (s.getDefenseDate() == null) continue; // ще не захищений
            try {
                // Unique cache key per supervision×department.
                long cacheKey = ((long) ("supv-dept:" + s.getId() + ":" + departmentId).hashCode())
                        & 0xFFFFFFFFL;
                var result = qualificationMatchAiService.checkDisciplineMatch(
                        teacherId,
                        cacheKey,
                        null,
                        s.getDegreeType() != null ? s.getDegreeType().name() : null,
                        s.getTopic(),
                        null,
                        departmentName,     // disciplineName ← використовуємо назву кафедри як ціль
                        null,                // programSpec
                        null);               // programField
                if (result.degreeMatches()) return true;
            } catch (Exception e) {
                log.warn("AI supervision-department match failed for supervision {}: {}",
                        s.getId(), e.getMessage());
            }
        }
        return false;
    }
}
