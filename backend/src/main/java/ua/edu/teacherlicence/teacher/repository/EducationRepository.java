package ua.edu.teacherlicence.teacher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.teacher.model.Education;

import java.util.List;

@Repository
public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findByTeacherIdOrderByGraduationYearDesc(Long teacherId);

    List<Education> findByTeacherIdIn(List<Long> teacherIds);

    void deleteByTeacherId(Long teacherId);
}
