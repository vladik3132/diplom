package ua.edu.teacherlicence.opp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.opp.dto.DisciplineStaffingDto;
import ua.edu.teacherlicence.opp.dto.ProgramStaffStats;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.service.EducationalProgramService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/educational-programs")
@RequiredArgsConstructor
public class EducationalProgramController {

    private final EducationalProgramService service;

    @GetMapping
    public List<EducationalProgram> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public EducationalProgram getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-department/{departmentId}")
    public List<EducationalProgram> getByDepartment(@PathVariable Long departmentId) {
        return service.findByDepartmentId(departmentId);
    }

    @GetMapping("/{id}/staff-stats")
    public ProgramStaffStats getStaffStats(@PathVariable Long id) {
        return service.getStaffStats(id);
    }

    @GetMapping("/{id}/discipline-staffing")
    public Map<Long, DisciplineStaffingDto> getDisciplineStaffing(@PathVariable Long id) {
        return service.getDisciplineStaffing(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EducationalProgram create(@RequestBody Map<String, Object> body) {
        EducationalProgram program = mapToEntity(body);
        Long departmentId = body.get("departmentId") != null
                ? ((Number) body.get("departmentId")).longValue()
                : null;
        return service.create(program, departmentId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public EducationalProgram update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        EducationalProgram program = mapToEntity(body);
        Long departmentId = body.containsKey("departmentId")
                ? (body.get("departmentId") != null ? ((Number) body.get("departmentId")).longValue() : null)
                : null;
        return service.update(id, program, departmentId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    private EducationalProgram mapToEntity(Map<String, Object> body) {
        return EducationalProgram.builder()
                .name((String) body.get("name"))
                .shortCode((String) body.get("shortCode"))
                .educationLevel((String) body.get("educationLevel"))
                .educationForm((String) body.get("educationForm"))
                .degree((String) body.get("degree"))
                .educationalQualification((String) body.get("educationalQualification"))
                .fieldOfKnowledge((String) body.get("fieldOfKnowledge"))
                .professionalQualification((String) body.get("professionalQualification"))
                .specialty((String) body.get("specialty"))
                .credits(body.get("credits") != null ? ((Number) body.get("credits")).intValue() : null)
                .specialization((String) body.get("specialization"))
                .duration((String) body.get("duration"))
                .enrollmentYear(body.get("enrollmentYear") != null ? ((Number) body.get("enrollmentYear")).intValue() : null)
                .build();
    }
}
