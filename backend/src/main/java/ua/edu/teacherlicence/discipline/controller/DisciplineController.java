package ua.edu.teacherlicence.discipline.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.discipline.model.Discipline;
import ua.edu.teacherlicence.discipline.model.DisciplineDocument;
import ua.edu.teacherlicence.discipline.model.DocumentStatus;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.service.DisciplineService;
import ua.edu.teacherlicence.notification.service.ChangeNotificationService;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/disciplines")
@RequiredArgsConstructor
public class DisciplineController {

    private final DisciplineService disciplineService;
    private final CurrentUserProvider currentUser;
    private final ChangeNotificationService changeNotificationService;

    /** All authenticated users can view disciplines. */
    @GetMapping
    public List<Discipline> getAll(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long programId) {
        if (programId != null) return disciplineService.findDisciplinesByProgram(programId);
        if (departmentId != null) return disciplineService.findDisciplinesByDepartment(departmentId);
        return disciplineService.findAllDisciplines();
    }

    @GetMapping("/{id}")
    public Discipline getById(@PathVariable Long id) {
        return disciplineService.findDisciplineById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Discipline create(@RequestBody Discipline discipline) {
        return disciplineService.createDiscipline(discipline);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/{id}")
    public Discipline update(@PathVariable Long id, @RequestBody Discipline discipline) {
        return disciplineService.updateDiscipline(id, discipline);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        disciplineService.deleteDiscipline(id);
    }

    /**
     * Імпорт дисциплін з Excel навчального плану (НП).
     * Дисципліни прив'язуються до ОПП та кафедри за shortCode та номером кафедри.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("enrollmentYear") Integer enrollmentYear,
            @RequestParam(value = "educationLevel", required = false) String educationLevel,
            @RequestParam(value = "educationForm", required = false) String educationForm) throws IOException {
        log.info("Importing disciplines from Excel: name='{}', size={}, year={}, level='{}', form='{}'",
                file.getOriginalFilename(), file.getSize(), enrollmentYear, educationLevel, educationForm);
        int count = disciplineService.importFromExcel(file.getInputStream(), enrollmentYear, educationLevel, educationForm);
        return Map.of("imported", count, "message", "Дисципліни імпортовано успішно");
    }

    @GetMapping("/teacher/{teacherId}")
    public List<TeacherDiscipline> getTeacherDisciplines(@PathVariable Long teacherId) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkTeacherAccess(teacherId);
        return disciplineService.findTeacherDisciplines(teacherId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherDiscipline assign(@RequestBody TeacherDiscipline td) {
        TeacherDiscipline saved = disciplineService.assignTeacherDiscipline(td);
        if (saved.getTeacher() != null) {
            String discName = saved.getDiscipline() != null ? saved.getDiscipline().getName() : "дисципліна";
            changeNotificationService.notifyDataChanged(
                    saved.getTeacher(), currentUser.getCurrentUser(),
                    "призначено", "Дисципліни", discName);
        }
        return saved;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @DeleteMapping("/assign/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAssignment(@PathVariable Long id) {
        TeacherDiscipline td = disciplineService.findTeacherDisciplineById(id);
        if (td != null && td.getTeacher() != null) {
            String discName = td.getDiscipline() != null ? td.getDiscipline().getName() : "дисципліна";
            changeNotificationService.notifyDataChanged(
                    td.getTeacher(), currentUser.getCurrentUser(),
                    "знято призначення", "Дисципліни", discName);
        }
        disciplineService.removeTeacherDiscipline(id);
    }

    @GetMapping("/{disciplineId}/teachers")
    public List<TeacherDiscipline> getDisciplineTeachers(@PathVariable Long disciplineId) {
        return disciplineService.findTeachersByDiscipline(disciplineId);
    }

    @GetMapping("/{disciplineId}/documents")
    public List<DisciplineDocument> getDocuments(@PathVariable Long disciplineId) {
        return disciplineService.findDocumentsByDiscipline(disciplineId);
    }

    @GetMapping("/documents/teacher/{teacherId}")
    public List<DisciplineDocument> getDocumentsByTeacher(@PathVariable Long teacherId) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkTeacherAccess(teacherId);
        return disciplineService.findDocumentsByTeacher(teacherId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/documents/status/{status}")
    public List<DisciplineDocument> getDocumentsByStatus(@PathVariable DocumentStatus status) {
        return disciplineService.findDocumentsByStatus(status);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DisciplineDocument createDocument(@RequestBody DisciplineDocument doc) {
        DisciplineDocument saved = disciplineService.createDocument(doc);
        if (saved.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(
                    saved.getTeacher(), currentUser.getCurrentUser(),
                    "додано", "Документи дисциплін", saved.getType() != null ? saved.getType() : "документ");
        }
        return saved;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/documents/{id}")
    public DisciplineDocument updateDocument(@PathVariable Long id, @RequestBody DisciplineDocument doc) {
        DisciplineDocument updated = disciplineService.updateDocument(id, doc);
        if (updated.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(
                    updated.getTeacher(), currentUser.getCurrentUser(),
                    "оновлено", "Документи дисциплін", updated.getType() != null ? updated.getType() : "документ");
        }
        return updated;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable Long id) {
        disciplineService.deleteDocument(id);
    }
}
