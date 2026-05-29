package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.OlympiadGuidance;

import java.util.List;

@Repository
public interface OlympiadGuidanceRepository extends JpaRepository<OlympiadGuidance, Long> {

    List<OlympiadGuidance> findByTeacherId(Long teacherId);
    List<OlympiadGuidance> findByTeacherIdIn(List<Long> teacherIds);
}
