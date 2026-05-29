package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.InternationalProject;

import java.util.List;

@Repository
public interface InternationalProjectRepository extends JpaRepository<InternationalProject, Long> {

    List<InternationalProject> findByTeacherId(Long teacherId);
    List<InternationalProject> findByTeacherIdIn(List<Long> teacherIds);
}
