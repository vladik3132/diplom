package ua.edu.teacherlicence.teacher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;

import java.util.List;

@Repository
public interface AcademicDegreeRepository extends JpaRepository<AcademicDegree, Long> {

    /** Хронологічно: спершу старіші (за датою диплома), null дата у кінці. */
    List<AcademicDegree> findByTeacherIdOrderByDiplomaDateAsc(Long teacherId);

    List<AcademicDegree> findByTeacherIdIn(List<Long> teacherIds);

    void deleteByTeacherId(Long teacherId);
}
