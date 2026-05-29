package ua.edu.teacherlicence.editorial.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.editorial.model.EditorialPlan;
import ua.edu.teacherlicence.editorial.model.EditorialPlanItem;
import ua.edu.teacherlicence.editorial.service.EditorialService;
import ua.edu.teacherlicence.notification.service.ChangeNotificationService;
import ua.edu.teacherlicence.user.model.User;

import java.nio.file.AccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/editorial")
@RequiredArgsConstructor
public class EditorialController {

    private final EditorialService editorialService;
    private final CurrentUserProvider currentUser;
    private final ChangeNotificationService changeNotificationService;

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/plans")
    public List<EditorialPlan> getPlans(@RequestParam(required = false) Long departmentId) {
        if (departmentId != null) return editorialService.findPlansByDepartment(departmentId);
        return editorialService.findAllPlans();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/plans/{id}")
    public EditorialPlan getPlan(@PathVariable Long id) {
        return editorialService.findPlanById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public EditorialPlan createPlan(@RequestBody EditorialPlan plan) {
        return editorialService.createPlan(plan);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/plans/{id}")
    public EditorialPlan updatePlan(@PathVariable Long id, @RequestBody EditorialPlan plan) {
        return editorialService.updatePlan(id, plan);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable Long id) {
        editorialService.deletePlan(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/plans/{planId}/items")
    public List<EditorialPlanItem> getItems(@PathVariable Long planId) {
        return editorialService.findItemsByPlan(planId);
    }

    /** Teacher can see own editorial items. */
    @GetMapping("/items/teacher/{teacherId}")
    public List<EditorialPlanItem> getItemsByTeacher(@PathVariable Long teacherId) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkTeacherAccess(teacherId);
        return editorialService.findItemsByTeacher(teacherId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public EditorialPlanItem createItem(@RequestBody EditorialPlanItem item) {
        EditorialPlanItem saved = editorialService.createItem(item);
        if (saved.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(
                    saved.getTeacher(), currentUser.getCurrentUser(),
                    "додано", "Редакційно-видавничий план",
                    saved.getTitle() != null ? saved.getTitle() : "новий запис");
        }
        return saved;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/items/{id}")
    public EditorialPlanItem updateItem(@PathVariable Long id, @RequestBody EditorialPlanItem item) {
        EditorialPlanItem updated = editorialService.updateItem(id, item);
        if (updated.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(
                    updated.getTeacher(), currentUser.getCurrentUser(),
                    "оновлено", "Редакційно-видавничий план",
                    updated.getTitle() != null ? updated.getTitle() : "запис плану");
        }
        return updated;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        editorialService.deleteItem(id);
    }
}
