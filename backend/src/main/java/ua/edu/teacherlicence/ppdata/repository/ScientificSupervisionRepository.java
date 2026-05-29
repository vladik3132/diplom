package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.ScientificSupervision;

import java.util.List;

@Repository
public interface ScientificSupervisionRepository extends JpaRepository<ScientificSupervision, Long> {

    List<ScientificSupervision> findByTeacherId(Long teacherId);
    List<ScientificSupervision> findByTeacherIdIn(List<Long> teacherIds);
}
