package ua.edu.teacherlicence.teacher.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.compliance.events.ComplianceEvents;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.department.service.DepartmentService;
import ua.edu.teacherlicence.teacher.dto.AcademicDegreeDto;
import ua.edu.teacherlicence.teacher.dto.EducationDto;
import ua.edu.teacherlicence.teacher.dto.MilitaryEducationDto;
import ua.edu.teacherlicence.teacher.dto.TeacherCreateRequest;
import ua.edu.teacherlicence.teacher.dto.TeacherDto;
import ua.edu.teacherlicence.teacher.model.*;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository;
import ua.edu.teacherlicence.teacher.repository.CareerRecordRepository;
import ua.edu.teacherlicence.teacher.repository.EducationRepository;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.MilitaryEducationRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking;
import ua.edu.teacherlicence.teacher.util.AcademicTitleRanking;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final CareerRecordRepository careerRecordRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final EducationRepository educationRepository;
    private final MilitaryEducationRepository militaryEducationRepository;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final AcademicTitleRepository academicTitleRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final ua.edu.teacherlicence.achievement.service.AchievementComposer achievementComposer;
    private final TeacherPositionService teacherPositionService;

    @Transactional(readOnly = true)
    public List<TeacherDto> findAll() {
        return enrichWithRegalia(teacherRepository.findAll());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TeacherDto> findAllPaged(
            org.springframework.data.domain.Pageable pageable, String search, Long departmentId) {
        org.springframework.data.domain.Page<Teacher> page;
        if (departmentId != null && search != null && !search.isBlank()) {
            page = teacherRepository.findByDepartmentIdAndLastNameContainingIgnoreCase(departmentId, search, pageable);
        } else if (departmentId != null) {
            page = teacherRepository.findByDepartmentId(departmentId, pageable);
        } else if (search != null && !search.isBlank()) {
            page = teacherRepository.findByLastNameContainingIgnoreCase(search, pageable);
        } else {
            page = teacherRepository.findAll(pageable);
        }
        // Batch-fetch regalia для всіх teachers на сторінці одним запитом — без N+1.
        List<TeacherDto> enriched = enrichWithRegalia(page.getContent());
        Map<Long, TeacherDto> byId = enriched.stream().collect(Collectors.toMap(TeacherDto::getId, d -> d));
        return page.map(t -> byId.getOrDefault(t.getId(), TeacherDto.fromEntity(t)));
    }

    @Transactional(readOnly = true)
    public TeacherDto findById(Long id) {
        Teacher teacher = findTeacherEntityById(id);
        TeacherDto dto = TeacherDto.fromEntity(teacher);
        enrichSingleWithRegalia(dto, id);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<TeacherDto> findByDepartmentId(Long departmentId) {
        return enrichWithRegalia(teacherRepository.findByDepartmentId(departmentId));
    }

    @Transactional(readOnly = true)
    public List<TeacherDto> search(String query) {
        return enrichWithRegalia(teacherRepository.findByLastNameContainingIgnoreCase(query));
    }

    /**
     * Batch-збагачує список Teacher → TeacherDto з primary academicDegree та academicTitle,
     * читаючи academic_degrees та academic_titles одним запитом замість N+1.
     * Додатково: effectivePosition / totalRate / bootstrappedPosition зі штатного розпису.
     */
    private List<TeacherDto> enrichWithRegalia(List<Teacher> teachers) {
        if (teachers == null || teachers.isEmpty()) return List.of();
        List<Long> ids = teachers.stream().map(Teacher::getId).filter(java.util.Objects::nonNull).toList();
        Map<Long, List<AcademicDegree>> degreesByTeacher = ids.isEmpty()
                ? Map.of()
                : academicDegreeRepository.findByTeacherIdIn(ids).stream()
                .collect(Collectors.groupingBy(d -> d.getTeacher().getId()));
        Map<Long, List<AcademicTitle>> titlesByTeacher = ids.isEmpty()
                ? Map.of()
                : academicTitleRepository.findByTeacherIdIn(ids).stream()
                .collect(Collectors.groupingBy(t -> t.getTeacher().getId()));
        Map<Long, String> effectivePositions = teacherPositionService.getEffectivePositions(teachers);
        Map<Long, Double> totalRates = teacherPositionService.getTotalRates(teachers);
        Map<Long, Boolean> bootstrappedFlags = teacherPositionService.getBootstrappedPositionFlags(teachers);
        return teachers.stream().map(t -> {
            TeacherDto dto = TeacherDto.fromEntity(t);
            populateRegalia(dto,
                    degreesByTeacher.getOrDefault(t.getId(), List.of()),
                    titlesByTeacher.getOrDefault(t.getId(), List.of()));
            populateStaffPosition(dto, t, effectivePositions, totalRates, bootstrappedFlags);
            return dto;
        }).toList();
    }

    /** Збагачує один DTO для findById/create/update — простий per-teacher запит. */
    private void enrichSingleWithRegalia(TeacherDto dto, Long teacherId) {
        if (dto == null || teacherId == null) return;
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacherId);
        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacherId);
        populateRegalia(dto, degrees, titles);
        // Single-teacher шлях: збагачуємо посаду через одиничний виклик сервісу.
        Teacher t = teacherRepository.findById(teacherId).orElse(null);
        if (t != null) {
            List<Teacher> single = List.of(t);
            Map<Long, String> ep = teacherPositionService.getEffectivePositions(single);
            Map<Long, Double> tr = teacherPositionService.getTotalRates(single);
            Map<Long, Boolean> bf = teacherPositionService.getBootstrappedPositionFlags(single);
            populateStaffPosition(dto, t, ep, tr, bf);
        }
    }

    private void populateRegalia(TeacherDto dto, List<AcademicDegree> degrees, List<AcademicTitle> titles) {
        AcademicDegree primaryD = AcademicDegreeRanking.primary(degrees);
        AcademicTitle primaryT = AcademicTitleRanking.primary(titles);
        dto.setAcademicDegree(primaryD != null ? primaryD.getDegree() : null);
        dto.setAcademicTitle(primaryT != null ? primaryT.getTitleName() : null);
        dto.setAcademicDegreesCount(degrees.size());
        dto.setAcademicTitlesCount(titles.size());
    }

    private void populateStaffPosition(TeacherDto dto, Teacher t,
                                        Map<Long, String> effectivePositions,
                                        Map<Long, Double> totalRates,
                                        Map<Long, Boolean> bootstrappedFlags) {
        // staff_positions — єдине джерело правди (Стадія 5 рефакторингу).
        // null коли у викладача немає жодної штатної позиції.
        dto.setEffectivePosition(effectivePositions.get(t.getId()));
        dto.setTotalRate(totalRates.get(t.getId()));
        dto.setBootstrappedPosition(bootstrappedFlags.getOrDefault(t.getId(), false));
    }

    public TeacherDto create(TeacherCreateRequest request) {
        Teacher teacher = mapRequestToEntity(request, new Teacher());
        Teacher saved = teacherRepository.save(teacher);
        departmentService.autoLinkStaffPositionsForTeacher(saved);
        events.publishEvent(new ComplianceEvents.TeacherChanged(saved.getId()));
        TeacherDto dto = TeacherDto.fromEntity(saved);
        enrichSingleWithRegalia(dto, saved.getId());
        return dto;
    }

    public TeacherDto update(Long id, TeacherCreateRequest request) {
        Teacher existing = findTeacherEntityById(id);
        Long oldDeptId = existing.getDepartment() != null ? existing.getDepartment().getId() : null;
        mapRequestToEntity(request, existing);
        Teacher saved = teacherRepository.save(existing);
        departmentService.autoLinkStaffPositionsForTeacher(saved);
        Long newDeptId = saved.getDepartment() != null ? saved.getDepartment().getId() : null;
        if (!java.util.Objects.equals(oldDeptId, newDeptId)) {
            events.publishEvent(new ComplianceEvents.TeacherDepartmentChanged(saved.getId(), oldDeptId, newDeptId));
        } else {
            events.publishEvent(new ComplianceEvents.TeacherChanged(saved.getId()));
        }
        TeacherDto dto = TeacherDto.fromEntity(saved);
        enrichSingleWithRegalia(dto, saved.getId());
        return dto;
    }

    public void delete(Long id) {
        if (!teacherRepository.existsById(id)) {
            throw new EntityNotFoundException("Teacher not found with id: " + id);
        }
        // Видалення всіх пов'язаних записів перед видаленням викладача
        deleteAllRelatedData(id);
        teacherRepository.deleteById(id);
        log.info("Deleted teacher id={} with all related data", id);
        events.publishEvent(new ComplianceEvents.TeacherDeleted(id));
    }

    /**
     * Каскадне видалення всіх пов'язаних сутностей через нативний SQL.
     */
    private void deleteAllRelatedData(Long teacherId) {
        String[] tables = {
                // Achievement-related
                "validation_results",
                "publications",
                "achievements",
                // Teacher-related
                "academic_degrees",
                "academic_titles",
                "educations",
                "teacher_disciplines",
                "qualification_improvements",
                "career_records",
                "language_skills",
                // ppData tables (all have teacher_id FK)
                "scientific_supervision",
                "attestation_activity",
                "editorial_activity",
                "expert_council",
                "foreign_language_teaching",
                "international_project",
                "military_mission",
                "olympiad_guidance",
                "practical_experience",
                "professional_association",
                "scientific_consulting",
                // Other
                "discipline_documents",
                "editorial_plan_items",
                "gantt_events",
                "ppdata_validation_results",
                "staff_positions",
                // Rating entities
                "teacher_ratings",
                "open_lessons",
                "methodological_experiments",
                "academic_mobilities",
                "program_working_groups",
        };
        for (String table : tables) {
            try {
                int count = entityManager.createNativeQuery("DELETE FROM " + table + " WHERE teacher_id = :tid")
                        .setParameter("tid", teacherId)
                        .executeUpdate();
                if (count > 0) {
                    log.debug("Deleted {} rows from {} for teacher {}", count, table, teacherId);
                }
            } catch (Exception e) {
                // Таблиця може не існувати або не мати teacher_id — ігноруємо
                log.trace("Skip table {}: {}", table, e.getMessage());
            }
        }
    }

    // ── Educations ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EducationDto> findEducations(Long teacherId) {
        return educationRepository.findByTeacherIdOrderByGraduationYearDesc(teacherId)
                .stream().map(EducationDto::fromEntity).toList();
    }

    public EducationDto createEducation(Long teacherId, EducationDto dto) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found: " + teacherId));
        Education edu = Education.builder()
                .teacher(teacher)
                .institution(dto.getInstitution())
                .city(dto.getCity())
                .degree(dto.getDegree())
                .speciality(dto.getSpeciality())
                .qualification(dto.getQualification())
                .graduationYear(dto.getGraduationYear())
                .diploma(normalizeDiploma(dto.getDiploma()))
                .diplomaDate(dto.getDiplomaDate())
                .build();
        Education saved = educationRepository.save(edu);
        syncPrimaryEducation(teacher);
        events.publishEvent(new ComplianceEvents.EducationChanged(teacherId));
        return EducationDto.fromEntity(saved);
    }

    public EducationDto updateEducation(Long eduId, EducationDto dto) {
        Education edu = educationRepository.findById(eduId)
                .orElseThrow(() -> new EntityNotFoundException("Education not found: " + eduId));
        edu.setInstitution(dto.getInstitution());
        edu.setCity(dto.getCity());
        edu.setDegree(dto.getDegree());
        edu.setSpeciality(dto.getSpeciality());
        edu.setQualification(dto.getQualification());
        edu.setGraduationYear(dto.getGraduationYear());
        edu.setDiploma(normalizeDiploma(dto.getDiploma()));
        edu.setDiplomaDate(dto.getDiplomaDate());
        Education saved = educationRepository.save(edu);
        Teacher t = edu.getTeacher();
        syncPrimaryEducation(t);
        if (t != null) events.publishEvent(new ComplianceEvents.EducationChanged(t.getId()));
        return EducationDto.fromEntity(saved);
    }

    public void deleteEducation(Long eduId) {
        Education edu = educationRepository.findById(eduId)
                .orElseThrow(() -> new EntityNotFoundException("Education not found: " + eduId));
        Teacher teacher = edu.getTeacher();
        educationRepository.delete(edu);
        syncPrimaryEducation(teacher);
        if (teacher != null) events.publishEvent(new ComplianceEvents.EducationChanged(teacher.getId()));
    }

    /**
     * Sync Teacher's flat university fields from the latest (by graduation year) Education record.
     * This keeps backward compatibility with compliance checks, AI context, exports.
     */
    private void syncPrimaryEducation(Teacher teacher) {
        List<Education> educations = educationRepository
                .findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
        if (!educations.isEmpty()) {
            Education latest = educations.get(0);
            teacher.setUniversity(latest.getInstitution());
            teacher.setUniversitySpeciality(latest.getSpeciality());
            teacher.setUniversityDiploma(latest.getDiploma());
            teacher.setUniversityGraduationYear(latest.getGraduationYear());
            teacher.setUniversityDiplomaDate(latest.getDiplomaDate());
        } else {
            teacher.setUniversity(null);
            teacher.setUniversitySpeciality(null);
            teacher.setUniversityDiploma(null);
            teacher.setUniversityGraduationYear(null);
            teacher.setUniversityDiplomaDate(null);
        }
        teacherRepository.save(teacher);
    }

    /**
     * Remove "Диплом" prefix from diploma string (e.g. "Диплом ДВО №063245" → "ДВО №063245").
     */
    private String normalizeDiploma(String diploma) {
        if (diploma == null) return null;
        return diploma
                .replaceFirst("(?iu)^[Дд]иплом(\\s+(з\\s+відзнакою|магістра|спеціаліста|бакалавра|молодшого спеціаліста))?\\s*:?\\s*", "")
                .trim();
    }

    // ── Academic Degrees ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AcademicDegreeDto> findAcademicDegrees(Long teacherId) {
        return academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacherId)
                .stream().map(AcademicDegreeDto::fromEntity).toList();
    }

    public AcademicDegreeDto createAcademicDegree(Long teacherId, AcademicDegreeDto dto) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found: " + teacherId));
        AcademicDegree d = AcademicDegree.builder()
                .teacher(teacher)
                .degree(dto.getDegree())
                .speciality(dto.getSpeciality())
                .dissertationTopic(dto.getDissertationTopic())
                .diploma(dto.getDiploma())
                .diplomaDate(dto.getDiplomaDate())
                .issuedBy(dto.getIssuedBy())
                .build();
        AcademicDegree saved = academicDegreeRepository.save(d);
        events.publishEvent(new ComplianceEvents.TeacherChanged(teacherId));
        return AcademicDegreeDto.fromEntity(saved);
    }

    public AcademicDegreeDto updateAcademicDegree(Long degreeId, AcademicDegreeDto dto) {
        AcademicDegree d = academicDegreeRepository.findById(degreeId)
                .orElseThrow(() -> new EntityNotFoundException("AcademicDegree not found: " + degreeId));
        d.setDegree(dto.getDegree());
        d.setSpeciality(dto.getSpeciality());
        d.setDissertationTopic(dto.getDissertationTopic());
        d.setDiploma(dto.getDiploma());
        d.setDiplomaDate(dto.getDiplomaDate());
        d.setIssuedBy(dto.getIssuedBy());
        AcademicDegree saved = academicDegreeRepository.save(d);
        Teacher t = d.getTeacher();
        if (t != null) events.publishEvent(new ComplianceEvents.TeacherChanged(t.getId()));
        return AcademicDegreeDto.fromEntity(saved);
    }

    public void deleteAcademicDegree(Long degreeId) {
        AcademicDegree d = academicDegreeRepository.findById(degreeId)
                .orElseThrow(() -> new EntityNotFoundException("AcademicDegree not found: " + degreeId));
        Teacher teacher = d.getTeacher();
        academicDegreeRepository.delete(d);
        if (teacher != null) events.publishEvent(new ComplianceEvents.TeacherChanged(teacher.getId()));
    }

    // ── Academic Titles ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ua.edu.teacherlicence.teacher.dto.AcademicTitleDto> findAcademicTitles(Long teacherId) {
        return academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacherId)
                .stream().map(ua.edu.teacherlicence.teacher.dto.AcademicTitleDto::fromEntity).toList();
    }

    public ua.edu.teacherlicence.teacher.dto.AcademicTitleDto createAcademicTitle(
            Long teacherId, ua.edu.teacherlicence.teacher.dto.AcademicTitleDto dto) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found: " + teacherId));
        ua.edu.teacherlicence.teacher.model.AcademicTitle t = ua.edu.teacherlicence.teacher.model.AcademicTitle.builder()
                .teacher(teacher)
                .titleName(dto.getTitleName())
                .attestat(dto.getAttestat())
                .attestatDate(dto.getAttestatDate())
                .issuedBy(dto.getIssuedBy())
                .build();
        var saved = academicTitleRepository.save(t);
        events.publishEvent(new ComplianceEvents.TeacherChanged(teacherId));
        return ua.edu.teacherlicence.teacher.dto.AcademicTitleDto.fromEntity(saved);
    }

    public ua.edu.teacherlicence.teacher.dto.AcademicTitleDto updateAcademicTitle(
            Long titleId, ua.edu.teacherlicence.teacher.dto.AcademicTitleDto dto) {
        var t = academicTitleRepository.findById(titleId)
                .orElseThrow(() -> new EntityNotFoundException("AcademicTitle not found: " + titleId));
        t.setTitleName(dto.getTitleName());
        t.setAttestat(dto.getAttestat());
        t.setAttestatDate(dto.getAttestatDate());
        t.setIssuedBy(dto.getIssuedBy());
        var saved = academicTitleRepository.save(t);
        Teacher teacher = t.getTeacher();
        if (teacher != null) events.publishEvent(new ComplianceEvents.TeacherChanged(teacher.getId()));
        return ua.edu.teacherlicence.teacher.dto.AcademicTitleDto.fromEntity(saved);
    }

    public void deleteAcademicTitle(Long titleId) {
        var t = academicTitleRepository.findById(titleId)
                .orElseThrow(() -> new EntityNotFoundException("AcademicTitle not found: " + titleId));
        Teacher teacher = t.getTeacher();
        academicTitleRepository.delete(t);
        if (teacher != null) events.publishEvent(new ComplianceEvents.TeacherChanged(teacher.getId()));
    }

    // ── Military Educations ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MilitaryEducationDto> findMilitaryEducations(Long teacherId) {
        return militaryEducationRepository.findByTeacherIdOrderByGraduationYearDesc(teacherId)
                .stream().map(MilitaryEducationDto::fromEntity).toList();
    }

    public MilitaryEducationDto createMilitaryEducation(Long teacherId, MilitaryEducationDto dto) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found: " + teacherId));
        MilitaryEducation me = MilitaryEducation.builder()
                .teacher(teacher)
                .level(dto.getLevel() != null && !dto.getLevel().isBlank()
                        ? MilitaryEducationLevel.valueOf(dto.getLevel()) : null)
                .institution(dto.getInstitution())
                .speciality(dto.getSpeciality())
                .diploma(dto.getDiploma())
                .diplomaDate(dto.getDiplomaDate())
                .issuedBy(dto.getIssuedBy())
                .graduationYear(dto.getGraduationYear())
                .build();
        MilitaryEducation saved = militaryEducationRepository.save(me);
        syncPrimaryMilitaryEducation(teacher);
        events.publishEvent(new ComplianceEvents.MilitaryEducationChanged(teacherId));
        return MilitaryEducationDto.fromEntity(saved);
    }

    public MilitaryEducationDto updateMilitaryEducation(Long id, MilitaryEducationDto dto) {
        MilitaryEducation me = militaryEducationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MilitaryEducation not found: " + id));
        me.setLevel(dto.getLevel() != null && !dto.getLevel().isBlank()
                ? MilitaryEducationLevel.valueOf(dto.getLevel()) : null);
        me.setInstitution(dto.getInstitution());
        me.setSpeciality(dto.getSpeciality());
        me.setDiploma(dto.getDiploma());
        me.setDiplomaDate(dto.getDiplomaDate());
        me.setIssuedBy(dto.getIssuedBy());
        me.setGraduationYear(dto.getGraduationYear());
        MilitaryEducation saved = militaryEducationRepository.save(me);
        Teacher t = me.getTeacher();
        syncPrimaryMilitaryEducation(t);
        if (t != null) events.publishEvent(new ComplianceEvents.MilitaryEducationChanged(t.getId()));
        return MilitaryEducationDto.fromEntity(saved);
    }

    public void deleteMilitaryEducation(Long id) {
        MilitaryEducation me = militaryEducationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MilitaryEducation not found: " + id));
        Teacher teacher = me.getTeacher();
        militaryEducationRepository.delete(me);
        syncPrimaryMilitaryEducation(teacher);
        if (teacher != null) events.publishEvent(new ComplianceEvents.MilitaryEducationChanged(teacher.getId()));
    }

    /**
     * Sync Teacher's flat military education fields from the highest-level record.
     * STRATEGIC > OPERATIONAL. Keeps backward compat with rating calculation.
     */
    private void syncPrimaryMilitaryEducation(Teacher teacher) {
        List<MilitaryEducation> records = militaryEducationRepository
                .findByTeacherIdOrderByGraduationYearDesc(teacher.getId());
        if (!records.isEmpty()) {
            // Pick the highest level: STRATEGIC > OPERATIONAL
            MilitaryEducation best = records.stream()
                    .filter(r -> r.getLevel() != null)
                    .max((a, b) -> a.getLevel().compareTo(b.getLevel()))
                    .orElse(records.get(0));
            teacher.setMilitaryEducationLevel(best.getLevel());
            teacher.setMilitaryEducationDiploma(best.getDiploma());
            teacher.setMilitaryEducationDiplomaDate(best.getDiplomaDate());
            teacher.setMilitaryEducationIssuedBy(best.getIssuedBy());
        } else {
            teacher.setMilitaryEducationLevel(null);
            teacher.setMilitaryEducationDiploma(null);
            teacher.setMilitaryEducationDiplomaDate(null);
            teacher.setMilitaryEducationIssuedBy(null);
        }
        teacherRepository.save(teacher);
    }

    // ── Career Records ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CareerRecord> findCareerRecords(Long teacherId) {
        return careerRecordRepository.findByTeacherId(teacherId);
    }

    @Transactional(readOnly = true)
    public List<LanguageSkill> findLanguageSkills(Long teacherId) {
        return languageSkillRepository.findByTeacherId(teacherId);
    }

    @Transactional(readOnly = true)
    public CareerRecord findCareerRecordById(Long id) {
        return careerRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CareerRecord not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public LanguageSkill findLanguageSkillById(Long id) {
        return languageSkillRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("LanguageSkill not found with id: " + id));
    }

    // ── CareerRecord CRUD ────────────────────────────────────────────

    public CareerRecord createCareerRecord(Long teacherId, CareerRecord record) {
        Teacher teacher = findTeacherEntityById(teacherId);
        record.setTeacher(teacher);
        CareerRecord saved = careerRecordRepository.save(record);
        // CareerRecord — джерело пп.20 (поряд з practical_experience). Recompose
        // потрібен щоб AchievementComposer створив Achievement.PP_20 (якщо ще немає)
        // та оновив опис, інакше прогрес у Звіті відповідності та A3 у п.37 будуть false.
        safeRecompose(teacher);
        return saved;
    }

    public CareerRecord updateCareerRecord(Long id, CareerRecord update) {
        CareerRecord existing = careerRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CareerRecord not found with id: " + id));
        existing.setPosition(update.getPosition());
        existing.setOrganization(update.getOrganization());
        existing.setStartDate(update.getStartDate());
        existing.setEndDate(update.getEndDate());
        existing.setNotes(update.getNotes());
        CareerRecord saved = careerRecordRepository.save(existing);
        safeRecompose(existing.getTeacher());
        return saved;
    }

    public void deleteCareerRecord(Long id) {
        CareerRecord existing = careerRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CareerRecord not found with id: " + id));
        Teacher teacher = existing.getTeacher();
        careerRecordRepository.deleteById(id);
        if (teacher != null) safeRecompose(teacher);
    }

    /**
     * Тригерить recompose досягнень викладача без переривання основного потоку,
     * якщо composer кине виключення (напр. коли AI недоступний).
     */
    private void safeRecompose(Teacher teacher) {
        if (teacher == null) return;
        try {
            achievementComposer.recomposeForTeacher(teacher);
        } catch (Exception e) {
            log.warn("recomposeForTeacher failed for teacher {}: {}", teacher.getId(), e.getMessage());
        }
    }

    // ── LanguageSkill CRUD ─────────────────────────────────────────

    public LanguageSkill createLanguageSkill(Long teacherId, LanguageSkill skill) {
        Teacher teacher = findTeacherEntityById(teacherId);
        skill.setTeacher(teacher);
        LanguageSkill saved = languageSkillRepository.save(skill);
        events.publishEvent(new ComplianceEvents.LanguageChanged(teacherId));
        return saved;
    }

    public LanguageSkill updateLanguageSkill(Long id, LanguageSkill update) {
        LanguageSkill existing = languageSkillRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("LanguageSkill not found with id: " + id));
        existing.setLanguage(update.getLanguage());
        existing.setLevel(update.getLevel());
        existing.setCertificateDetails(update.getCertificateDetails());
        existing.setCertificateNumber(update.getCertificateNumber());
        existing.setCertificateDate(update.getCertificateDate());
        existing.setCertificateOrganization(update.getCertificateOrganization());
        LanguageSkill saved = languageSkillRepository.save(existing);
        if (existing.getTeacher() != null) {
            events.publishEvent(new ComplianceEvents.LanguageChanged(existing.getTeacher().getId()));
        }
        return saved;
    }

    public void deleteLanguageSkill(Long id) {
        LanguageSkill ls = languageSkillRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("LanguageSkill not found with id: " + id));
        Long tid = ls.getTeacher() != null ? ls.getTeacher().getId() : null;
        languageSkillRepository.deleteById(id);
        if (tid != null) events.publishEvent(new ComplianceEvents.LanguageChanged(tid));
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Teacher findTeacherEntityById(Long id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found with id: " + id));
    }

    private Teacher mapRequestToEntity(TeacherCreateRequest request, Teacher teacher) {
        teacher.setLastName(request.getLastName());
        teacher.setFirstName(request.getFirstName());
        teacher.setPatronymic(request.getPatronymic());
        teacher.setDateOfBirth(request.getDateOfBirth());
        teacher.setMilitaryRank(request.getMilitaryRank());
        // Поле position видалено зі схеми — посада редагується через staff_positions.
        teacher.setEmploymentType(request.getEmploymentType());
        teacher.setExperienceStartDate(request.getExperienceStartDate());
        teacher.setUniversity(request.getUniversity());
        teacher.setUniversitySpeciality(request.getUniversitySpeciality());
        teacher.setUniversityDiploma(request.getUniversityDiploma());
        teacher.setUniversityGraduationYear(request.getUniversityGraduationYear());
        teacher.setUniversityDiplomaDate(request.getUniversityDiplomaDate());
        teacher.setCombatVeteranStatus(request.isCombatVeteranStatus());
        teacher.setCombatVeteranDoc(request.getCombatVeteranDoc());
        teacher.setCombatVeteranDocDate(request.getCombatVeteranDocDate());
        teacher.setCombatVeteranDocIssuedBy(request.getCombatVeteranDocIssuedBy());
        teacher.setCombatExperienceDates(request.getCombatExperienceDates());
        // Військова освіта
        if (request.getMilitaryEducationLevel() != null && !request.getMilitaryEducationLevel().isBlank()) {
            teacher.setMilitaryEducationLevel(
                    ua.edu.teacherlicence.teacher.model.MilitaryEducationLevel.valueOf(request.getMilitaryEducationLevel()));
        } else {
            teacher.setMilitaryEducationLevel(null);
        }
        teacher.setMilitaryEducationDiploma(request.getMilitaryEducationDiploma());
        teacher.setMilitaryEducationDiplomaDate(request.getMilitaryEducationDiplomaDate());
        teacher.setMilitaryEducationIssuedBy(request.getMilitaryEducationIssuedBy());
        teacher.setOrcidId(normalizeOrcid(request.getOrcidId()));
        teacher.setGoogleScholarUrl(request.getGoogleScholarUrl());
        teacher.setScopusId(normalizeScopusId(request.getScopusId()));
        teacher.setWosId(normalizeWosId(request.getWosId()));
        teacher.setEmail(request.getEmail());
        teacher.setPhone(request.getPhone());
        teacher.setPhotoUrl(request.getPhotoUrl());
        teacher.setNotes(request.getNotes());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Department not found with id: " + request.getDepartmentId()));
            teacher.setDepartment(department);
        } else {
            teacher.setDepartment(null);
        }

        return teacher;
    }

    // ── Normalize scientometric IDs (extract ID from full URL) ────

    public static String normalizeOrcid(String value) {
        if (value == null || value.isBlank()) return value;
        value = value.trim();
        // https://orcid.org/0000-0001-5592-5121 → 0000-0001-5592-5121
        if (value.contains("orcid.org/")) {
            value = value.substring(value.lastIndexOf("orcid.org/") + "orcid.org/".length());
        }
        return value.replaceAll("[^\\dX-]", "");
    }

    public static String normalizeScopusId(String value) {
        if (value == null || value.isBlank()) return value;
        value = value.trim();
        // https://www.scopus.com/authid/detail.uri?authorId=59133020400 → 59133020400
        if (value.contains("authorId=")) {
            String id = value.substring(value.indexOf("authorId=") + "authorId=".length());
            int amp = id.indexOf('&');
            return amp > 0 ? id.substring(0, amp) : id;
        }
        if (value.contains("scopus.com")) {
            // fallback: extract last numeric segment
            var m = java.util.regex.Pattern.compile("(\\d{5,})").matcher(value);
            if (m.find()) return m.group(1);
        }
        return value.replaceAll("[^\\d]", "");
    }

    public static String normalizeWosId(String value) {
        if (value == null || value.isBlank()) return value;
        value = value.trim();
        // https://www.webofscience.com/wos/author/record/C-1998-2019 → C-1998-2019
        if (value.contains("/record/")) {
            String id = value.substring(value.lastIndexOf("/record/") + "/record/".length());
            int slash = id.indexOf('/');
            return slash > 0 ? id.substring(0, slash) : id;
        }
        if (value.contains("webofscience.com") || value.contains("publons.com")) {
            // try to extract pattern like A-1234-5678
            var m = java.util.regex.Pattern.compile("([A-Z]-\\d{4}-\\d{4})").matcher(value);
            if (m.find()) return m.group(1);
        }
        return value;
    }
}
