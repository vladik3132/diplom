package ua.edu.teacherlicence.department.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.department.model.StaffPosition;

import java.util.List;

@Repository
public interface StaffPositionRepository extends JpaRepository<StaffPosition, Long> {

    List<StaffPosition> findByDepartmentIdOrderByOrderNumber(Long departmentId);

    List<StaffPosition> findByTeacherIsNullAndImportedTeacherNameIsNotNull();

    /** Усі штатні посади, на яких призначено цього викладача (можуть бути в різних кафедрах). */
    List<StaffPosition> findByTeacherId(Long teacherId);

    /**
     * Batch варіант для оптимізації N+1 — повертає всі штатні посади
     * для заданого списку викладачів (зазвичай використовується у DTO
     * batch-збагачуванні: knownTeacherIds → ефективна посада).
     */
    List<StaffPosition> findByTeacherIdIn(List<Long> teacherIds);
}
