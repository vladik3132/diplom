package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.PracticalExperience;

import java.util.List;

@Repository
public interface PracticalExperienceRepository extends JpaRepository<PracticalExperience, Long> {

    List<PracticalExperience> findByTeacherId(Long teacherId);
    List<PracticalExperience> findByTeacherIdIn(List<Long> teacherIds);
}
