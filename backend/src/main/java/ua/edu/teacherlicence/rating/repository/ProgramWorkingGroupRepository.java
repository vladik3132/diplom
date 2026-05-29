package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.rating.model.ProgramWorkingGroup;

import java.util.List;

public interface ProgramWorkingGroupRepository extends JpaRepository<ProgramWorkingGroup, Long> {
    List<ProgramWorkingGroup> findByProgramIdOrderByRoleAsc(Long programId);
    List<ProgramWorkingGroup> findByTeacherId(Long teacherId);
    void deleteByProgramId(Long programId);
    void deleteByTeacherId(Long teacherId);
    boolean existsByProgramIdAndTeacherId(Long programId, Long teacherId);
}
