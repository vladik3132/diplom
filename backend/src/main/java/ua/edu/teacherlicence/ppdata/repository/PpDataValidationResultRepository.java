package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ua.edu.teacherlicence.ppdata.model.PpDataValidationResult;

import java.util.List;

public interface PpDataValidationResultRepository extends JpaRepository<PpDataValidationResult, Long> {

    List<PpDataValidationResult> findBySessionIdOrderByPpNumber(String sessionId);

    /** Останні сесії для викладача (унікальні sessionId, найновіші перші) */
    @Query("""
        SELECT DISTINCT r.sessionId, MAX(r.validatedAt)
        FROM PpDataValidationResult r
        WHERE r.teacher.id = :teacherId
        GROUP BY r.sessionId
        ORDER BY MAX(r.validatedAt) DESC
        """)
    List<Object[]> findSessionsByTeacherId(Long teacherId);

    /** Всі результати конкретної сесії */
    List<PpDataValidationResult> findBySessionId(String sessionId);

    /** Останній результат для конкретного запису */
    @Query("""
        SELECT r FROM PpDataValidationResult r
        WHERE r.teacher.id = :teacherId AND r.entityType = :entityType AND r.entityId = :entityId
        ORDER BY r.validatedAt DESC
        LIMIT 1
        """)
    PpDataValidationResult findLatestForEntry(Long teacherId, String entityType, Long entityId);

    /** Всі результати валідації для викладача (найновіші першими) — для фільтрації в AchievementComposer */
    List<PpDataValidationResult> findByTeacherIdOrderByValidatedAtDesc(Long teacherId);
}
