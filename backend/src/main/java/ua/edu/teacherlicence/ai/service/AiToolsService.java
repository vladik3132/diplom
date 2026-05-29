package ua.edu.teacherlicence.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.service.AchievementService;
import ua.edu.teacherlicence.achievement.service.ComplianceService;
import ua.edu.teacherlicence.department.dto.DepartmentComplianceSummaryDto;
import ua.edu.teacherlicence.department.service.DepartmentComplianceService;
import ua.edu.teacherlicence.department.service.DepartmentService;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.service.PublicationService;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.service.QualificationService;
import ua.edu.teacherlicence.teacher.model.CareerRecord;
import ua.edu.teacherlicence.teacher.model.Education;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.MilitaryEducation;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.CareerRecordRepository;
import ua.edu.teacherlicence.teacher.repository.EducationRepository;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.MilitaryEducationRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.discipline.model.Discipline;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.DisciplineRepository;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.department.model.StaffPosition;
import ua.edu.teacherlicence.department.repository.StaffPositionRepository;
import ua.edu.teacherlicence.rating.model.RatingPeriod;
import ua.edu.teacherlicence.rating.model.TeacherRating;
import ua.edu.teacherlicence.rating.repository.RatingPeriodRepository;
import ua.edu.teacherlicence.rating.repository.TeacherRatingRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Набір інструментів (Tool Calling / Function Calling), які AI-асистент
 * може викликати автоматично для отримання точних даних з БД.
 *
 * Це головне покращення проти галюцинацій: замість дампу всіх даних у контекст,
 * AI сам вирішує який інструмент викликати і отримує ТОЧНІ дані.
 *
 * Кожен метод повертає JSON-рядок — компактно й добре парситься моделлю.
 * Лімітуємо розміри списків, щоб не переповнювати контекст.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
@Transactional(readOnly = true)
public class AiToolsService {

    private static final int MAX_LIST_SIZE = 30;
    private static final int MAX_SEARCH_RESULTS = 15;
    private static final int MAX_FIELD_LEN = 200;

    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository academicTitleRepository;
    private final ComplianceService complianceService;
    private final DepartmentComplianceService departmentComplianceService;
    private final DepartmentService departmentService;
    private final AchievementService achievementService;
    private final QualificationService qualificationService;
    private final PublicationService publicationService;
    private final LanguageSkillRepository languageSkillRepository;
    private final CareerRecordRepository careerRecordRepository;
    private final EducationRepository educationRepository;
    private final MilitaryEducationRepository militaryEducationRepository;
    private final TeacherDisciplineRepository teacherDisciplineRepository;
    private final DisciplineRepository disciplineRepository;
    private final EducationalProgramRepository educationalProgramRepository;
    private final StaffPositionRepository staffPositionRepository;
    private final TeacherRatingRepository teacherRatingRepository;
    private final RatingPeriodRepository ratingPeriodRepository;
    private final ObjectMapper objectMapper;
    /**
     * Необов'язкова залежність: у dev (H2 / без pgvector) цей bean відсутній, тому
     * використовуємо ObjectProvider для ліниво-опціонального резолвінгу.
     * Якщо відсутній — семантичний пошук @Tool повертає зрозумілу помилку.
     */
    private final ObjectProvider<AiEmbeddingIndexService> embeddingIndexProvider;

