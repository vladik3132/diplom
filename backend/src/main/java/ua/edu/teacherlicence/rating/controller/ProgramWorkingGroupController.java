package ua.edu.teacherlicence.rating.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.rating.dto.ProgramWorkingGroupDto;
import ua.edu.teacherlicence.rating.model.ProgramWorkingGroup;
import ua.edu.teacherlicence.rating.model.WorkingGroupRole;
import ua.edu.teacherlicence.rating.repository.ProgramWorkingGroupRepository;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.util.List;

/**
 * CRUD для робочої групи ОПП.
 */
@RestController
@RequestMapping("/api/programs/{programId}/working-group")
@RequiredArgsConstructor
public class ProgramWorkingGroupController {

    private final ProgramWorkingGroupRepository repository;
    private final EducationalProgramRepository programRepository;
    private final TeacherRepository teacherRepository;

    @GetMapping
    public List<ProgramWorkingGroupDto> getMembers(@PathVariable Long programId) {
        return repository.findByProgramIdOrderByRoleAsc(programId)
                .stream().map(ProgramWorkingGroupDto::fromEntity).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramWorkingGroupDto addMember(@PathVariable Long programId,
                                             @RequestBody ProgramWorkingGroupDto dto) {
        if (repository.existsByProgramIdAndTeacherId(programId, dto.getTeacherId())) {
            throw new RuntimeException("Викладач вже є в робочій групі цієї ОПП");
        }
        EducationalProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("ОПП не знайдено: " + programId));
        Teacher teacher = teacherRepository.findById(dto.getTeacherId())
                .orElseThrow(() -> new RuntimeException("Викладача не знайдено: " + dto.getTeacherId()));
        ProgramWorkingGroup entity = ProgramWorkingGroup.builder()
                .program(program)
                .teacher(teacher)
                .role(dto.getRole() != null ? WorkingGroupRole.valueOf(dto.getRole()) : WorkingGroupRole.MEMBER)
                .orderNumber(dto.getOrderNumber())
                .orderDate(dto.getOrderDate())
                .build();
        return ProgramWorkingGroupDto.fromEntity(repository.save(entity));
    }

    @PutMapping("/{id}")
    public ProgramWorkingGroupDto updateMember(@PathVariable Long programId, @PathVariable Long id,
                                                @RequestBody ProgramWorkingGroupDto dto) {
        ProgramWorkingGroup entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        if (dto.getRole() != null) {
            entity.setRole(WorkingGroupRole.valueOf(dto.getRole()));
        }
        entity.setOrderNumber(dto.getOrderNumber());
        entity.setOrderDate(dto.getOrderDate());
        return ProgramWorkingGroupDto.fromEntity(repository.save(entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long programId, @PathVariable Long id) {
        repository.deleteById(id);
    }
}
