package ua.edu.teacherlicence.teacher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;

import java.util.List;

@Repository
public interface LanguageSkillRepository extends JpaRepository<LanguageSkill, Long> {

    List<LanguageSkill> findByTeacherId(Long teacherId);

    List<LanguageSkill> findByTeacherIdIn(List<Long> teacherIds);
}
