package ua.edu.teacherlicence.opp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.service.AchievementService;
import ua.edu.teacherlicence.achievement.service.ComplianceService;
import ua.edu.teacherlicence.ai.service.QualificationMatchAiService;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.discipline.model.Discipline;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.DisciplineRepository;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.opp.dto.DisciplineStaffingDto;
import ua.edu.teacherlicence.opp.dto.DisciplineStaffingDto.TeacherQualificationDto;
import ua.edu.teacherlicence.opp.dto.ProgramStaffStats;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EducationalProgramService {

    private final EducationalProgramRepository repository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final DepartmentRepository departmentRepository;
    private final DisciplineRepository disciplineRepository;
    private final TeacherDisciplineRepository teacherDisciplineRepository;
    private final ComplianceService complianceService;
    private final AchievementService achievementService;
    private final ua.edu.teacherlicence.achievement.service.AchievementValidationService achievementValidationService;
    private final ua.edu.teacherlicence.teacher.repository.CareerRecordRepository careerRecordRepository;
    private final ua.edu.teacherlicence.ppdata.repository.PracticalExperienceRepository practicalExperienceRepository;
    private final ua.edu.teacherlicence.ppdata.repository.ScientificSupervisionRepository scientificSupervisionRepository;

    private final ua.edu.teacherlicence.teacher.repository.EducationRepository educationRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository academicTitleRepository;
    private final ua.edu.teacherlicence.teacher.repository.TeacherRepository teacherRepository;
    private final org.springframework.context.ApplicationEventPublisher events;

    /** AI-сервіс для перевірки відповідності кваліфікації (опціональний, може бути null якщо ai.enabled=false) */
    @Autowired(required = false)
    private QualificationMatchAiService qualificationMatchAiService;

    public List<EducationalProgram> findAll() {
        return repository.findAll();
    }

    public EducationalProgram findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("ОПП не знайдено: " + id));
    }

    public List<EducationalProgram> findByDepartmentId(Long departmentId) {
        return repository.findByDepartmentId(departmentId);
    }

    @Transactional
    public EducationalProgram create(EducationalProgram program, Long departmentId) {
        if (departmentId != null) {
            program.setDepartment(departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new RuntimeException("Кафедру не знайдено: " + departmentId)));
        }
        EducationalProgram saved = repository.save(program);
        events.publishEvent(new ua.edu.teacherlicence.compliance.events.ComplianceEvents.EducationalProgramChanged(saved.getId()));
        return saved;
    }

    @Transactional
    public EducationalProgram update(Long id, EducationalProgram data, Long departmentId) {
        EducationalProgram existing = findById(id);
        existing.setName(data.getName());
        existing.setShortCode(data.getShortCode());
        existing.setEducationLevel(data.getEducationLevel());
        existing.setEducationForm(data.getEducationForm());
        existing.setDegree(data.getDegree());
        existing.setEducationalQualification(data.getEducationalQualification());
        existing.setFieldOfKnowledge(data.getFieldOfKnowledge());
        existing.setProfessionalQualification(data.getProfessionalQualification());
        existing.setSpecialty(data.getSpecialty());
        existing.setCredits(data.getCredits());
        existing.setSpecialization(data.getSpecialization());
        existing.setDuration(data.getDuration());
        existing.setEnrollmentYear(data.getEnrollmentYear());

        if (departmentId != null) {
            existing.setDepartment(departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new RuntimeException("Кафедру не знайдено: " + departmentId)));
        } else {
            existing.setDepartment(null);
        }

        EducationalProgram saved = repository.save(existing);
        events.publishEvent(new ua.edu.teacherlicence.compliance.events.ComplianceEvents.EducationalProgramChanged(saved.getId()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        events.publishEvent(new ua.edu.teacherlicence.compliance.events.ComplianceEvents.EducationalProgramDeleted(id));
    }

    /**
     * Розрахунок кадрових показників ОПП згідно п.35 та п.36
     */
    @Transactional(readOnly = true)
    public ProgramStaffStats getStaffStats(Long programId) {
        EducationalProgram program = findById(programId);

        // 1. Get all disciplines for this program
        List<Discipline> disciplines = disciplineRepository.findByEducationalProgramId(programId);

        // 2. Get all teacher assignments
        List<Long> disciplineIds = disciplines.stream().map(Discipline::getId).toList();
        Set<Teacher> uniqueTeachers = disciplineIds.stream()
                .flatMap(dId -> teacherDisciplineRepository.findByDisciplineId(dId).stream())
                .map(TeacherDiscipline::getTeacher)
                .collect(Collectors.toSet());

        int total = uniqueTeachers.size();

        // 3. п.35 A: ≥50% with degree/title AND main employment
        long mainWithDegree = uniqueTeachers.stream()
                .filter(t -> "MAIN".equals(t.getEmploymentType()))
                .filter(this::hasDegreeOrTitle)
                .count();
        double mainWithDegreePercent = total > 0 ? (mainWithDegree * 100.0 / total) : 0;

        // 4. п.35 B: doctors or professors
        long doctorsOrProfessors = uniqueTeachers.stream()
                .filter(this::isDoctorOrProfessor)
                .count();
        double doctorsOrProfessorsPercent = total > 0 ? (doctorsOrProfessors * 100.0 / total) : 0;

        // 5. п.35 C: ≥3 with degree/title + MAIN + п.37 compliant (по ОПП!)
        long qualifiedMainCount = uniqueTeachers.stream()
                .filter(t -> "MAIN".equals(t.getEmploymentType()))
                .filter(this::hasDegreeOrTitle)
                .filter(t -> checkPoint37ForProgram(t, program).point37Compliant)
                .count();

        // 6. Забезпечені компоненти (п.36+п.37): дисципліна забезпечена, якщо хоча б один
        //    викладач відповідає і п.36 (≥4 типи п.38) і п.37 (кваліфікація + публікації)
        int disciplinesTotal = disciplines.size();
        int disciplinesFullyStaffed = 0;
        for (Discipline d : disciplines) {
            DisciplineStaffingDto staffing = buildDisciplineStaffing(d, program);
            if (staffing.isStaffed()) disciplinesFullyStaffed++;
        }

        return ProgramStaffStats.builder()
                .degree(program.getDegree())
                .totalTeachers(total)
                .mainWithDegreeCount((int) mainWithDegree)
                .mainWithDegreePercent(Math.round(mainWithDegreePercent * 10) / 10.0)
                .point35Compliant(mainWithDegreePercent >= 50.0)
                .doctorsOrProfessorsCount((int) doctorsOrProfessors)
                .doctorsOrProfessorsPercent(Math.round(doctorsOrProfessorsPercent * 10) / 10.0)
                .qualifiedMainCount((int) qualifiedMainCount)
                .point35cCompliant(qualifiedMainCount >= 3)
                .disciplinesTotal(disciplinesTotal)
                .disciplinesFullyStaffed(disciplinesFullyStaffed)
                .point36Compliant(disciplinesFullyStaffed == disciplinesTotal && disciplinesTotal > 0)
                .build();
    }

    /**
     * Розрахунок забезпеченості всіх дисциплін ОПП згідно п.36 та п.37.
     * Повертає map: disciplineId → DisciplineStaffingDto
     */
    @Transactional(readOnly = true)
    public Map<Long, DisciplineStaffingDto> getDisciplineStaffing(Long programId) {
        EducationalProgram program = findById(programId);
        List<Discipline> disciplines = disciplineRepository.findByEducationalProgramId(programId);

        return disciplines.stream().collect(Collectors.toMap(
                Discipline::getId,
                d -> buildDisciplineStaffing(d, program)
        ));
    }

    private DisciplineStaffingDto buildDisciplineStaffing(Discipline discipline, EducationalProgram program) {
        List<TeacherDiscipline> tds = teacherDisciplineRepository.findByDisciplineId(discipline.getId());
        List<TeacherQualificationDto> teacherDtos = new ArrayList<>();

        for (TeacherDiscipline td : tds) {
            Teacher teacher = td.getTeacher();
            String fullName = teacher.getLastName() + " " + teacher.getFirstName();

            // п.36: ≥4 types of achievements (from existing compliance)
            ComplianceReportDto compliance = complianceService.checkCompliance(teacher.getId());
            int point38Types = compliance.getUniqueTypeCount();
            boolean p36 = point38Types >= 4;

            // п.37: перевірка відповідності дисципліні
            Point37Result p37result = checkPoint37ForDiscipline(teacher, discipline, program);

            boolean full = p36 && p37result.point37Compliant;

            teacherDtos.add(TeacherQualificationDto.builder()
                    .teacherId(teacher.getId())
                    .teacherName(fullName)
                    .position(teacherPositionService.getEffectivePosition(teacher))
                    .employmentType(teacher.getEmploymentType())
                    .academicDegree(primaryDegreeName(teacher))
                    .academicTitle(primaryTitleName(teacher))
                    .point38TypeCount(point38Types)
                    .point36Compliant(p36)
                    .hasMatchingDiploma(p37result.hasMatchingDiploma)
                    .hasMatchingDegree(p37result.hasMatchingDegree)
                    .hasPracticalExperience(p37result.hasPracticalExperience)
                    .hasDissertationSupervision(p37result.hasDissertationSupervision)
                    .point37aCompliant(p37result.point37aCompliant)
                    .qualifiedPublicationsCount(p37result.qualifiedPublicationsCount)
                    .point37bCompliant(p37result.point37bCompliant)
                    .point37Compliant(p37result.point37Compliant)
                    .fullyCompliant(full)
                    .build());
        }

        boolean staffed = teacherDtos.stream().anyMatch(TeacherQualificationDto::isFullyCompliant);

        return DisciplineStaffingDto.builder()
                .disciplineId(discipline.getId())
                .teachers(teacherDtos)
                .staffed(!tds.isEmpty() && staffed)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // п.37: Блок А + Блок Б
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Результат перевірки п.37 для одного викладача
     */
    // ═══════════════════════════════════════════════════════════════
    //  Public compute wrappers — використовуються ComplianceCache services.
    //  Фетчать Teacher/Discipline/Program і делегують до private checkPoint37*.
    // ═══════════════════════════════════════════════════════════════

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Point37Result computePoint37ForDiscipline(Long teacherId, Long disciplineId) {
        var teacher = teacherRepository.findById(teacherId).orElse(null);
        var discipline = disciplineRepository.findById(disciplineId).orElse(null);
        if (teacher == null || discipline == null || discipline.getEducationalProgram() == null) return null;
        return checkPoint37ForDiscipline(teacher, discipline, discipline.getEducationalProgram());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Point37Result computePoint37ForProgram(Long teacherId, Long programId) {
        var teacher = teacherRepository.findById(teacherId).orElse(null);
        var program = repository.findById(programId).orElse(null);
        if (teacher == null || program == null) return null;
        return checkPoint37ForProgram(teacher, program);
    }

    public record Point37Result(
            boolean hasMatchingDiploma,         // А1
            boolean hasMatchingDegree,          // А2
            boolean hasPracticalExperience,     // А3 (пп.20)
            boolean hasDissertationSupervision, // А4 (пп.6)
            boolean point37aCompliant,          // Блок А: хоча б один з А1-А4
            int qualifiedPublicationsCount,     // Блок Б: кількість кваліфікованих публікацій
            boolean point37bCompliant,          // Блок Б виконано (≥5)
            boolean point37Compliant            // п.37 повністю (А і Б)
    ) {}

    private static final int REQUIRED_PUBLICATIONS = 5;

    /**
     * п.37 для дисципліни (п.36+п.37 забезпечення компонента).
     * AI перевіряє відповідність кваліфікації ДИСЦИПЛІНІ.
     *
     * <p>А1 (диплом) і А2 (ступінь) перевіряються НЕЗАЛЕЖНО:
     * можлива ситуація коли є диплом але немає ступеня (магістр без PhD)
     * — раніше внутрішній цикл по degreeViews не виконувався і A1 завжди
     * залишався false. Тепер якщо degreeViews порожній — додаємо placeholder
     * DegreeView, щоб цикл виконав AI-перевірку диплома без ступеня.
     */
    private Point37Result checkPoint37ForDiscipline(Teacher teacher, Discipline discipline, EducationalProgram program) {
        boolean a1 = false, a2 = false;

        if (qualificationMatchAiService != null) {
            var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
            var degreeViews = degreeViewsOf(teacher);
            // Placeholder коли немає ступенів — щоб A1 (диплом) все ж перевірявся.
            List<DegreeView> dvList = degreeViews.isEmpty()
                    ? List.of(new DegreeView(0L, null, null, null))
                    : degreeViews;

            outer:
            if (!educations.isEmpty()) {
                for (var edu : educations) {
                    for (DegreeView dv : dvList) {
                        var aiResult = qualificationMatchAiService.checkDisciplineMatch(
                                teacher.getId(),
                                cacheKey3(discipline.getId(), edu.getId(), dv.id()),
                                edu.getSpeciality(),
                                dv.degree(),
                                dv.topic(),
                                dv.speciality(),
                                discipline.getName(),
                                program.getSpecialty(),
                                program.getFieldOfKnowledge()
                        );
                        if (aiResult.diplomaMatches()) a1 = true;
                        if (aiResult.degreeMatches()) a2 = true;
                        if (a1 && a2) break outer;
                    }
                }
            } else {
                for (DegreeView dv : dvList) {
                    var aiResult = qualificationMatchAiService.checkDisciplineMatch(
                            teacher.getId(), cacheKey3(discipline.getId(), 0L, dv.id()),
                            teacher.getUniversitySpeciality(),
                            dv.degree(),
                            dv.topic(),
                            dv.speciality(),
                            discipline.getName(),
                            program.getSpecialty(),
                            program.getFieldOfKnowledge()
                    );
                    if (aiResult.diplomaMatches()) a1 = true;
                    if (aiResult.degreeMatches()) a2 = true;
                    if (a1 && a2) break;
                }
            }
            // ВАЖЛИВО: для забезпечення дисципліни (п.36+п.37) перевіряється
            // ТІЛЬКИ ступінь, без вчених звань. Title-match використовується
            // лише на рівні ОПП (п.35 A/C).
        } else {
            var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
            a1 = educations.stream().anyMatch(e -> e.getSpeciality() != null && !e.getSpeciality().isBlank());
            if (!a1) a1 = teacher.getUniversitySpeciality() != null && !teacher.getUniversitySpeciality().isBlank();
            a2 = !academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId()).isEmpty();
        }

        return buildPoint37ResultForDiscipline(teacher, a1, a2, discipline, program);
    }

    /**
     * п.37 для ОПП (п.35с кваліфікація за програмою).
     * AI перевіряє відповідність кваліфікації СПЕЦІАЛЬНОСТІ ОПП.
     * Перевіряються ВСІ освіти ✕ ВСІ ступені — якщо хоча б одна пара відповідає, повертає true.
     */
    private Point37Result checkPoint37ForProgram(Teacher teacher, EducationalProgram program) {
        boolean a1 = false, a2 = false;

        if (qualificationMatchAiService != null) {
            var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
            var degreeViews = degreeViewsOf(teacher);
            // Placeholder коли немає ступенів — щоб A1 (диплом) все ж перевірявся.
            // Без цього магістр без PhD не отримував A1=true навіть з відповідним дипломом.
            List<DegreeView> dvList = degreeViews.isEmpty()
                    ? List.of(new DegreeView(0L, null, null, null))
                    : degreeViews;

            outer:
            if (!educations.isEmpty()) {
                for (var edu : educations) {
                    for (DegreeView dv : dvList) {
                        var aiResult = qualificationMatchAiService.checkProgramMatch(
                                teacher.getId(),
                                cacheKey3(program.getId(), edu.getId(), dv.id()),
                                edu.getSpeciality(),
                                dv.degree(),
                                dv.topic(),
                                dv.speciality(),
                                program.getSpecialty(),
                                program.getFieldOfKnowledge(),
                                program.getName()
                        );
                        if (aiResult.diplomaMatches()) a1 = true;
                        if (aiResult.degreeMatches()) a2 = true;
                        if (a1 && a2) break outer;
                    }
                }
            } else {
                for (DegreeView dv : dvList) {
                    var aiResult = qualificationMatchAiService.checkProgramMatch(
                            teacher.getId(), cacheKey3(program.getId(), 0L, dv.id()),
                            teacher.getUniversitySpeciality(),
                            dv.degree(),
                            dv.topic(),
                            dv.speciality(),
                            program.getSpecialty(),
                            program.getFieldOfKnowledge(),
                            program.getName()
                    );
                    if (aiResult.diplomaMatches()) a1 = true;
                    if (aiResult.degreeMatches()) a2 = true;
                    if (a1 && a2) break;
                }
            }

            // Якщо ступінь не задовольнив A2 — пробуємо вчені звання vs ОПП.
            if (!a2 && titleMatchesProgram(teacher, program)) {
                a2 = true;
            }
        } else {
            var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
            a1 = educations.stream().anyMatch(e -> e.getSpeciality() != null && !e.getSpeciality().isBlank());
            if (!a1) a1 = teacher.getUniversitySpeciality() != null && !teacher.getUniversitySpeciality().isBlank();
            boolean hasAnyDegree = !academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId()).isEmpty();
            boolean hasAnyTitle = !academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacher.getId()).isEmpty();
            a2 = hasAnyDegree || hasAnyTitle;
        }

        return buildPoint37ResultForProgram(teacher, a1, a2, program);
    }

    /** AI-перевірка: чи бодай одне звання викладача відповідає ОПП. */
    private boolean titleMatchesProgram(Teacher teacher, EducationalProgram program) {
        if (qualificationMatchAiService == null) return false;
        String descriptor = String.join(" | ",
                program.getName() != null ? program.getName() : "",
                program.getSpecialty() != null ? program.getSpecialty() : "",
                program.getFieldOfKnowledge() != null ? program.getFieldOfKnowledge() : "");
        return iterateTitlesAndCheck(teacher, program.getId(),
                ua.edu.teacherlicence.ai.service.QualificationMatchAiService.TitleTargetKind.PROGRAM,
                descriptor);
    }

    /**
     * Загальний хелпер: ітерує по всіх звань викладача (з flat-fallback)
     * і викликає AI-перевірку проти заданого target-об'єкту. Повертає true
     * як тільки знайде перший збіг.
     *
     * Зараз використовується лише для PROGRAM (п.35 A/C ОПП). Для DISCIPLINE
     * не використовується — відповідно до вимог: для забезпечення дисципліни
     * (п.36+п.37) звання не зараховуються, тільки ступінь.
     */
    private boolean iterateTitlesAndCheck(Teacher teacher, Long targetId,
            ua.edu.teacherlicence.ai.service.QualificationMatchAiService.TitleTargetKind kind,
            String descriptor) {
        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacher.getId());
        List<String> titleNames = titles.stream()
                .map(t -> t.getTitleName())
                .filter(s -> s != null && !s.isBlank())
                .toList();
        for (String name : titleNames) {
            if (qualificationMatchAiService.checkTitleMatch(
                    teacher.getId(), targetId, kind, name, descriptor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Список (id, degree, topic, speciality) для AI-перевірки.
     * Якщо у викладача немає ступенів — повертає порожній список (a2 не задовольняється).
     */
    private List<DegreeView> degreeViewsOf(Teacher teacher) {
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());
        return degrees.stream()
                .map(d -> new DegreeView(d.getId(),
                        d.getDegree(),
                        d.getDissertationTopic(),
                        d.getSpeciality()))
                .toList();
    }

    private String primaryDegreeName(Teacher teacher) {
        var d = ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking.primary(
                academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId()));
        return d != null ? d.getDegree() : null;
    }

    private String primaryTitleName(Teacher teacher) {
        var t = ua.edu.teacherlicence.teacher.util.AcademicTitleRanking.primary(
                academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacher.getId()));
        return t != null ? t.getTitleName() : null;
    }

    /** Ключ для AI-кешу, унікальний по (target, education, degree). */
    private static long cacheKey3(Long target, Long eduId, Long degreeId) {
        long t = target == null ? 0 : target;
        long e = eduId == null ? 0 : eduId;
        long g = degreeId == null ? 0 : degreeId;
        return t * 1_000_000L + e * 1000L + g;
    }

    private record DegreeView(Long id, String degree, String topic, String speciality) {}

    /**
     * Спільна логіка п.37 для ДИСЦИПЛІНИ (А3, А4, Блок Б).
     *
     * <p>А3 на рівні дисципліни вимагає:
     * <ul>
     *   <li>Тривалість профільного досвіду ≥5 років (PP_20 fulfilled — на рівні кафедри)</li>
     *   <li><b>І</b> хоча б один запис practical_experience/career_record відповідає
     *       саме цій дисципліні (AI checkPracticalExperienceMatchDiscipline).</li>
     * </ul>
     *
     * <p>А4 на рівні дисципліни:
     * <ul>
     *   <li>≥1 захищений здобувач (PP_6 fulfilled)</li>
     *   <li><b>І</b> хоча б одне керівництво по темі що відповідає дисципліні
     *       (AI на рівні topic + speciality).</li>
     * </ul>
     */
    private Point37Result buildPoint37ResultForDiscipline(
            Teacher teacher, boolean a1, boolean a2,
            Discipline discipline, EducationalProgram program
    ) {
        var progressList = safeProgress(teacher.getId());

        boolean pp20Fulfilled = progressList.stream()
                .filter(p -> p.getPpNumber() == 20)
                .anyMatch(p -> p.isFulfilled());
        boolean pp6Fulfilled = progressList.stream()
                .filter(p -> p.getPpNumber() == 6)
                .anyMatch(p -> p.isFulfilled());

        boolean a3 = pp20Fulfilled
                && hasPracticalExperienceMatchingDiscipline(teacher, discipline, program);
        boolean a4 = pp6Fulfilled
                && hasSupervisionMatchingDiscipline(teacher, discipline, program);

        boolean blockA = a1 || a2 || a3 || a4;
        int qualifiedPubs = getQualifiedPublicationCount(teacher.getId());
        boolean blockB = qualifiedPubs >= REQUIRED_PUBLICATIONS;
        boolean compliant = blockA && blockB;

        return new Point37Result(a1, a2, a3, a4, blockA, qualifiedPubs, blockB, compliant);
    }

    /**
     * Спільна логіка п.37 для ОПП (А3, А4, Блок Б).
     * А3 — досвід має відповідати спеціальності ОПП (не дисципліні).
     */
    private Point37Result buildPoint37ResultForProgram(
            Teacher teacher, boolean a1, boolean a2, EducationalProgram program
    ) {
        var progressList = safeProgress(teacher.getId());

        boolean pp20Fulfilled = progressList.stream()
                .filter(p -> p.getPpNumber() == 20)
                .anyMatch(p -> p.isFulfilled());
        boolean pp6Fulfilled = progressList.stream()
                .filter(p -> p.getPpNumber() == 6)
                .anyMatch(p -> p.isFulfilled());

        boolean a3 = pp20Fulfilled
                && hasPracticalExperienceMatchingProgram(teacher, program);
        // Для ОПП А4 простіше: достатньо щоб був принаймні один захищений здобувач
        // (без перевірки відповідності темі програми — для всієї ОПП це широко).
        boolean a4 = pp6Fulfilled;

        boolean blockA = a1 || a2 || a3 || a4;
        int qualifiedPubs = getQualifiedPublicationCount(teacher.getId());
        boolean blockB = qualifiedPubs >= REQUIRED_PUBLICATIONS;
        boolean compliant = blockA && blockB;

        return new Point37Result(a1, a2, a3, a4, blockA, qualifiedPubs, blockB, compliant);
    }

    /**
     * Перевіряє чи серед career_records / practical_experience викладача
     * є хоч один запис що AI вважає профільним для конкретної дисципліни.
     * Викликається тільки якщо PP_20 уже fulfilled (тривалість ≥5 років за кафедрою).
     */
    private boolean hasPracticalExperienceMatchingDiscipline(
            Teacher teacher, Discipline discipline, EducationalProgram program
    ) {
        if (qualificationMatchAiService == null) return true;   // AI недоступний → fallback
        String programSpec = program != null ? program.getSpecialty() : null;
        String programField = program != null ? program.getFieldOfKnowledge() : null;

        // Пріоритет: career_records → practical_experience (узгоджено з checkPp20)
        var career = careerRecordRepository.findByTeacherId(teacher.getId());
        if (!career.isEmpty()) {
            for (var cr : career) {
                if (qualificationMatchAiService.checkPracticalExperienceMatchDiscipline(
                        cr.getPosition(), cr.getOrganization(), null,
                        discipline.getId(), discipline.getName(), programSpec, programField)) return true;
            }
            return false;
        }
        var practical = practicalExperienceRepository.findByTeacherId(teacher.getId());
        for (var pe : practical) {
            if (qualificationMatchAiService.checkPracticalExperienceMatchDiscipline(
                    pe.getPosition(), pe.getOrganizationName(), pe.getSpecialtyName(),
                    discipline.getId(), discipline.getName(), programSpec, programField)) return true;
        }
        return false;
    }

    private boolean hasPracticalExperienceMatchingProgram(Teacher teacher, EducationalProgram program) {
        if (qualificationMatchAiService == null) return true;
        if (program == null) return false;

        // Пріоритет: career_records → practical_experience (узгоджено з checkPp20)
        var career = careerRecordRepository.findByTeacherId(teacher.getId());
        if (!career.isEmpty()) {
            for (var cr : career) {
                if (qualificationMatchAiService.checkPracticalExperienceMatchProgram(
                        cr.getPosition(), cr.getOrganization(), null,
                        program.getId(), program.getSpecialty(), program.getFieldOfKnowledge(),
                        program.getName())) return true;
            }
            return false;
        }
        var practical = practicalExperienceRepository.findByTeacherId(teacher.getId());
        for (var pe : practical) {
            if (qualificationMatchAiService.checkPracticalExperienceMatchProgram(
                    pe.getPosition(), pe.getOrganizationName(), pe.getSpecialtyName(),
                    program.getId(), program.getSpecialty(), program.getFieldOfKnowledge(),
                    program.getName())) return true;
        }
        return false;
    }

    /**
     * Перевіряє чи серед керівництв викладача (scientific_supervision) є хоча б одне
     * за спеціальністю, що відповідає дисципліні. Використовує AI checkDisciplineMatch
     * з topic+speciality керівництва замість dissertation → дисципліна.
     */
    private boolean hasSupervisionMatchingDiscipline(
            Teacher teacher, Discipline discipline, EducationalProgram program
    ) {
        if (qualificationMatchAiService == null) return true;
        String programSpec = program != null ? program.getSpecialty() : null;
        String programField = program != null ? program.getFieldOfKnowledge() : null;

        var supervisions = scientificSupervisionRepository.findByTeacherId(teacher.getId());
        for (var s : supervisions) {
            if (s.getDefenseDate() == null) continue;
            // Використовуємо існуючий disciplineMatch — топік дисертації ↔ дисципліна
            var result = qualificationMatchAiService.checkDisciplineMatch(
                    teacher.getId(),
                    /*cacheKey*/ (long) ("supv:" + s.getId() + ":" + discipline.getId()).hashCode(),
                    null,                                         // universitySpec
                    /*degree*/ s.getDegreeType() != null ? s.getDegreeType().name() : null,
                    /*topic*/ s.getTopic(),
                    /*speciality*/ null,
                    discipline.getName(), programSpec, programField);
            if (result.degreeMatches()) return true;
        }
        return false;
    }

    /** Safe wrapper: getProgressForTeacher може кинути виняток (наприклад timeout AI). */
    private List<ua.edu.teacherlicence.achievement.dto.AchievementProgressDto> safeProgress(Long teacherId) {
        try {
            return achievementValidationService.getProgressForTeacher(teacherId);
        } catch (Exception e) {
            log.warn("getProgressForTeacher failed for teacherId={}: {}", teacherId, e.getMessage());
            return List.of();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Бере кількість кваліфікованих публікацій з Achievement PP_1.
     * qualifiedCount встановлюється AchievementComposer при recompose
     * (вже з фільтром fieldRelevant==false і OUTDATED).
     */
    private int getQualifiedPublicationCount(Long teacherId) {
        return achievementService.findByTeacherId(teacherId).stream()
                .filter(a -> a.getAchievementType() == AchievementType.PP_1_PUBLICATIONS)
                .findFirst()
                .map(a -> a.getQualifiedCount() != null ? a.getQualifiedCount() : 0)
                .orElse(0);
    }

    private boolean hasAchievementType(Long teacherId, AchievementType type) {
        return achievementService.findByTeacherId(teacherId).stream()
                .anyMatch(a -> a.getAchievementType() == type);
    }

    private boolean hasDegreeOrTitle(Teacher t) {
        boolean hasAnyDegree = !academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).isEmpty();
        boolean hasAnyTitle = !academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId()).isEmpty();
        return hasAnyDegree || hasAnyTitle;
    }

    private boolean isDoctorOrProfessor(Teacher t) {
        // Будь-який ступінь типу "Доктор наук" АБО будь-яке звання "Професор".
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        boolean isDoctor = degrees.stream()
                .map(d -> d.getDegree() != null ? d.getDegree().toLowerCase() : "")
                .anyMatch(s -> s.contains("доктор") && !s.contains("філософ"));
        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
        boolean isProfessor = titles.stream()
                .map(at -> at.getTitleName() != null ? at.getTitleName().toLowerCase() : "")
                .anyMatch(s -> s.contains("професор"));
        return isDoctor || isProfessor;
    }
}
