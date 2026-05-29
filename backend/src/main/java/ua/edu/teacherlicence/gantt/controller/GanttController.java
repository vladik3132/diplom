package ua.edu.teacherlicence.gantt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.gantt.model.GanttEvent;
import ua.edu.teacherlicence.gantt.service.GanttService;
import ua.edu.teacherlicence.user.model.User;

import java.util.List;

@RestController
@RequestMapping("/api/gantt")
@RequiredArgsConstructor
public class GanttController {

    private final GanttService ganttService;
    private final CurrentUserProvider currentUser;

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping
    public List<GanttEvent> getAll(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String academicYear) {
        if (teacherId != null) return ganttService.findByTeacherId(teacherId);
        if (academicYear != null) return ganttService.findByAcademicYear(academicYear);
        return ganttService.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/{id}")
    public GanttEvent getById(@PathVariable Long id) {
        return ganttService.findById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GanttEvent create(@RequestBody GanttEvent event) {
        return ganttService.create(event);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/{id}")
    public GanttEvent update(@PathVariable Long id, @RequestBody GanttEvent event) {
        return ganttService.update(id, event);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ganttService.delete(id);
    }
}
