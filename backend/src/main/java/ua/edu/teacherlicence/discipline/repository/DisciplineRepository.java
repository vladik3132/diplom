package ua.edu.teacherlicence.discipline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.discipline.model.Discipline;

import java.util.List;

@Repository
public interface DisciplineRepository extends JpaRepository<Discipline, Long> {

    List<Discipline> findByDepartmentId(Long departmentId);

    List<Discipline> findByEducationalProgramId(Long educationalProgramId);

    void deleteByEducationalProgramId(Long educationalProgramId);
}
