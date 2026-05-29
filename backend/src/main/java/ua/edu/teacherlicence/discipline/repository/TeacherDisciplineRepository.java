package ua.edu.teacherlicence.discipline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;

import java.util.List;

@Repository
public interface TeacherDisciplineRepository extends JpaRepository<TeacherDiscipline, Long> {

    List<TeacherDiscipline> findByTeacherId(Long teacherId);

    List<TeacherDiscipline> findByTeacherIdIn(List<Long> teacherIds);

    List<TeacherDiscipline> findByDisciplineId(Long disciplineId);

    void deleteByDisciplineId(Long disciplineId);
}
