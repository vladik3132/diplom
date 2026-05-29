package ua.edu.teacherlicence.department.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.department.model.Department;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByFacultyId(Long facultyId);

    /** Кафедри, що беруть участь у рейтингу (rating_excluded = false). */
    List<Department> findByRatingExcludedFalse();
}
