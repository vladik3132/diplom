package ua.edu.teacherlicence.achievement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ua.edu.teacherlicence.achievement.model.ValidationResult;

import java.util.List;

public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> {

    List<ValidationResult> findByTeacherIdOrderByValidatedAtDesc(Long teacherId);

    /** Останні результати валідації для викладача (тільки з найновішої сесії) */
    @Query("SELECT vr FROM ValidationResult vr WHERE vr.teacher.id = :teacherId " +
           "AND vr.sessionId = (SELECT vr2.sessionId FROM ValidationResult vr2 " +
           "WHERE vr2.teacher.id = :teacherId ORDER BY vr2.validatedAt DESC LIMIT 1)")
    List<ValidationResult> findLatestByTeacherId(Long teacherId);

    /** Всі сесії валідації для викладача (по одному запису на сесію) */
    @Query("SELECT vr.sessionId, MAX(vr.validatedAt) FROM ValidationResult vr " +
           "WHERE vr.teacher.id = :teacherId GROUP BY vr.sessionId ORDER BY MAX(vr.validatedAt) DESC")
    List<Object[]> findSessionsByTeacherId(Long teacherId);

    List<ValidationResult> findBySessionId(String sessionId);

    void deleteByTeacherId(Long teacherId);
}
