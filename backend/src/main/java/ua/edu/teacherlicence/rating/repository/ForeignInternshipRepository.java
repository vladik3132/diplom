package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.rating.model.ForeignInternship;

import java.util.List;

public interface ForeignInternshipRepository extends JpaRepository<ForeignInternship, Long> {
    List<ForeignInternship> findByTeacherIdOrderByDateFromDesc(Long teacherId);
    List<ForeignInternship> findByTeacherId(Long teacherId);
    void deleteByTeacherId(Long teacherId);
}
