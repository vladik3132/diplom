package ua.edu.teacherlicence.teacher.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.file.model.EntityTypeConstants;
import ua.edu.teacherlicence.file.service.FileAttachmentService;
import ua.edu.teacherlicence.notification.service.ChangeNotificationService;
import ua.edu.teacherlicence.notification.service.FieldDiff;
import ua.edu.teacherlicence.teacher.dto.AcademicDegreeDto;
import ua.edu.teacherlicence.teacher.dto.AcademicTitleDto;
import ua.edu.teacherlicence.teacher.dto.EducationDto;
import ua.edu.teacherlicence.teacher.dto.MilitaryEducationDto;
import ua.edu.teacherlicence.teacher.dto.TeacherCreateRequest;
import ua.edu.teacherlicence.teacher.dto.TeacherDto;
import ua.edu.teacherlicence.teacher.model.CareerRecord;
import ua.edu.teacherlicence.teacher.model.Education;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.service.TeacherService;
import ua.edu.teacherlicence.user.model.User;

import java.nio.file.AccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final CurrentUserProvider currentUser;
    private final ChangeNotificationService changeNotificationService;
    private final FileAttachmentService fileAttachmentService;

    /**
     * ADMIN — all teachers. HEAD — own department. TEACHER — only self.
     *
     * Backward-compatible: без параметра {@code page} — повертає весь список (legacy),
     * з параметром — повертає {@code Page<TeacherDto>} (рекомендовано).
     */
    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "20") Integer size) throws AccessDeniedException {

        if (currentUser.isTeacher()) {
            User user = currentUser.getCurrentUser();
            if (user.getTeacherId() == null) return ResponseEntity.ok(List.of());
            return ResponseEntity.ok(List.of(teacherService.findById(user.getTeacherId())));
        }

        Long scopedDept = departmentId;
        if (currentUser.isHead()) {
            scopedDept = departmentId != null ? departmentId : currentUser.getCurrentDepartmentId();
            if (scopedDept == null) return ResponseEntity.ok(List.of());
            currentUser.checkDepartmentAccess(scopedDept);
        }

        // Paged response — нова рекомендована поведінка
        if (page != null) {
            int safeSize = Math.min(Math.max(size, 1), 200);
            var pageable = org.springframework.data.domain.PageRequest.of(
                    Math.max(page, 0), safeSize,
                    org.springframework.data.domain.Sort.by("lastName").ascending());
            return ResponseEntity.ok(teacherService.findAllPaged(pageable, search, scopedDept));
        }

        // Legacy (unpaged) — щоб не ламати існуючих клієнтів
        final Long effectiveDept = scopedDept;
        List<TeacherDto> teachers;
        if (effectiveDept != null && search != null && !search.isBlank()) {
            teachers = teacherService.search(search).stream()
                    .filter(t -> t.getDepartmentId() != null && t.getDepartmentId().equals(effectiveDept))
                    .toList();
        } else if (effectiveDept != null) {
            teachers = teacherService.findByDepartmentId(effectiveDept);
        } else if (search != null && !search.isBlank()) {
            teachers = teacherService.search(search);
        } else {
            teachers = teacherService.findAll();
        }
        return ResponseEntity.ok(teachers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherDto> getById(@PathVariable Long id) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        return ResponseEntity.ok(teacherService.findById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TeacherDto> create(@Valid @RequestBody TeacherCreateRequest request) {
        TeacherDto created = teacherService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * ADMIN — any teacher. HEAD — own department. TEACHER — only self.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TeacherDto> update(
            @PathVariable Long id,
            @Valid @RequestBody TeacherCreateRequest request) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        // Зберігаємо старі значення для diff
        TeacherDto old = teacherService.findById(id);
        TeacherDto updated = teacherService.update(id, request);
        FieldDiff diff = new FieldDiff()
                // Diff по effectivePosition (primary зі staff_positions). Legacy поле position
                // ще зберігається в БД, але джерело правди — effectivePosition.
                .compare("Посада", old.getEffectivePosition(), updated.getEffectivePosition())
                .compare("Тип зайнятості", old.getEmploymentType(), updated.getEmploymentType())
                .compare("Військове звання", old.getMilitaryRank(), updated.getMilitaryRank())
                .compare("Кафедра", old.getDepartmentName(), updated.getDepartmentName())
                .compare("Email", old.getEmail(), updated.getEmail())
                .compare("Телефон", old.getPhone(), updated.getPhone())
                .compare("ORCID", old.getOrcidId(), updated.getOrcidId())
                .compare("Scopus ID", old.getScopusId(), updated.getScopusId());
        String base = updated.getLastName() + " " + updated.getFirstName();
        String details = diff.hasChanges() ? base + " | " + diff.build() : base;
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "оновлено", "Профіль викладача", details);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fileAttachmentService.deleteAllFilesForTeacher(id);
        teacherService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Educations CRUD ─────────────────────────────────────────────

    @GetMapping("/{id}/educations")
    public ResponseEntity<List<EducationDto>> getEducations(@PathVariable Long id) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        return ResponseEntity.ok(teacherService.findEducations(id));
    }

    @PostMapping("/{id}/educations")
    public ResponseEntity<EducationDto> createEducation(
            @PathVariable Long id, @RequestBody EducationDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        EducationDto saved = teacherService.createEducation(id, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "додано", "Освіта",
                saved.getInstitution() != null ? saved.getInstitution() : "новий запис");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/educations/{eduId}")
    public ResponseEntity<EducationDto> updateEducation(
            @PathVariable Long id, @PathVariable Long eduId,
            @RequestBody EducationDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        EducationDto updated = teacherService.updateEducation(eduId, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "оновлено", "Освіта",
                updated.getInstitution() != null ? updated.getInstitution() : "запис освіти");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/educations/{eduId}")
    public ResponseEntity<Void> deleteEducation(
            @PathVariable Long id, @PathVariable Long eduId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "видалено", "Освіта", "запис освіти");
        teacherService.deleteEducation(eduId);
        return ResponseEntity.noContent().build();
    }

    // ── Academic Degrees CRUD ─────────────────────────────────────────

    @GetMapping("/{id}/academic-degrees")
    public ResponseEntity<List<AcademicDegreeDto>> getAcademicDegrees(@PathVariable Long id) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        return ResponseEntity.ok(teacherService.findAcademicDegrees(id));
    }

    @PostMapping("/{id}/academic-degrees")
    public ResponseEntity<AcademicDegreeDto> createAcademicDegree(
            @PathVariable Long id, @RequestBody AcademicDegreeDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        AcademicDegreeDto saved = teacherService.createAcademicDegree(id, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "додано", "Науковий ступінь",
                saved.getDegree() != null ? saved.getDegree() : "новий запис");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/academic-degrees/{degreeId}")
    public ResponseEntity<AcademicDegreeDto> updateAcademicDegree(
            @PathVariable Long id, @PathVariable Long degreeId,
            @RequestBody AcademicDegreeDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        AcademicDegreeDto updated = teacherService.updateAcademicDegree(degreeId, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "оновлено", "Науковий ступінь",
                updated.getDegree() != null ? updated.getDegree() : "запис ступеня");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/academic-degrees/{degreeId}")
    public ResponseEntity<Void> deleteAcademicDegree(
            @PathVariable Long id, @PathVariable Long degreeId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "видалено", "Науковий ступінь", "запис ступеня");
        teacherService.deleteAcademicDegree(degreeId);
        return ResponseEntity.noContent().build();
    }

    // ── Academic Titles CRUD ──────────────────────────────────────────

    @GetMapping("/{id}/academic-titles")
    public ResponseEntity<List<AcademicTitleDto>> getAcademicTitles(@PathVariable Long id) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        return ResponseEntity.ok(teacherService.findAcademicTitles(id));
    }

    @PostMapping("/{id}/academic-titles")
    public ResponseEntity<AcademicTitleDto> createAcademicTitle(
            @PathVariable Long id, @RequestBody AcademicTitleDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        AcademicTitleDto saved = teacherService.createAcademicTitle(id, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "додано", "Вчене звання",
                saved.getTitleName() != null ? saved.getTitleName() : "новий запис");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/academic-titles/{titleId}")
    public ResponseEntity<AcademicTitleDto> updateAcademicTitle(
            @PathVariable Long id, @PathVariable Long titleId,
            @RequestBody AcademicTitleDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        AcademicTitleDto updated = teacherService.updateAcademicTitle(titleId, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "оновлено", "Вчене звання",
                updated.getTitleName() != null ? updated.getTitleName() : "запис звання");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/academic-titles/{titleId}")
    public ResponseEntity<Void> deleteAcademicTitle(
            @PathVariable Long id, @PathVariable Long titleId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "видалено", "Вчене звання", "запис звання");
        teacherService.deleteAcademicTitle(titleId);
        return ResponseEntity.noContent().build();
    }

    // ── Military Educations CRUD ──────────────────────────────────────

    @GetMapping("/{id}/military-educations")
    public ResponseEntity<List<MilitaryEducationDto>> getMilitaryEducations(@PathVariable Long id) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        return ResponseEntity.ok(teacherService.findMilitaryEducations(id));
    }

    @PostMapping("/{id}/military-educations")
    public ResponseEntity<MilitaryEducationDto> createMilitaryEducation(
            @PathVariable Long id, @RequestBody MilitaryEducationDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        MilitaryEducationDto saved = teacherService.createMilitaryEducation(id, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "додано", "Військова освіта",
                saved.getInstitution() != null ? saved.getInstitution() : "новий запис");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/military-educations/{meId}")
    public ResponseEntity<MilitaryEducationDto> updateMilitaryEducation(
            @PathVariable Long id, @PathVariable Long meId,
            @RequestBody MilitaryEducationDto dto) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        MilitaryEducationDto updated = teacherService.updateMilitaryEducation(meId, dto);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "оновлено", "Військова освіта",
                updated.getInstitution() != null ? updated.getInstitution() : "запис ВО");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/military-educations/{meId}")
    public ResponseEntity<Void> deleteMilitaryEducation(
            @PathVariable Long id, @PathVariable Long meId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "видалено", "Військова освіта", "запис ВО");
        teacherService.deleteMilitaryEducation(meId);
        return ResponseEntity.noContent().build();
    }

    // ── Career Records CRUD ──────────────────────────────────────────

    @GetMapping("/{id}/career")
    public ResponseEntity<List<CareerRecord>> getCareerRecords(@PathVariable Long id) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        return ResponseEntity.ok(teacherService.findCareerRecords(id));
    }

    @PostMapping("/{id}/career")
    public ResponseEntity<CareerRecord> createCareerRecord(
            @PathVariable Long id, @RequestBody CareerRecord record) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        CareerRecord saved = teacherService.createCareerRecord(id, record);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "додано", "Кар'єра",
                saved.getPosition() != null ? saved.getPosition() : "новий запис");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/career/{recId}")
    public ResponseEntity<CareerRecord> updateCareerRecord(
            @PathVariable Long id, @PathVariable Long recId,
            @RequestBody CareerRecord record) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        CareerRecord existing = teacherService.findCareerRecordById(recId);
        FieldDiff diff = new FieldDiff()
                .compare("Посада", existing.getPosition(), record.getPosition())
                .compare("Організація", existing.getOrganization(), record.getOrganization());
        CareerRecord updated = teacherService.updateCareerRecord(recId, record);
        String base = updated.getPosition() != null ? updated.getPosition() : "запис кар'єри";
        String details = diff.hasChanges() ? base + " | " + diff.build() : base;
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(), "оновлено", "Кар'єра", details);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/career/{recId}")
    public ResponseEntity<Void> deleteCareerRecord(
            @PathVariable Long id, @PathVariable Long recId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "видалено", "Кар'єра", "запис кар'єри");
        teacherService.deleteCareerRecord(recId);
        return ResponseEntity.noContent().build();
    }

    // ── Language Skills CRUD ──────────────────────────────────────────

    @GetMapping("/{id}/languages")
    public ResponseEntity<List<LanguageSkill>> getLanguageSkills(@PathVariable Long id) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        return ResponseEntity.ok(teacherService.findLanguageSkills(id));
    }

    @PostMapping("/{id}/languages")
    public ResponseEntity<LanguageSkill> createLanguageSkill(
            @PathVariable Long id, @RequestBody LanguageSkill skill) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        LanguageSkill saved = teacherService.createLanguageSkill(id, skill);
        String details = (saved.getLanguage() != null ? saved.getLanguage() : "мова") +
                (saved.getCertificateDetails() != null ? " — " + saved.getCertificateDetails() : "");
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "додано", "Мовні навички / сертифікати", details);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/languages/{recId}")
    public ResponseEntity<LanguageSkill> updateLanguageSkill(
            @PathVariable Long id, @PathVariable Long recId,
            @RequestBody LanguageSkill skill) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        LanguageSkill existing = teacherService.findLanguageSkillById(recId);
        FieldDiff diff = new FieldDiff()
                .compare("Мова", existing.getLanguage(), skill.getLanguage())
                .compare("Рівень", existing.getLevel(), skill.getLevel())
                .compare("Сертифікат", existing.getCertificateDetails(), skill.getCertificateDetails());
        LanguageSkill updated = teacherService.updateLanguageSkill(recId, skill);
        String base = (updated.getLanguage() != null ? updated.getLanguage() : "мова") +
                (updated.getCertificateDetails() != null ? " — " + updated.getCertificateDetails() : "");
        String details = diff.hasChanges() ? base + " | " + diff.build() : base;
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "оновлено", "Мовні навички / сертифікати", details);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/languages/{recId}")
    public ResponseEntity<Void> deleteLanguageSkill(
            @PathVariable Long id, @PathVariable Long recId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(id);
        changeNotificationService.notifyDataChanged(
                id, currentUser.getCurrentUser(),
                "видалено", "Мовні навички / сертифікати", "мовний сертифікат");
        fileAttachmentService.deleteByEntity(EntityTypeConstants.LANGUAGE_SKILL, recId);
        teacherService.deleteLanguageSkill(recId);
        return ResponseEntity.noContent().build();
    }
}
