package ua.edu.teacherlicence.department.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.department.dto.DepartmentComplianceSummaryDto;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.service.DepartmentComplianceService;
import ua.edu.teacherlicence.department.service.DepartmentService;

import java.nio.file.AccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentComplianceService departmentComplianceService;
    private final CurrentUserProvider currentUser;

    /** ADMIN — all. HEAD/TEACHER — only own department. */
    @GetMapping
    public ResponseEntity<List<Department>> getAll(
            @RequestParam(required = false) Long facultyId) {
        if (currentUser.isAdmin()) {
            List<Department> departments = facultyId != null
                    ? departmentService.findDepartmentsByFacultyId(facultyId)
                    : departmentService.findAllDepartments();
            return ResponseEntity.ok(departments);
        }
        Long deptId = currentUser.getCurrentDepartmentId();
        if (deptId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(List.of(departmentService.findDepartmentById(deptId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/compliance-summary")
    public ResponseEntity<List<DepartmentComplianceSummaryDto>> getAllComplianceSummaries() {
        if (currentUser.isHead()) {
            Long deptId = currentUser.getCurrentDepartmentId();
            if (deptId == null) return ResponseEntity.ok(List.of());
            return ResponseEntity.ok(List.of(departmentComplianceService.getSummary(deptId)));
        }
        return ResponseEntity.ok(departmentComplianceService.getAllSummaries());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/{id}/compliance-summary")
    public ResponseEntity<DepartmentComplianceSummaryDto> getComplianceSummary(@PathVariable Long id) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(id);
        return ResponseEntity.ok(departmentComplianceService.getSummary(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Department> getById(@PathVariable Long id) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(id);
        return ResponseEntity.ok(departmentService.findDepartmentById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Department> create(@RequestBody Department department) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.createDepartment(department));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Department> update(@PathVariable Long id, @RequestBody Department department) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, department));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
