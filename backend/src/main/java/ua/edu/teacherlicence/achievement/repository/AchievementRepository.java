package ua.edu.teacherlicence.achievement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;

import java.util.List;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    List<Achievement> findByTeacherId(Long teacherId);

    List<Achievement> findByTeacherIdAndAchievementType(Long teacherId, AchievementType type);

    List<Achievement> findByTeacherIdIn(List<Long> teacherIds);
}
