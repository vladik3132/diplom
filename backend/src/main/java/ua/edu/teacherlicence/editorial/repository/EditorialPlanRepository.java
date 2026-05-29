package ua.edu.teacherlicence.editorial.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.editorial.model.EditorialPlan;

import java.util.List;

public interface EditorialPlanRepository extends JpaRepository<EditorialPlan, Long> {
    List<EditorialPlan> findByDepartmentId(Long departmentId);
    List<EditorialPlan> findByAcademicYear(String academicYear);
}
