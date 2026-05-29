package ua.edu.teacherlicence.qualification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.file.model.EntityTypeConstants;
import ua.edu.teacherlicence.file.service.FileAttachmentService;
import ua.edu.teacherlicence.notification.service.ChangeNotificationService;
import ua.edu.teacherlicence.notification.service.FieldDiff;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.service.QualificationService;
import ua.edu.teacherlicence.user.model.User;

import java.nio.file.AccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/qualifications")
@RequiredArgsConstructor
public class QualificationController {

    private final QualificationService qualificationService;
    private final CurrentUserProvider currentUser;
    private final ChangeNotificationService changeNotificationService;
    private final FileAttachmentService fileAttachmentService;

    @GetMapping
    public List<QualificationImprovement> getAll(@RequestParam(required = false) Long teacherId) throws AccessDeniedException {
        if (currentUser.isTeacher()) {
            User user = currentUser.getCurrentUser();
            return user.getTeacherId() != null
                    ? qualificationService.findByTeacherId(user.getTeacherId())
                    : List.of();
        }
        if (teacherId != null) {
            if (!currentUser.isAdmin()) currentUser.checkTeacherAccess(teacherId);
            return qualificationService.findByTeacherId(teacherId);
        }
        return qualificationService.findAll();
    }

    @GetMapping("/{id}")
    public QualificationImprovement getById(@PathVariable Long id) throws AccessDeniedException {
        QualificationImprovement qi = qualificationService.findById(id);
        if (qi.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(qi.getTeacher().getId());
        }
        return qi;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QualificationImprovement create(@RequestBody QualificationImprovement qi) throws AccessDeniedException {
        if (currentUser.isTeacher()) {
            if (qi.getTeacher() == null) {
                qi.setTeacher(currentUser.getCurrentTeacher());
            } else {
                currentUser.checkTeacherAccess(qi.getTeacher().getId());
            }
        }
        QualificationImprovement saved = qualificationService.create(qi);
        if (saved.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(
                    saved.getTeacher(), currentUser.getCurrentUser(),
                    "додано", "Підвищення кваліфікації",
                    saved.getTitle() != null ? saved.getTitle() : "нове підвищення кваліфікації");
        }
        return saved;
    }

    @PutMapping("/{id}")
    public QualificationImprovement update(@PathVariable Long id, @RequestBody QualificationImprovement qi) throws AccessDeniedException {
        QualificationImprovement existing = qualificationService.findById(id);
        if (existing.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(existing.getTeacher().getId());
        }
        String oldTitle = existing.getTitle();
        String oldOrg = existing.getOrganization();
        Integer oldHours = existing.getHours();

        QualificationImprovement updated = qualificationService.update(id, qi);
        if (updated.getTeacher() != null) {
            FieldDiff diff = new FieldDiff()
                    .compare("Назва", oldTitle, updated.getTitle())
                    .compare("Організація", oldOrg, updated.getOrganization())
                    .compare("Години", oldHours, updated.getHours());
            String base = updated.getTitle() != null ? updated.getTitle() : "підвищення кваліфікації";
            String details = diff.hasChanges() ? base + " | " + diff.build() : base;
            changeNotificationService.notifyDataChanged(
                    updated.getTeacher(), currentUser.getCurrentUser(),
                    "оновлено", "Підвищення кваліфікації", details);
        }
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) throws AccessDeniedException {
        QualificationImprovement existing = qualificationService.findById(id);
        if (existing.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(existing.getTeacher().getId());
        }
        if (existing.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(
                    existing.getTeacher(), currentUser.getCurrentUser(),
                    "видалено", "Підвищення кваліфікації",
                    existing.getTitle() != null ? existing.getTitle() : "підвищення кваліфікації");
        }
        fileAttachmentService.deleteByEntity(EntityTypeConstants.QUALIFICATION, id);
        qualificationService.delete(id);
    }
}
