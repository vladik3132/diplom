package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.EditorialActivity;

import java.util.List;

@Repository
public interface EditorialActivityRepository extends JpaRepository<EditorialActivity, Long> {

    List<EditorialActivity> findByTeacherId(Long teacherId);
    List<EditorialActivity> findByTeacherIdIn(List<Long> teacherIds);
}
