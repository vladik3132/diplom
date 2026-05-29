package ua.edu.teacherlicence.opp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.opp.model.EducationalProgram;

import java.util.List;

@Repository
public interface EducationalProgramRepository extends JpaRepository<EducationalProgram, Long> {

    List<EducationalProgram> findByDepartmentId(Long departmentId);

    List<EducationalProgram> findByEnrollmentYear(Integer year);

    List<EducationalProgram> findBySpecialtyContainingIgnoreCase(String specialty);
}
