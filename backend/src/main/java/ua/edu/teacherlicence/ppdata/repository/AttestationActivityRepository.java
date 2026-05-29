package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.AttestationActivity;

import java.util.List;

@Repository
public interface AttestationActivityRepository extends JpaRepository<AttestationActivity, Long> {

    List<AttestationActivity> findByTeacherId(Long teacherId);
    List<AttestationActivity> findByTeacherIdIn(List<Long> teacherIds);
}
