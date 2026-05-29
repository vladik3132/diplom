package ua.edu.teacherlicence.achievement.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.achievement.dto.*;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.service.AchievementService;
import ua.edu.teacherlicence.achievement.service.AchievementValidationService;
import ua.edu.teacherlicence.achievement.service.ComplianceService;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.compliance.service.ComplianceCacheService;
import ua.edu.teacherlicence.user.model.User;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final ComplianceService complianceService;
    private final ComplianceCacheService complianceCache;
    private final AchievementValidationService validationService;
    private final CurrentUserProvider currentUser;

    // --- Achievements CRUD ---

    @GetMapping("/achievements")
    public List<Achievement> getAll(@RequestParam(required = false) Long teacherId) throws AccessDeniedException {
        if (currentUser.isTeacher()) {
            User user = currentUser.getCurrentUser();
            if (user.getTeacherId() == null) return List.of();
            return achievementService.findByTeacherId(user.getTeacherId());
        }
        if (teacherId != null) {
            if (!currentUser.isAdmin()) currentUser.checkTeacherAccess(teacherId);
            return achievementService.findByTeacherId(teacherId);
        }
        if (currentUser.isAdmin()) return achievementService.findAll();
        // HEAD without teacherId — return all for their department via findAll filtered below
        // For simplicity, return all (service doesn't have dept filter yet), HEAD sees broad view
        return achievementService.findAll();
    }

    @GetMapping("/teachers/{teacherId}/achievements")
    public List<Achievement> getByTeacher(@PathVariable Long teacherId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(teacherId);
        return achievementService.findByTeacherId(teacherId);
    }

    @GetMapping("/achievements/{id}")
    public Achievement getById(@PathVariable Long id) throws AccessDeniedException {
        Achievement ach = achievementService.findById(id);
        if (ach.getTeacher() != null) {
            currentUser.checkTeacherAccess(ach.getTeacher().getId());
        }
        return ach;
    }

    /** TEACHER can create achievements for self only (auto-linked). */
    @PostMapping("/achievements")
    public ResponseEntity<Achievement> create(@RequestBody Achievement achievement) throws AccessDeniedException {
        if (currentUser.isTeacher()) {
            if (achievement.getTeacher() == null) {
                achievement.setTeacher(currentUser.getCurrentTeacher());
            } else {
                currentUser.checkTeacherAccess(achievement.getTeacher().getId());
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(achievementService.save(achievement));
    }

    @PutMapping("/achievements/{id}")
    public Achievement update(@PathVariable Long id, @RequestBody Achievement achievement) throws AccessDeniedException {
        Achievement existing = achievementService.findById(id);
        if (existing.getTeacher() != null) {
            currentUser.checkTeacherAccess(existing.getTeacher().getId());
        }
        achievement.setId(id);
        return achievementService.save(achievement);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @DeleteMapping("/achievements/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        achievementService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- AI Validation ---

    @GetMapping("/achievements/ai/status")
    public ResponseEntity<Map<String, Boolean>> getAiStatus() {
        return ResponseEntity.ok(Map.of("available", validationService.isAiAvailable()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/achievements/validate")
    public ResponseEntity<AchievementValidationResponse> validateAchievements(
            @RequestBody AchievementValidationRequest request) {
        return ResponseEntity.ok(validationService.validate(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/achievements/batch-reclassify")
    public ResponseEntity<Map<String, Integer>> batchReclassify(
            @RequestBody BatchReclassifyRequest request) {
        int updated = validationService.applyReclassifications(request);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/achievements/validate-single")
    public ResponseEntity<AchievementValidationSuggestion> validateSingle(
            @RequestBody Map<String, String> request) {
        String description = request.get("description");
        String currentType = request.get("achievementType");
        if (description == null || currentType == null) {
            return ResponseEntity.badRequest().build();
        }
        AchievementValidationSuggestion suggestion =
                validationService.validateSingle(description, currentType);
        if (suggestion == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(suggestion);
    }

    // --- Achievement Progress (lightweight, no AI) ---

    @GetMapping("/teachers/{teacherId}/achievements/progress")
    public List<AchievementProgressDto> getProgress(@PathVariable Long teacherId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(teacherId);
        return validationService.getProgressForTeacher(teacherId);
    }

    // --- Validation History ---

    @GetMapping("/teachers/{teacherId}/validation/latest")
    public AchievementValidationResponse getLatestValidation(@PathVariable Long teacherId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(teacherId);
        return validationService.getLatestResults(teacherId);
    }

    @GetMapping("/teachers/{teacherId}/validation/history")
    public List<ValidationSessionDto> getValidationHistory(@PathVariable Long teacherId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(teacherId);
        return validationService.getValidationHistory(teacherId);
    }

    @GetMapping("/validation/session/{sessionId}")
    public AchievementValidationResponse getSessionResults(@PathVariable String sessionId) {
        return validationService.getSessionResults(sessionId);
    }

    // --- Compliance (п.38) ---

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/compliance")
    public List<ComplianceReportDto> getComplianceAll(
            @RequestParam(required = false) Long departmentId) throws AccessDeniedException {
        // Читання з cache — 1 SQL замість 2400+
        if (currentUser.isHead()) {
            Long deptId = departmentId != null ? departmentId : currentUser.getCurrentDepartmentId();
            if (deptId != null) currentUser.checkDepartmentAccess(deptId);
            return deptId != null ? complianceCache.getByDepartmentId(deptId) : List.of();
        }
        if (departmentId != null) return complianceCache.getByDepartmentId(departmentId);
        return complianceCache.getAll();
    }

    @GetMapping("/compliance/{teacherId}")
    public ComplianceReportDto getComplianceForTeacher(@PathVariable Long teacherId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(teacherId);
        // Cache → fallback до live compute якщо запис відсутній (lazy warmup)
        return complianceCache.getByTeacherId(teacherId)
                .orElseGet(() -> complianceCache.refreshTeacherSync(teacherId));
    }

    // --- Manual refresh (admin/head) ---

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/compliance/refresh")
    public Map<String, Object> refreshAll() {
        int n = complianceCache.refreshAll();
        return Map.of("refreshed", n);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/compliance/refresh/{teacherId}")
    public ComplianceReportDto refreshTeacher(@PathVariable Long teacherId) throws AccessDeniedException {
        currentUser.checkTeacherAccess(teacherId);
        return complianceCache.refreshTeacherSync(teacherId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/compliance/refresh/department/{departmentId}")
    public Map<String, Object> refreshDepartment(@PathVariable Long departmentId) throws AccessDeniedException {
        currentUser.checkDepartmentAccess(departmentId);
        int n = complianceCache.refreshByDepartmentId(departmentId);
        return Map.of("refreshed", n);
    }
}
