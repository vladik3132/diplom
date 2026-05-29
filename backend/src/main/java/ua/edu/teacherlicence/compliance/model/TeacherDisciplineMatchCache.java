package ua.edu.teacherlicence.compliance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Кеш п.36+п.37 для пари (teacher × discipline).
 * Один викладач може викладати кілька дисциплін, одна дисципліна — мати кількох викладачів.
 *
 * Оновлюється при:
 *  - TeacherDiscipline assign/remove (INSERT/DELETE рядка)
 *  - Education CUD викладача (AI перерахунок)
 *  - Achievement/Publication CUD викладача (Блок А3/А4/Б)
 *  - Discipline update (перерахунок усіх teachers цієї дисципліни)
 */
@Entity
@Table(
    name = "teacher_discipline_match_cache",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tdmc_teacher_discipline",
        columnNames = {"teacher_id", "discipline_id"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDisciplineMatchCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "discipline_id", nullable = false)
    private Long disciplineId;

    // Блок А1-А4
    @Column(nullable = false) private boolean hasMatchingDiploma;         // A1 (AI)
    @Column(nullable = false) private boolean hasMatchingDegree;          // A2 (AI)
    @Column(nullable = false) private boolean hasPracticalExperience;     // A3 (PP_20)
    @Column(nullable = false) private boolean hasDissertationSupervision; // A4 (PP_6)
    @Column(nullable = false) private boolean point37aCompliant;          // A = OR(A1..A4)

    // Блок Б
    @Column(nullable = false) private Integer qualifiedPublicationsCount;
    @Column(nullable = false) private boolean point37bCompliant;          // ≥ 5

    // Загальна
    @Column(nullable = false) private boolean point37Compliant;           // A && B
    @Column(nullable = false) private boolean point36Compliant;           // ≥ 4 типи п.38
    @Column(nullable = false) private boolean fullyCompliant;             // p36 && p37

    private LocalDateTime aiLastComputedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }
}
