package ua.edu.teacherlicence.teacher.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.util.List;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /* ──────── Повертає Teacher з уже завантаженим department — уникає N+1 у TeacherDto.fromEntity. ──────── */

    @Override
    @EntityGraph(attributePaths = {"department", "department.faculty"})
    List<Teacher> findAll();

    @Override
    @EntityGraph(attributePaths = {"department", "department.faculty"})
    Page<Teacher> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"department", "department.faculty"})
    List<Teacher> findByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"department", "department.faculty"})
    Page<Teacher> findByDepartmentId(Long departmentId, Pageable pageable);

    List<Teacher> findByDepartmentIdIn(List<Long> departmentIds);

    @EntityGraph(attributePaths = {"department", "department.faculty"})
    List<Teacher> findByLastNameContainingIgnoreCase(String lastName);

    @EntityGraph(attributePaths = {"department", "department.faculty"})
    Page<Teacher> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "department.faculty"})
    Page<Teacher> findByDepartmentIdAndLastNameContainingIgnoreCase(Long departmentId, String lastName, Pageable pageable);

    List<Teacher> findByLastNameIgnoreCaseAndFirstNameIgnoreCase(String lastName, String firstName);

    /**
     * Викладачі, чия кафедра бере участь у рейтингу
     * (department.ratingExcluded = false). Викладачі без department —
     * НЕ повертаються (вони і так не НПП).
     */
    @EntityGraph(attributePaths = {"department", "department.faculty"})
    List<Teacher> findByDepartmentRatingExcludedFalse();
}
