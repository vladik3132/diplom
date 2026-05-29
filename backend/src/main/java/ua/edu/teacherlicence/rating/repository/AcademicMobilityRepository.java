package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.rating.model.AcademicMobility;

import java.util.List;

public interface AcademicMobilityRepository extends JpaRepository<AcademicMobility, Long> {
    List<AcademicMobility> findByTeacherIdOrderByDateFromDesc(Long teacherId);
    void deleteByTeacherId(Long teacherId);
}
