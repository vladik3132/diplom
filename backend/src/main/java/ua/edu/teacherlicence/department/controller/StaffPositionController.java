package ua.edu.teacherlicence.department.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.department.model.StaffPosition;
import ua.edu.teacherlicence.department.service.DepartmentService;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departments/{deptId}/staff-positions")
@RequiredArgsConstructor
public class StaffPositionController {

    private final DepartmentService departmentService;
    private final CurrentUserProvider currentUser;

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping
    public List<StaffPosition> getByDepartment(@PathVariable Long deptId) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(deptId);
        return departmentService.findStaffPositions(deptId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffPosition create(@PathVariable Long deptId, @RequestBody StaffPosition position) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(deptId);
        return departmentService.createStaffPosition(deptId, position);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/{id}")
    public StaffPosition update(@PathVariable Long deptId, @PathVariable Long id,
                                 @RequestBody StaffPosition position) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(deptId);
        return departmentService.updateStaffPosition(id, position);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long deptId, @PathVariable Long id) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(deptId);
        departmentService.deleteStaffPosition(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/relink")
    public Map<String, Object> relink(@PathVariable Long deptId) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(deptId);
        int linked = departmentService.relinkStaffPositions(deptId);
        return Map.of("linked", linked);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/batch")
    public List<StaffPosition> batchImport(@PathVariable Long deptId,
                                            @RequestBody List<StaffPosition> positions) throws AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(deptId);
        return departmentService.batchImportStaffPositions(deptId, positions);
    }
}
