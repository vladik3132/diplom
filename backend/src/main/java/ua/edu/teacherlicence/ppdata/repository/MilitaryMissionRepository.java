package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.MilitaryMission;

import java.util.List;

@Repository
public interface MilitaryMissionRepository extends JpaRepository<MilitaryMission, Long> {

    List<MilitaryMission> findByTeacherId(Long teacherId);
    List<MilitaryMission> findByTeacherIdIn(List<Long> teacherIds);
}
