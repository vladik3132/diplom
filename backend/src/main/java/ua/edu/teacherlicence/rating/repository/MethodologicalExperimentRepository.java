package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.rating.model.MethodologicalExperiment;

import java.util.List;

public interface MethodologicalExperimentRepository extends JpaRepository<MethodologicalExperiment, Long> {
    List<MethodologicalExperiment> findByTeacherIdOrderByDateDesc(Long teacherId);
    void deleteByTeacherId(Long teacherId);
}
