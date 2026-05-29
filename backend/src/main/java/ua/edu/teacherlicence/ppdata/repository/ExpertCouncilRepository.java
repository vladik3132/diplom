package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.ExpertCouncil;

import java.util.List;

@Repository
public interface ExpertCouncilRepository extends JpaRepository<ExpertCouncil, Long> {

    List<ExpertCouncil> findByTeacherId(Long teacherId);
    List<ExpertCouncil> findByTeacherIdIn(List<Long> teacherIds);
}