    // ═══════════════════════════════════════════════════════════════
    //  OVERVIEW / SEARCH TOOLS
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Отримати загальну статистику системи: кількість викладачів, кафедр,
            розподіл за статусом п.38 та статусом кафедр. Використовуй для
            загальних запитів типу "стан системи", "скільки викладачів", "огляд".
            Повертає JSON з полями totalTeachers, mainEmployment, partTime,
            compliance {compliant, warning, nonCompliant, exempt}, departments
            {total, good, warning, critical}.
            """)
    public String getOverallStats() {
        logTool("getOverallStats");
        try {
            List<Teacher> teachers = teacherRepository.findAll();
            List<ComplianceReportDto> reports = complianceService.checkComplianceAll();
            List<DepartmentComplianceSummaryDto> depts = departmentComplianceService.getAllSummaries();

            long main = teachers.stream().filter(t -> "MAIN".equals(t.getEmploymentType())).count();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalTeachers", teachers.size());
            result.put("mainEmployment", main);
            result.put("partTime", teachers.size() - main);

            Map<String, Long> compliance = new LinkedHashMap<>();
            compliance.put("compliant", count(reports, ComplianceStatus.COMPLIANT));
            compliance.put("warning", count(reports, ComplianceStatus.WARNING));
            compliance.put("nonCompliant", count(reports, ComplianceStatus.NON_COMPLIANT));
            compliance.put("exempt", count(reports, ComplianceStatus.EXEMPT));
            result.put("compliance", compliance);

            Map<String, Long> deptStats = new LinkedHashMap<>();
            deptStats.put("total", (long) depts.size());
            deptStats.put("good", depts.stream().filter(d -> "GOOD".equals(d.getOverallStatus())).count());
            deptStats.put("warning", depts.stream().filter(d -> "WARNING".equals(d.getOverallStatus())).count());
            deptStats.put("critical", depts.stream().filter(d -> "CRITICAL".equals(d.getOverallStatus())).count());
            result.put("departments", deptStats);

            return json(result);
        } catch (Exception e) {
            return toolError("getOverallStats", e);
        }
    }

    @Tool(description = """
            Знайти викладачів за прізвищем (частковий збіг, без урахування регістру).
            Використовуй коли користувач згадав прізвище, наприклад "Стоцький", "Романенко".
            Повертає JSON-список з полями:
              id, fullName, rank, position, department, employmentType, status, uniqueTypeCount,
              academicDegree (PRIMARY — найвищий за рангом ступінь),
              academicTitle (PRIMARY — найвище за рангом звання),
              academicDegrees (МАСИВ ВСІХ ступенів — викладач може мати декілька!),
              academicTitles  (МАСИВ ВСІХ звань),
              academicDegreesCount, academicTitlesCount.
            ВАЖЛИВО: завжди дивись на academicDegrees/academicTitles (плюрал) — там УСІ записи.
            Ліміт: 15 результатів. Якщо треба повна інфа — далі викликай getTeacherFullProfile(id).
            """)
    public String findTeacherByLastName(
            @ToolParam(description = "Прізвище або його частина для пошуку") String lastName
    ) {
        logTool("findTeacherByLastName", lastName);
        try {
            if (lastName == null || lastName.isBlank()) {
                return json(Map.of("error", "lastName is required"));
            }
            List<Teacher> found = teacherRepository.findByLastNameContainingIgnoreCase(lastName.trim());
            if (found.isEmpty()) {
                return "[]";
            }
            Map<Long, ComplianceReportDto> reportsById = complianceService.checkComplianceAll().stream()
                    .collect(Collectors.toMap(ComplianceReportDto::getTeacherId, r -> r, (a, b) -> a));

            List<Map<String, Object>> result = found.stream()
                    .limit(MAX_SEARCH_RESULTS)
                    .map(t -> compactTeacher(t, reportsById.get(t.getId())))
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("findTeacherByLastName", e);
        }
    }

    @Tool(description = """
            Отримати ПОВНИЙ профіль викладача за ID: освіта, ступінь/звання, УБД,
            військова освіта, контакти, мовні навички, досягнення п.38, публікації,
            підвищення кваліфікації, статус п.38 з переліком відсутніх типів.
            Викликай ПІСЛЯ findTeacherByLastName, коли знаєш teacherId.
            Повертає компактний JSON з усіма даними одного викладача.
            ВАЖЛИВО: scientific.academicDegrees — МАСИВ УСІХ ступенів (їх може бути декілька!).
            scientific.academicTitles — МАСИВ УСІХ звань. Завжди перевіряй довжину масиву
            перед відповіддю користувачу — не вважай що ступінь/звання тільки один.
            """)
    public String getTeacherFullProfile(
            @ToolParam(description = "ID викладача (long) отриманий з findTeacherByLastName") Long teacherId
    ) {
        logTool("getTeacherFullProfile", teacherId);
        try {
            if (teacherId == null) {
                return json(Map.of("error", "teacherId is required"));
            }
            Optional<Teacher> opt = teacherRepository.findById(teacherId);
            if (opt.isEmpty()) {
                return json(Map.of("error", "Teacher not found", "teacherId", teacherId));
            }
            Teacher t = opt.get();
            ComplianceReportDto report = safeCheckCompliance(teacherId);

            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("id", t.getId());
            profile.put("fullName", fullName(t));
            profile.put("dateOfBirth", t.getDateOfBirth() != null
                    ? t.getDateOfBirth().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    : null);
            profile.put("militaryRank", t.getMilitaryRank());
            profile.put("position", trunc(teacherPositionService.getEffectivePosition(t)));
            profile.put("employmentType", t.getEmploymentType());
            profile.put("department", t.getDepartment() != null ? t.getDepartment().getName() : null);
            profile.put("faculty", t.getDepartment() != null && t.getDepartment().getFaculty() != null
                    ? t.getDepartment().getFaculty().getName() : null);
            profile.put("experienceStartDate", t.getExperienceStartDate());

            Map<String, Object> scientific = new LinkedHashMap<>();

            // ПОВНІ списки ступенів і звань (один викладач може мати декілька)
            var degreesAll = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
            List<Map<String, Object>> degreesList = new ArrayList<>();
            for (var d : degreesAll) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("degree", d.getDegree());
                dm.put("speciality", d.getSpeciality());
                dm.put("dissertationTopic", trunc(d.getDissertationTopic()));
                dm.put("diploma", d.getDiploma());
                dm.put("diplomaDate", d.getDiplomaDate());
                dm.put("issuedBy", d.getIssuedBy());
                degreesList.add(dm);
            }
            scientific.put("academicDegrees", degreesList);

            var titlesAll = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
            List<Map<String, Object>> titlesList = new ArrayList<>();
            for (var at : titlesAll) {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("titleName", at.getTitleName());
                tm.put("attestat", at.getAttestat());
                tm.put("attestatDate", at.getAttestatDate());
                tm.put("issuedBy", at.getIssuedBy());
                titlesList.add(tm);
            }
            scientific.put("academicTitles", titlesList);

            profile.put("scientific", scientific);

            // ── Освіта (multi-record) ──
            // university — flat поля Teacher (для зворотної сумісності, primary освіта).
            Map<String, Object> university = new LinkedHashMap<>();
            university.put("institution", trunc(t.getUniversity()));
            university.put("speciality", trunc(t.getUniversitySpeciality()));
            university.put("diploma", t.getUniversityDiploma());
            university.put("graduationYear", t.getUniversityGraduationYear());
            profile.put("university", university);
            // educations — масив УСІХ освітніх записів (бакалавр, магістр тощо).
            List<Education> educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacherId);
            profile.put("educations", educations.stream().map(this::compactEducation).collect(Collectors.toList()));

            Map<String, Object> combat = new LinkedHashMap<>();
            combat.put("veteranStatus", t.isCombatVeteranStatus());
            combat.put("experienceDates", t.getCombatExperienceDates());
            profile.put("combat", combat);

            // ── Військова освіта ──
            // militaryEducation — flat поля Teacher (primary).
            Map<String, Object> milEd = new LinkedHashMap<>();
            milEd.put("level", t.getMilitaryEducationLevel() != null ? t.getMilitaryEducationLevel().name() : null);
            milEd.put("diploma", t.getMilitaryEducationDiploma());
            milEd.put("diplomaDate", t.getMilitaryEducationDiplomaDate());
            profile.put("militaryEducation", milEd);
            // militaryEducations — масив УСІХ записів про військову освіту.
            List<MilitaryEducation> milEdusList = militaryEducationRepository.findByTeacherIdOrderByGraduationYearDesc(teacherId);
            profile.put("militaryEducations", milEdusList.stream().map(this::compactMilitaryEducation).collect(Collectors.toList()));

            // ── Послужний список (career history) ──
            List<CareerRecord> careerRecords = careerRecordRepository.findByTeacherId(teacherId);
            profile.put("careerRecords", careerRecords.stream().map(this::compactCareerRecord).collect(Collectors.toList()));

            // ── Дисципліни які викладає ──
            List<TeacherDiscipline> tds = teacherDisciplineRepository.findByTeacherId(teacherId);
            profile.put("disciplines", tds.stream()
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactTeacherDiscipline)
                    .collect(Collectors.toList()));

            // ── Штатні посади (на яких призначений) ──
            List<StaffPosition> staffPositions = staffPositionRepository.findByTeacherId(teacherId);
            profile.put("staffPositions", staffPositions.stream().map(this::compactStaffPosition).collect(Collectors.toList()));

            // ── Рейтинг (Додаток 1) — поточний активний період ──
            Optional<RatingPeriod> activePeriod = ratingPeriodRepository.findByActiveTrue();
            if (activePeriod.isPresent()) {
                List<TeacherRating> ratings = teacherRatingRepository.findByPeriodIdAndTeacherId(
                        activePeriod.get().getId(), teacherId);
                Map<String, Object> ratingSummary = new LinkedHashMap<>();
                ratingSummary.put("periodName", activePeriod.get().getName());
                ratingSummary.put("totalScore", ratings.stream().mapToInt(TeacherRating::getScore).sum());
                ratingSummary.put("entriesCount", ratings.size());
                ratingSummary.put("entries", ratings.stream()
                        .sorted(Comparator.comparingInt(TeacherRating::getScore).reversed())
                        .limit(MAX_LIST_SIZE)
                        .map(this::compactRating)
                        .collect(Collectors.toList()));
                profile.put("rating", ratingSummary);
            }

            // Мовні навички
            List<LanguageSkill> langs = languageSkillRepository.findByTeacherId(teacherId);
            profile.put("languages", langs.stream().map(this::compactLanguage).collect(Collectors.toList()));

            // Compliance
            if (report != null) {
                Map<String, Object> comp = new LinkedHashMap<>();
                comp.put("status", report.getStatus() != null ? report.getStatus().name() : null);
                comp.put("uniqueTypeCount", report.getUniqueTypeCount());
                comp.put("required", 4);
                comp.put("achievementTypes", report.getAchievementTypes());
                comp.put("exemptionReason", report.getExemptionReason());
                comp.put("missingInfo", report.getMissingInfo());
                profile.put("compliance", comp);
            }

            // Досягнення
            List<Achievement> achs = achievementService.findByTeacherId(teacherId);
            profile.put("achievements", achs.stream()
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactAchievement)
                    .collect(Collectors.toList()));

            // Публікації (коротко, top 20)
            List<Publication> pubs = publicationService.findByTeacherId(teacherId);
            profile.put("publicationsCount", pubs.size());
            profile.put("publicationsTop", pubs.stream()
                    .sorted(Comparator.comparing(Publication::getYear, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(20)
                    .map(this::compactPublication)
                    .collect(Collectors.toList()));

            // Підвищення кваліфікації
            List<QualificationImprovement> quals = qualificationService.findByTeacherId(teacherId);
            profile.put("qualifications", quals.stream()
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactQualification)
                    .collect(Collectors.toList()));

            return json(profile);
        } catch (Exception e) {
            return toolError("getTeacherFullProfile", e);
        }
    }

    @Tool(description = """
            Знайти викладачів кафедри за назвою/номером. Приймає частковий збіг:
            "210", "управління", "зв'язку". Повертає JSON зі списком викладачів
            (id, fullName, rank, position, status) + інформацією про саму кафедру.
            """)
    public String listTeachersByDepartment(
            @ToolParam(description = "Назва або номер кафедри (частковий збіг)") String departmentQuery
    ) {
        logTool("listTeachersByDepartment", departmentQuery);
        try {
            if (departmentQuery == null || departmentQuery.isBlank()) {
                return json(Map.of("error", "departmentQuery is required"));
            }
            DepartmentComplianceSummaryDto dept = findDepartmentByQuery(departmentQuery);
            if (dept == null) {
                return json(Map.of("error", "Department not found", "query", departmentQuery));
            }
            Map<Long, ComplianceReportDto> reportsById = complianceService.checkComplianceAll().stream()
                    .collect(Collectors.toMap(ComplianceReportDto::getTeacherId, r -> r, (a, b) -> a));

            List<Teacher> teachers = teacherRepository.findByDepartmentId(dept.getDepartmentId());
            List<Map<String, Object>> teacherList = teachers.stream()
                    .limit(MAX_LIST_SIZE)
                    .map(t -> compactTeacher(t, reportsById.get(t.getId())))
                    .collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("department", Map.of(
                    "id", dept.getDepartmentId(),
                    "name", dept.getDepartmentName(),
                    "faculty", nvl(dept.getFacultyName()),
                    "totalTeachers", dept.getTotalTeachers(),
                    "overallStatus", dept.getOverallStatus()
            ));
            result.put("teachers", teacherList);
            return json(result);
        } catch (Exception e) {
            return toolError("listTeachersByDepartment", e);
        }
    }

    @Tool(description = """
            Перелік УСІХ кафедр системи з компактною статистикою: назва, факультет,
            кількість викладачів, % за п.35, рахунок п.38 (compliant/warning/nonCompliant),
            загальний статус (GOOD/WARNING/CRITICAL). Використовуй коли користувач
            хоче побачити огляд усіх кафедр.
            """)
    public String listDepartments() {
        logTool("listDepartments");
        try {
            List<DepartmentComplianceSummaryDto> depts = departmentComplianceService.getAllSummaries();
            List<Map<String, Object>> result = depts.stream()
                    .map(this::compactDepartment)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("listDepartments", e);
        }
    }

    @Tool(description = """
            Детальна compliance-статистика конкретної кафедри: кількість докторів,
            % на основній роботі зі ступенем (п.35), розподіл п.38, список
            викладачів з їх статусами та прізвищами. Використовуй коли
            запитують деталі по кафедрі.
            """)
    public String getDepartmentCompliance(
            @ToolParam(description = "Назва або номер кафедри (частковий збіг)") String departmentQuery
    ) {
        logTool("getDepartmentCompliance", departmentQuery);
        try {
            DepartmentComplianceSummaryDto dept = findDepartmentByQuery(departmentQuery);
            if (dept == null) {
                return json(Map.of("error", "Department not found", "query", departmentQuery));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", dept.getDepartmentId());
            result.put("name", dept.getDepartmentName());
            result.put("faculty", dept.getFacultyName());
            result.put("totalTeachers", dept.getTotalTeachers());
            result.put("mainEmployment", dept.getMainEmploymentTeachers());
            result.put("partTime", dept.getPartTimeTeachers());

            Map<String, Object> p35 = new LinkedHashMap<>();
            p35.put("withDegreeAndMain", dept.getWithDegreeAndMainCount());
            p35.put("withDegreeAndMainPercent", dept.getWithDegreeAndMainPercent());
            p35.put("compliant", dept.isPoint35Compliant());
            p35.put("doctorsOrProfessors", dept.getDoctorsOrProfessorsCount());
            p35.put("doctorsOrProfessorsPercent", dept.getDoctorsOrProfessorsPercent());
            result.put("point35", p35);

            Map<String, Object> p38 = new LinkedHashMap<>();
            p38.put("compliant", dept.getPoint38Compliant());
            p38.put("warning", dept.getPoint38Warning());
            p38.put("nonCompliant", dept.getPoint38NonCompliant());
            p38.put("exempt", dept.getPoint38Exempt());
            result.put("point38", p38);

            result.put("overallStatus", dept.getOverallStatus());

            // Список викладачів (компактно)
            if (dept.getTeacherReports() != null) {
                result.put("teachers", dept.getTeacherReports().stream()
                        .limit(MAX_LIST_SIZE)
                        .map(this::compactReport)
                        .collect(Collectors.toList()));
            }
            return json(result);
        } catch (Exception e) {
            return toolError("getDepartmentCompliance", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  COMPLIANCE TOOLS
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Список викладачів, які НЕ ВІДПОВІДАЮТЬ вимогам п.38
            (status = NON_COMPLIANT або WARNING). Повертає JSON-список
            з id, fullName, department, status, uniqueTypeCount, відсутні типи.
            Корисно для питань "хто не відповідає", "у кого проблеми", "хто в зоні ризику".
            Виключає EXEMPT (звільнених від вимог).
            """)
    public String getNonCompliantTeachers() {
        logTool("getNonCompliantTeachers");
        try {
            List<ComplianceReportDto> reports = complianceService.checkComplianceAll();
            List<Map<String, Object>> result = reports.stream()
                    .filter(r -> r.getStatus() == ComplianceStatus.NON_COMPLIANT
                            || r.getStatus() == ComplianceStatus.WARNING)
                    .sorted(Comparator.comparing(ComplianceReportDto::getStatus))
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactReport)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("getNonCompliantTeachers", e);
        }
    }

    @Tool(description = """
            Отримати статус відповідності п.38 конкретного викладача:
            статус (COMPLIANT/WARNING/NON_COMPLIANT/EXEMPT), кількість унікальних
            підпунктів з 20, перелік виконаних типів, відсутні дані (missingInfo),
            причину звільнення якщо EXEMPT.
            """)
    public String getTeacherCompliance(
            @ToolParam(description = "ID викладача") Long teacherId
    ) {
        logTool("getTeacherCompliance", teacherId);
        try {
            ComplianceReportDto r = complianceService.checkCompliance(teacherId);
            if (r == null) {
                return json(Map.of("error", "Compliance report unavailable", "teacherId", teacherId));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("teacherId", r.getTeacherId());
            result.put("teacherName", r.getTeacherName());
            result.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            result.put("uniqueTypeCount", r.getUniqueTypeCount());
            result.put("required", 4);
            result.put("achievementCount", r.getAchievementCount());
            result.put("achievementTypes", r.getAchievementTypes());
            result.put("missingInfo", r.getMissingInfo());
            result.put("exemptionReason", r.getExemptionReason());
            result.put("publicationsCount", r.getPublicationsCount());
            result.put("relevantPublicationsCount", r.getRelevantPublicationsCount());
            result.put("diplomaMatchesDepartment", r.isDiplomaMatchesDepartment());
            result.put("degreeMatchesDepartment", r.isDegreeMatchesDepartment());
            result.put("qualificationMatchesDepartment", r.isQualificationMatchesDepartment());
            return json(result);
        } catch (Exception e) {
            return toolError("getTeacherCompliance", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ACHIEVEMENT / PUBLICATION TOOLS
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Перелік досягнень п.38 конкретного викладача: тип (PP_1..PP_20),
            назва, дата, опис. Використовуй коли користувач запитує про
            конкретні досягнення, наприклад "що має Стоцький з п.38".
            """)
    public String getTeacherAchievements(
            @ToolParam(description = "ID викладача") Long teacherId
    ) {
        logTool("getTeacherAchievements", teacherId);
        try {
            List<Achievement> achs = achievementService.findByTeacherId(teacherId);
            List<Map<String, Object>> result = achs.stream()
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactAchievement)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("getTeacherAchievements", e);
        }
    }

    @Tool(description = """
            Перелік публікацій викладача: назва, рік, тип (ARTICLE/PATENT/TEXTBOOK/...),
            категорія (SCOPUS/WOS/CATEGORY_A/...), журнал, DOI, відповідність профілю
            кафедри (fieldRelevant). Сортує за роком спадання. Ліміт 30.
            """)
    public String getTeacherPublications(
            @ToolParam(description = "ID викладача") Long teacherId
    ) {
        logTool("getTeacherPublications", teacherId);
        try {
            List<Publication> pubs = publicationService.findByTeacherId(teacherId);
            List<Map<String, Object>> result = pubs.stream()
                    .sorted(Comparator.comparing(Publication::getYear, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactPublication)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("getTeacherPublications", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  QUALIFICATION / LANGUAGE TOOLS
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Пошук викладачів у яких є курси підвищення кваліфікації за ключовим словом
            у назві/організації. Корисно для питань типу "хто проходив NATO", "SANS",
            "військові курси", "стажування". Повертає teacherName, курс, організацію,
            дати, години, рівень (L2/L3/L4 для ВО курсів).
            """)
    public String searchTeachersByQualificationKeyword(
            @ToolParam(description = "Ключове слово для пошуку у назві або організації курсу (напр. 'NATO', 'SANS')") String keyword
    ) {
        logTool("searchTeachersByQualificationKeyword", keyword);
        try {
            if (keyword == null || keyword.isBlank()) {
                return json(Map.of("error", "keyword is required"));
            }
            String kw = keyword.trim().toLowerCase();
            List<Teacher> teachers = teacherRepository.findAll();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Teacher t : teachers) {
                List<QualificationImprovement> quals = qualificationService.findByTeacherId(t.getId());
                for (QualificationImprovement q : quals) {
                    boolean matches = (q.getTitle() != null && q.getTitle().toLowerCase().contains(kw))
                            || (q.getOrganization() != null && q.getOrganization().toLowerCase().contains(kw))
                            || (q.getCountry() != null && q.getCountry().toLowerCase().contains(kw));
                    if (matches) {
                        Map<String, Object> row = compactQualification(q);
                        row.put("teacherId", t.getId());
                        row.put("teacherName", fullName(t));
                        result.add(row);
                        if (result.size() >= MAX_LIST_SIZE) break;
                    }
                }
                if (result.size() >= MAX_LIST_SIZE) break;
            }
            return json(result);
        } catch (Exception e) {
            return toolError("searchTeachersByQualificationKeyword", e);
        }
    }

    @Tool(description = """
            Знайти викладачів за рівнем СМР/STANAG 6001. СМР обчислюється як мінімум
            з 4 компонентів (читання/аудіювання/письмо/говоріння). minLevel=2 поверне
            всіх у кого СМР >= 2 (напр. 2222, 2233). Повертає teacherName, language,
            smr1..smr4, level, certificate.
            """)
    public String searchTeachersByLanguageLevel(
            @ToolParam(description = "Мінімальний рівень СМР (1, 2, 3)") Integer minLevel
    ) {
        logTool("searchTeachersByLanguageLevel", minLevel);
        try {
            int min = minLevel != null ? minLevel : 2;
            List<LanguageSkill> all = languageSkillRepository.findAll();
            List<Map<String, Object>> result = all.stream()
                    .filter(l -> l.getSmrLevel() != null && l.getSmrLevel() >= min)
                    .sorted(Comparator.comparing(LanguageSkill::getSmrLevel, Comparator.reverseOrder()))
                    .limit(MAX_LIST_SIZE)
                    .map(l -> {
                        Map<String, Object> m = compactLanguage(l);
                        if (l.getTeacher() != null) {
                            m.put("teacherId", l.getTeacher().getId());
                            m.put("teacherName", fullName(l.getTeacher()));
                        }
                        return m;
                    })
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("searchTeachersByLanguageLevel", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SEMANTIC SEARCH (RAG)
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Семантичний (векторний) пошук викладачів за природно-мовним запитом.
            На відміну від findTeacherByLastName (точний збіг прізвища) і
            searchTeachersByQualificationKeyword (keyword у курсах), цей інструмент
            шукає за СМИСЛОМ у повному профілі: ПІБ, кафедра, дисертація, досягнення,
            публікації, бойовий досвід, курси, мови.

            ВИКОРИСТОВУЙ коли інші tools не спрацьовують або для широких описових запитів:
            - "Хто воював в АТО" / "ветерани"     → знайде за combatExperienceDates
            - "Хто працював над радіолокацією"    → знайде за темою дисертації/публікаціями
            - "Хто має досвід з кібербезпеки"     → знайде за курсами/досягненнями
            - "Хто викладав у Польщі"             → знайде за країнами стажувань

            Повертає JSON-список з полями: id, fullName, department, score (0..1), snippet.
            ВАЖЛИВО про score:
            - score >= 0.70 — ДУЖЕ релевантно (точна відповідність)
            - score 0.55..0.70 — помірно релевантно (варто перевірити snippet)
            - нижче 0.55 — ВЖЕ ВІДФІЛЬТРОВАНО (не повертається)
            Якщо результат [] — відповідників немає, відкрито скажи користувачу.
            Не видавай результат з score < 0.65 за "точну відповідь" — це приблизний збіг;
            краще скажи "найближчі за змістом, але не точний збіг".
            """)
    public String semanticSearchTeachers(
            @ToolParam(description = "Природно-мовний запит українською (або будь-якою мовою)") String query,
            @ToolParam(required = false, description = "Скільки результатів повернути (default 8, max 15)") Integer topK
    ) {
        logTool("semanticSearchTeachers", query, topK);
        try {
            AiEmbeddingIndexService idx = embeddingIndexProvider.getIfAvailable();
            if (idx == null || !idx.isAvailable()) {
                return json(Map.of(
                        "error", "Semantic search not available in this environment",
                        "hint", "RAG requires pgvector (prod profile) + ai.rag.enabled=true. Use other search tools instead."
                ));
            }
            if (query == null || query.isBlank()) {
                return json(Map.of("error", "query is required"));
            }
            int k = Math.min(Math.max(topK != null ? topK : 8, 1), 15);
            List<Document> docs = idx.search(query.trim(), k);
            if (docs.isEmpty()) {
                return "[]";
            }
            // Сортуємо за score DESC (similaritySearch вже повертає відсортовано, але для надійності)
            List<Map<String, Object>> result = docs.stream()
                    .sorted((a, b) -> {
                        Double sa = a.getScore() != null ? a.getScore() : 0.0;
                        Double sb = b.getScore() != null ? b.getScore() : 0.0;
                        return sb.compareTo(sa);
                    })
                    .map(d -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        Object teacherId = d.getMetadata().get("teacherId");
                        if (teacherId != null) row.put("id", teacherId);
                        row.put("fullName", d.getMetadata().getOrDefault("lastName", ""));
                        if (d.getMetadata().get("departmentName") != null)
                            row.put("department", d.getMetadata().get("departmentName"));
                        if (d.getScore() != null) {
                            row.put("score", Math.round(d.getScore() * 100.0) / 100.0);
                        }
                        String text = d.getText();
                        if (text != null) {
                            row.put("snippet", trunc(text.replaceAll("\\s+", " "), 300));
                        }
                        return row;
                    })
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("semanticSearchTeachers", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DISCIPLINES TOOLS
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Перелік дисциплін, які викладає викладач (за ID). Повертає JSON-масив з полями:
            disciplineId, disciplineName, disciplineCode, credits, totalHours, academicYear,
            semester, programName (ОПП), disciplineDepartment.
            Використовуй для запитів: "Які дисципліни викладає Стоцький?", "Що читає X у 2024-2025?".
            """)
    public String getTeacherDisciplines(
            @ToolParam(description = "ID викладача") Long teacherId
    ) {
        logTool("getTeacherDisciplines", teacherId);
        try {
            if (teacherId == null) return json(Map.of("error", "teacherId is required"));
            List<TeacherDiscipline> tds = teacherDisciplineRepository.findByTeacherId(teacherId);
            List<Map<String, Object>> result = tds.stream()
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactTeacherDiscipline)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("getTeacherDisciplines", e);
        }
    }

    @Tool(description = """
            Знайти викладачів, які викладають дисципліну за частковим збігом назви або коду.
            Приклади: "Кібернетика", "Системи управління", "ОК 6". Повертає JSON-масив:
            disciplineId, disciplineName, code, departmentName, programName,
            teachers[] = [{teacherId, teacherName, academicYear, semester}].
            Використовуй для запитів: "Хто викладає Кібернетику?", "Хто читає ОК 6?".
            """)
    public String findTeachersByDiscipline(
            @ToolParam(description = "Назва або код дисципліни (частковий збіг, регістронезалежно)") String disciplineQuery
    ) {
        logTool("findTeachersByDiscipline", disciplineQuery);
        try {
            if (disciplineQuery == null || disciplineQuery.isBlank()) {
                return json(Map.of("error", "disciplineQuery is required"));
            }
            String q = disciplineQuery.trim().toLowerCase();
            List<Discipline> matched = disciplineRepository.findAll().stream()
                    .filter(d -> (d.getName() != null && d.getName().toLowerCase().contains(q))
                            || (d.getCode() != null && d.getCode().toLowerCase().contains(q)))
                    .limit(MAX_LIST_SIZE)
                    .toList();
            if (matched.isEmpty()) return "[]";

            List<Map<String, Object>> result = new ArrayList<>();
            for (Discipline d : matched) {
                Map<String, Object> row = compactDiscipline(d);
                List<TeacherDiscipline> tds = teacherDisciplineRepository.findByDisciplineId(d.getId());
                List<Map<String, Object>> teachers = tds.stream().map(td -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    if (td.getTeacher() != null) {
                        t.put("teacherId", td.getTeacher().getId());
                        t.put("teacherName", fullName(td.getTeacher()));
                    }
                    t.put("academicYear", td.getAcademicYear());
                    t.put("semester", td.getSemester());
                    return t;
                }).collect(Collectors.toList());
                row.put("teachers", teachers);
                result.add(row);
            }
            return json(result);
        } catch (Exception e) {
            return toolError("findTeachersByDiscipline", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RATING TOOLS (Додаток 1)
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Отримати рейтинг (Додаток 1) конкретного викладача за період.
            Повертає JSON: periodName, totalScore, entriesCount, entries[] зі списком
            критеріїв (criterion, criterionLabel, count, score, pointsPerUnit, details).
            Якщо periodId не вказано — береться поточний активний період.
            Використовуй для: "Який рейтинг у Стоцького?", "Скільки балів у X за 2025?".
            """)
    public String getTeacherRating(
            @ToolParam(description = "ID викладача") Long teacherId,
            @ToolParam(required = false, description = "ID періоду рейтингування (опціонально, default — активний)") Long periodId
    ) {
        logTool("getTeacherRating", teacherId, periodId);
        try {
            if (teacherId == null) return json(Map.of("error", "teacherId is required"));
            RatingPeriod period;
            if (periodId != null) {
                Optional<RatingPeriod> opt = ratingPeriodRepository.findById(periodId);
                if (opt.isEmpty()) return json(Map.of("error", "Rating period not found", "periodId", periodId));
                period = opt.get();
            } else {
                Optional<RatingPeriod> active = ratingPeriodRepository.findByActiveTrue();
                if (active.isEmpty()) {
                    return json(Map.of("error", "No active rating period",
                            "hint", "Set periodId explicitly or activate a period."));
                }
                period = active.get();
            }
            List<TeacherRating> ratings = teacherRatingRepository.findByPeriodIdAndTeacherId(period.getId(), teacherId);
            int totalScore = ratings.stream().mapToInt(TeacherRating::getScore).sum();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("teacherId", teacherId);
            result.put("periodId", period.getId());
            result.put("periodName", period.getName());
            result.put("periodActive", period.isActive());
            result.put("totalScore", totalScore);
            result.put("entriesCount", ratings.size());
            result.put("entries", ratings.stream()
                    .sorted(Comparator.comparingInt(TeacherRating::getScore).reversed())
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactRating)
                    .collect(Collectors.toList()));
            return json(result);
        } catch (Exception e) {
            return toolError("getTeacherRating", e);
        }
    }

    @Tool(description = """
            Топ викладачів за загальним рейтингом (Додаток 1) за період.
            Можна фільтрувати по кафедрі (опціонально). Повертає JSON-масив:
            rank, teacherId, fullName, department, totalScore.
            Якщо periodId не вказано — поточний активний період.
            Використовуй для: "Топ-10 за балами", "Найкращі по кафедрі 33".
            """)
    public String getTopTeachersByRating(
            @ToolParam(required = false, description = "ID періоду (опціонально, default — активний)") Long periodId,
            @ToolParam(required = false, description = "Назва/номер кафедри (опціонально, для фільтрації)") String departmentQuery,
            @ToolParam(required = false, description = "Скільки повертати (default 10, max 30)") Integer limit
    ) {
        logTool("getTopTeachersByRating", periodId, departmentQuery, limit);
        try {
            RatingPeriod period;
            if (periodId != null) {
                Optional<RatingPeriod> opt = ratingPeriodRepository.findById(periodId);
                if (opt.isEmpty()) return json(Map.of("error", "Rating period not found", "periodId", periodId));
                period = opt.get();
            } else {
                Optional<RatingPeriod> active = ratingPeriodRepository.findByActiveTrue();
                if (active.isEmpty()) {
                    return json(Map.of("error", "No active rating period"));
                }
                period = active.get();
            }
            int lim = Math.min(Math.max(limit != null ? limit : 10, 1), MAX_LIST_SIZE);

            List<Object[]> rows;
            if (departmentQuery != null && !departmentQuery.isBlank()) {
                DepartmentComplianceSummaryDto dept = findDepartmentByQuery(departmentQuery);
                if (dept == null) {
                    return json(Map.of("error", "Department not found", "query", departmentQuery));
                }
                rows = teacherRatingRepository.findTotalScoresByPeriodIdAndDepartmentId(
                        period.getId(), dept.getDepartmentId());
            } else {
                rows = teacherRatingRepository.findTotalScoresByPeriodId(period.getId());
            }

            // Збираємо ID
            List<Long> teacherIds = rows.stream()
                    .limit(lim)
                    .map(r -> ((Number) r[0]).longValue())
                    .toList();
            Map<Long, Teacher> teachersById = teacherRepository.findAllById(teacherIds).stream()
                    .collect(Collectors.toMap(Teacher::getId, t -> t));

            List<Map<String, Object>> result = new ArrayList<>();
            int rank = 1;
            for (Object[] row : rows) {
                if (result.size() >= lim) break;
                Long tid = ((Number) row[0]).longValue();
                long score = ((Number) row[1]).longValue();
                Teacher t = teachersById.get(tid);
                if (t == null) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("rank", rank++);
                r.put("teacherId", tid);
                r.put("fullName", fullName(t));
                r.put("militaryRank", t.getMilitaryRank());
                r.put("department", t.getDepartment() != null ? t.getDepartment().getName() : null);
                r.put("totalScore", score);
                result.add(r);
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("periodId", period.getId());
            resp.put("periodName", period.getName());
            resp.put("departmentFilter", departmentQuery);
            resp.put("count", result.size());
            resp.put("teachers", result);
            return json(resp);
        } catch (Exception e) {
            return toolError("getTopTeachersByRating", e);
        }
    }

    @Tool(description = """
            Перелік усіх періодів рейтингування з позначенням активного.
            Повертає JSON-масив: id, name (напр. "2025-2026"), startDate, endDate, active.
            """)
    public String listRatingPeriods() {
        logTool("listRatingPeriods");
        try {
            List<RatingPeriod> periods = ratingPeriodRepository.findAll();
            List<Map<String, Object>> result = periods.stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId());
                m.put("name", p.getName());
                m.put("startDate", p.getStartDate());
                m.put("endDate", p.getEndDate());
                m.put("active", p.isActive());
                return m;
            }).collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("listRatingPeriods", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CAREER HISTORY TOOL
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Послужний список (career history) викладача: попередні посади, організації, дати.
            Повертає JSON-масив: id, position, organization, startDate, endDate, notes.
            Використовуй для: "Де працював Стоцький раніше?", "Стаж в НДІ?".
            """)
    public String getTeacherCareerHistory(
            @ToolParam(description = "ID викладача") Long teacherId
    ) {
        logTool("getTeacherCareerHistory", teacherId);
        try {
            if (teacherId == null) return json(Map.of("error", "teacherId is required"));
            List<CareerRecord> records = careerRecordRepository.findByTeacherId(teacherId);
            List<Map<String, Object>> result = records.stream()
                    .sorted(Comparator.comparing(CareerRecord::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactCareerRecord)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("getTeacherCareerHistory", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  EDUCATIONAL PROGRAMS (ОПП)
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Перелік усіх освітньо-професійних програм (ОПП) у системі.
            Повертає JSON-масив: id, name, shortCode, educationLevel, degree, specialty,
            credits, departmentName.
            Використовуй для: "Які є ОПП?", "Перелік програм".
            """)
    public String listEducationalPrograms() {
        logTool("listEducationalPrograms");
        try {
            List<EducationalProgram> programs = educationalProgramRepository.findAll();
            List<Map<String, Object>> result = programs.stream()
                    .map(this::compactProgram)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("listEducationalPrograms", e);
        }
    }

    @Tool(description = """
            Знайти ОПП за частковим збігом назви, коду спеціальності або skor.
            Повертає JSON-масив компактних описів програм.
            Приклади запитів: "Кібербезпека", "121", "F3 КН".
            """)
    public String findEducationalProgram(
            @ToolParam(description = "Назва, спеціальність або shortCode програми (частковий збіг)") String programQuery
    ) {
        logTool("findEducationalProgram", programQuery);
        try {
            if (programQuery == null || programQuery.isBlank()) {
                return json(Map.of("error", "programQuery is required"));
            }
            String q = programQuery.trim().toLowerCase();
            List<EducationalProgram> all = educationalProgramRepository.findAll();
            List<Map<String, Object>> result = all.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(q))
                            || (p.getSpecialty() != null && p.getSpecialty().toLowerCase().contains(q))
                            || (p.getShortCode() != null && p.getShortCode().toLowerCase().contains(q)))
                    .limit(MAX_LIST_SIZE)
                    .map(this::compactProgram)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("findEducationalProgram", e);
        }
    }

    @Tool(description = """
            Список ОПП конкретної кафедри (за назвою/номером). Повертає JSON-масив
            програм та інфо про саму кафедру.
            Використовуй для: "Які ОПП на кафедрі 33?", "Програми кафедри кібербезпеки".
            """)
    public String getDepartmentEducationalPrograms(
            @ToolParam(description = "Назва або номер кафедри (частковий збіг)") String departmentQuery
    ) {
        logTool("getDepartmentEducationalPrograms", departmentQuery);
        try {
            DepartmentComplianceSummaryDto dept = findDepartmentByQuery(departmentQuery);
            if (dept == null) {
                return json(Map.of("error", "Department not found", "query", departmentQuery));
            }
            List<EducationalProgram> programs = educationalProgramRepository.findByDepartmentId(dept.getDepartmentId());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("department", Map.of(
                    "id", dept.getDepartmentId(),
                    "name", dept.getDepartmentName()
            ));
            result.put("count", programs.size());
            result.put("programs", programs.stream().map(this::compactProgram).collect(Collectors.toList()));
            return json(result);
        } catch (Exception e) {
            return toolError("getDepartmentEducationalPrograms", e);
        }
    }

    @Tool(description = """
            Перелік викладачів, що викладають дисципліни конкретної ОПП.
            Повертає JSON: program (id, name, shortCode), disciplines[] зі списком
            викладачів кожної дисципліни.
            Використовуй для: "Викладачі для ОПП Кібербезпека", "Хто викладає на F3 КН".
            """)
    public String getProgramTeachers(
            @ToolParam(description = "ID ОПП (отриманий з listEducationalPrograms або findEducationalProgram)") Long programId
    ) {
        logTool("getProgramTeachers", programId);
        try {
            if (programId == null) return json(Map.of("error", "programId is required"));
            Optional<EducationalProgram> opt = educationalProgramRepository.findById(programId);
            if (opt.isEmpty()) return json(Map.of("error", "Program not found", "programId", programId));
            EducationalProgram prog = opt.get();

            List<Discipline> disciplines = disciplineRepository.findByEducationalProgramId(programId);
            List<Map<String, Object>> discRows = new ArrayList<>();
            java.util.Set<Long> teacherIds = new java.util.LinkedHashSet<>();

            for (Discipline d : disciplines) {
                Map<String, Object> dRow = new LinkedHashMap<>();
                dRow.put("disciplineId", d.getId());
                dRow.put("disciplineName", trunc(d.getName()));
                dRow.put("code", d.getCode());
                dRow.put("credits", d.getCredits());

                List<TeacherDiscipline> tds = teacherDisciplineRepository.findByDisciplineId(d.getId());
                List<Map<String, Object>> tRows = tds.stream().map(td -> {
                    Map<String, Object> tRow = new LinkedHashMap<>();
                    if (td.getTeacher() != null) {
                        teacherIds.add(td.getTeacher().getId());
                        tRow.put("teacherId", td.getTeacher().getId());
                        tRow.put("teacherName", fullName(td.getTeacher()));
                    }
                    tRow.put("academicYear", td.getAcademicYear());
                    tRow.put("semester", td.getSemester());
                    return tRow;
                }).collect(Collectors.toList());
                dRow.put("teachers", tRows);
                discRows.add(dRow);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("program", compactProgram(prog));
            result.put("disciplinesCount", disciplines.size());
            result.put("uniqueTeachersCount", teacherIds.size());
            result.put("disciplines", discRows);
            return json(result);
        } catch (Exception e) {
            return toolError("getProgramTeachers", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STAFF POSITIONS (штатний розпис)
    // ═══════════════════════════════════════════════════════════════

    @Tool(description = """
            Штатний розпис кафедри: упорядкований за orderNumber список штатних посад.
            Кожна посада: orderNumber (№ у штатці), positionTitle ("Начальник кафедри",
            "Доцент"), militaryRankCategory ("Полковник"), tariffGrade, rate, teacherId
            (null якщо ВАКАНТ), teacherName.
            Використовуй для: "Хто на 2-й штатній посаді 33-ї кафедри?",
            "Який штатний розпис кафедри?", "Скільки вакансій на кафедрі?".
            """)
    public String getDepartmentStaffPositions(
            @ToolParam(description = "Назва або номер кафедри (частковий збіг)") String departmentQuery
    ) {
        logTool("getDepartmentStaffPositions", departmentQuery);
        try {
            DepartmentComplianceSummaryDto dept = findDepartmentByQuery(departmentQuery);
            if (dept == null) {
                return json(Map.of("error", "Department not found", "query", departmentQuery));
            }
            List<StaffPosition> positions = staffPositionRepository.findByDepartmentIdOrderByOrderNumber(dept.getDepartmentId());
            long vacant = positions.stream().filter(sp -> sp.getTeacher() == null).count();
            long filled = positions.size() - vacant;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("department", Map.of(
                    "id", dept.getDepartmentId(),
                    "name", dept.getDepartmentName()
            ));
            result.put("totalPositions", positions.size());
            result.put("filled", filled);
            result.put("vacant", vacant);
            result.put("positions", positions.stream().map(this::compactStaffPosition).collect(Collectors.toList()));
            return json(result);
        } catch (Exception e) {
            return toolError("getDepartmentStaffPositions", e);
        }
    }

    @Tool(description = """
            Штатна посада конкретного викладача: на яких посадах кафедр призначений
            (один викладач може бути на кількох — напр. 0.5+0.5 ставки).
            Повертає JSON-масив: orderNumber, positionTitle, departmentName, rate.
            """)
    public String getTeacherStaffPosition(
            @ToolParam(description = "ID викладача") Long teacherId
    ) {
        logTool("getTeacherStaffPosition", teacherId);
        try {
            if (teacherId == null) return json(Map.of("error", "teacherId is required"));
            List<StaffPosition> positions = staffPositionRepository.findByTeacherId(teacherId);
            List<Map<String, Object>> result = positions.stream()
                    .map(this::compactStaffPosition)
                    .collect(Collectors.toList());
            return json(result);
        } catch (Exception e) {
            return toolError("getTeacherStaffPosition", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════════════

    private Map<String, Object> compactTeacher(Teacher t, ComplianceReportDto r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("fullName", fullName(t));
        m.put("militaryRank", t.getMilitaryRank());
        m.put("position", trunc(teacherPositionService.getEffectivePosition(t)));
        m.put("department", t.getDepartment() != null ? t.getDepartment().getName() : null);
        m.put("employmentType", t.getEmploymentType());

        // ── Усі ступені та звання (один викладач може мати декілька) ──
        var allDegrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        var allTitles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
        var primaryDegree = ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking.primary(allDegrees);
        var primaryTitle = ua.edu.teacherlicence.teacher.util.AcademicTitleRanking.primary(allTitles);

        // primary (найвищий за рангом) — для зворотної сумісності з існуючими промптами
        m.put("academicDegree", primaryDegree != null ? primaryDegree.getDegree() : null);
        m.put("academicTitle", primaryTitle != null ? primaryTitle.getTitleName() : null);

        // ВСІ ступені — список рядків, щоб AI бачив повну картину (а не лише primary).
        // Якщо тільки 1 запис — будемо мати масив з 1 елементом, що дзеркалить academicDegree.
        m.put("academicDegrees", allDegrees.stream()
                .map(d -> d.getDegree())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList()));
        m.put("academicTitles", allTitles.stream()
                .map(at -> at.getTitleName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList()));
        m.put("academicDegreesCount", allDegrees.size());
        m.put("academicTitlesCount", allTitles.size());

        if (r != null) {
            m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            m.put("uniqueTypeCount", r.getUniqueTypeCount());
        }
        return m;
    }

    private Map<String, Object> compactEducation(Education e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("institution", trunc(e.getInstitution()));
        m.put("city", e.getCity());
        m.put("degree", e.getDegree());
        m.put("speciality", trunc(e.getSpeciality()));
        m.put("qualification", trunc(e.getQualification()));
        m.put("graduationYear", e.getGraduationYear());
        m.put("diploma", trunc(e.getDiploma()));
        m.put("diplomaDate", e.getDiplomaDate());
        return m;
    }

    private Map<String, Object> compactMilitaryEducation(MilitaryEducation me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", me.getId());
        m.put("level", me.getLevel() != null ? me.getLevel().name() : null);
        m.put("institution", trunc(me.getInstitution()));
        m.put("speciality", trunc(me.getSpeciality()));
        m.put("diploma", trunc(me.getDiploma()));
        m.put("diplomaDate", me.getDiplomaDate());
        m.put("issuedBy", trunc(me.getIssuedBy()));
        m.put("graduationYear", me.getGraduationYear());
        return m;
    }

    private Map<String, Object> compactCareerRecord(CareerRecord cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cr.getId());
        m.put("position", trunc(cr.getPosition()));
        m.put("organization", trunc(cr.getOrganization()));
        m.put("startDate", cr.getStartDate());
        m.put("endDate", cr.getEndDate());
        if (cr.getNotes() != null && !cr.getNotes().isBlank()) m.put("notes", trunc(cr.getNotes()));
        return m;
    }

    private Map<String, Object> compactTeacherDiscipline(TeacherDiscipline td) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", td.getId());
        m.put("academicYear", td.getAcademicYear());
        m.put("semester", td.getSemester());
        if (td.getDiscipline() != null) {
            Discipline d = td.getDiscipline();
            m.put("disciplineId", d.getId());
            m.put("disciplineName", trunc(d.getName()));
            m.put("disciplineCode", d.getCode());
            m.put("credits", d.getCredits());
            m.put("totalHours", d.getTotalHours());
            if (d.getEducationalProgram() != null) {
                m.put("programId", d.getEducationalProgram().getId());
                m.put("programName", trunc(d.getEducationalProgram().getName()));
                m.put("programShortCode", d.getEducationalProgram().getShortCode());
            }
            if (d.getDepartment() != null) {
                m.put("disciplineDepartment", d.getDepartment().getName());
            }
        }
        return m;
    }

    private Map<String, Object> compactDiscipline(Discipline d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", trunc(d.getName()));
        m.put("code", d.getCode());
        m.put("credits", d.getCredits());
        m.put("totalHours", d.getTotalHours());
        m.put("auditoryHours", d.getAuditoryHours());
        if (d.getDepartment() != null) {
            m.put("departmentId", d.getDepartment().getId());
            m.put("departmentName", d.getDepartment().getName());
        }
        if (d.getEducationalProgram() != null) {
            m.put("programId", d.getEducationalProgram().getId());
            m.put("programName", trunc(d.getEducationalProgram().getName()));
            m.put("programShortCode", d.getEducationalProgram().getShortCode());
        }
        return m;
    }

    private Map<String, Object> compactStaffPosition(StaffPosition sp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sp.getId());
        m.put("orderNumber", sp.getOrderNumber());
        m.put("positionTitle", trunc(sp.getPositionTitle()));
        m.put("militaryRankCategory", sp.getMilitaryRankCategory());
        m.put("militarySpecialtyCode", sp.getMilitarySpecialtyCode());
        m.put("tariffGrade", sp.getTariffGrade());
        m.put("rate", sp.getRate());
        if (sp.getDepartment() != null) {
            m.put("departmentId", sp.getDepartment().getId());
            m.put("departmentName", sp.getDepartment().getName());
            m.put("departmentNumber", sp.getDepartment().getNumber());
        }
        if (sp.getTeacher() != null) {
            m.put("teacherId", sp.getTeacher().getId());
            m.put("teacherName", fullName(sp.getTeacher()));
        } else {
            m.put("vacant", true);
            if (sp.getImportedTeacherName() != null) {
                m.put("importedTeacherName", sp.getImportedTeacherName());
            }
        }
        return m;
    }

    private Map<String, Object> compactRating(TeacherRating r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        if (r.getCriterion() != null) {
            m.put("criterion", r.getCriterion().name());
            m.put("criterionLabel", r.getCriterion().getLabel());
            m.put("pointsPerUnit", r.getCriterion().getPoints());
        }
        m.put("count", r.getCount());
        m.put("score", r.getScore());
        if (r.getDetails() != null && !r.getDetails().isBlank()) m.put("details", trunc(r.getDetails()));
        return m;
    }

    private Map<String, Object> compactProgram(EducationalProgram ep) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ep.getId());
        m.put("name", trunc(ep.getName()));
        m.put("shortCode", ep.getShortCode());
        m.put("educationLevel", ep.getEducationLevel());
        m.put("educationForm", ep.getEducationForm());
        m.put("degree", ep.getDegree());
        m.put("specialty", trunc(ep.getSpecialty()));
        m.put("fieldOfKnowledge", trunc(ep.getFieldOfKnowledge()));
        m.put("credits", ep.getCredits());
        m.put("duration", ep.getDuration());
        m.put("enrollmentYear", ep.getEnrollmentYear());
        if (ep.getDepartment() != null) {
            m.put("departmentId", ep.getDepartment().getId());
            m.put("departmentName", ep.getDepartment().getName());
            m.put("departmentNumber", ep.getDepartment().getNumber());
        }
        return m;
    }

    private Map<String, Object> compactDepartment(DepartmentComplianceSummaryDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getDepartmentId());
        m.put("name", d.getDepartmentName());
        m.put("faculty", d.getFacultyName());
        m.put("totalTeachers", d.getTotalTeachers());
        m.put("p35percent", d.getWithDegreeAndMainPercent());
        m.put("p35compliant", d.isPoint35Compliant());
        m.put("p38compliant", d.getPoint38Compliant());
        m.put("p38warning", d.getPoint38Warning());
        m.put("p38nonCompliant", d.getPoint38NonCompliant());
        m.put("p38exempt", d.getPoint38Exempt());
        m.put("overallStatus", d.getOverallStatus());
        return m;
    }

    private Map<String, Object> compactReport(ComplianceReportDto r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("teacherId", r.getTeacherId());
        m.put("teacherName", r.getTeacherName());
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        m.put("uniqueTypeCount", r.getUniqueTypeCount());
        m.put("achievementTypes", r.getAchievementTypes());
        if (r.getExemptionReason() != null) m.put("exemptionReason", r.getExemptionReason());
        return m;
    }

    private Map<String, Object> compactAchievement(Achievement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("type", a.getAchievementType() != null ? a.getAchievementType().name() : null);
        m.put("title", trunc(a.getTitle()));
        m.put("date", a.getDateAchieved());
        if (a.getQualifiedCount() != null) m.put("qualifiedCount", a.getQualifiedCount());
        if (a.isVerified()) m.put("verified", true);
        return m;
    }

    private Map<String, Object> compactPublication(Publication p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", trunc(p.getTitle()));
        m.put("year", p.getYear());
        m.put("type", p.getType() != null ? p.getType().name() : null);
        if (p.getArticleCategory() != null) m.put("category", p.getArticleCategory().name());
        if (p.getJournalName() != null) m.put("journal", trunc(p.getJournalName()));
        if (p.getDoi() != null) m.put("doi", p.getDoi());
        if (p.getFieldRelevant() != null) m.put("fieldRelevant", p.getFieldRelevant());
        return m;
    }

    private Map<String, Object> compactQualification(QualificationImprovement q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("title", trunc(q.getTitle()));
        m.put("organization", trunc(q.getOrganization()));
        m.put("startDate", q.getStartDate());
        m.put("endDate", q.getEndDate());
        if (q.getHours() != null) m.put("hours", q.getHours());
        if (q.getCertificateNumber() != null) m.put("certificateNumber", q.getCertificateNumber());
        if (q.getCategory() != null) m.put("category", q.getCategory().name());
        if (q.getMilitaryCourseLevel() != null) m.put("militaryCourseLevel", q.getMilitaryCourseLevel().name());
        if (q.getCountry() != null) m.put("country", q.getCountry());
        return m;
    }

    private Map<String, Object> compactLanguage(LanguageSkill l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("language", l.getLanguage());
        m.put("level", l.getLevel());
        m.put("smr1", l.getSmr1());
        m.put("smr2", l.getSmr2());
        m.put("smr3", l.getSmr3());
        m.put("smr4", l.getSmr4());
        m.put("smrLevel", l.getSmrLevel());
        if (l.getCertificateNumber() != null) m.put("certificateNumber", l.getCertificateNumber());
        if (l.getCertificateDate() != null) m.put("certificateDate", l.getCertificateDate());
        if (l.getCertificateOrganization() != null) m.put("organization", trunc(l.getCertificateOrganization()));
        return m;
    }

    /** Шукає кафедру за частковим збігом у назві або номері. */
    private DepartmentComplianceSummaryDto findDepartmentByQuery(String q) {
        if (q == null || q.isBlank()) return null;
        String normalized = q.trim().toLowerCase();
        return departmentComplianceService.getAllSummaries().stream()
                .filter(d -> d.getDepartmentName() != null
                        && d.getDepartmentName().toLowerCase().contains(normalized))
                .findFirst()
                .orElse(null);
    }

    private ComplianceReportDto safeCheckCompliance(Long teacherId) {
        try {
            return complianceService.checkCompliance(teacherId);
        } catch (Exception e) {
            log.warn("checkCompliance failed for teacherId={}", teacherId, e);
            return null;
        }
    }

    private static String fullName(Teacher t) {
        StringBuilder sb = new StringBuilder();
        if (t.getLastName() != null) sb.append(t.getLastName());
        if (t.getFirstName() != null) sb.append(" ").append(t.getFirstName());
        if (t.getPatronymic() != null) sb.append(" ").append(t.getPatronymic());
        return sb.toString().trim();
    }

    private static long count(List<ComplianceReportDto> list, ComplianceStatus status) {
        return list.stream().filter(r -> r.getStatus() == status).count();
    }

    private static String trunc(String s) {
        if (s == null) return null;
        return s.length() > MAX_FIELD_LEN ? s.substring(0, MAX_FIELD_LEN) + "…" : s;
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private String json(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "{\"error\":\"serialization failed\"}";
        }
    }

    /** Логування виклику tool (INFO щоб бачити у prod). Викликається першим рядком у кожному @Tool. */
    private static void logTool(String name, Object... args) {
        if (log.isInfoEnabled()) {
            log.info("[AI Tool] → {} args={}", name, java.util.Arrays.toString(args));
        }
    }

    private String toolError(String toolName, Exception e) {
        log.error("Tool '{}' failed: {}", toolName, e.getMessage(), e);
        try {
            return objectMapper.writeValueAsString(
                    Map.of("error", "Tool execution failed",
                            "tool", toolName,
                            "message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"" + toolName + " failed\"}";
        }
    }
}
