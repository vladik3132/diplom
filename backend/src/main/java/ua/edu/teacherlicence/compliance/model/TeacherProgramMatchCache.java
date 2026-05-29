package ua.edu.teacherlicence.compliance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Кеш п.37 для пари (teacher × educational_program).
 * Викладач "пов'язаний" з ОПП опосередковано через дисципліни:
 *  match до ОПП = агрегат OR по всіх дисциплінах програми які викладач веде + публікації.
 *
 * Оновлюється при:
 *  - TeacherDiscipline (коли дисципліна належить цій програмі)
 *  - Education / Achievement / Publication CUD
 *  - EducationalProgram update
 */
@Entity
@Table(
    name = "teacher_program_match_cache",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tpmc_teacher_program",
        columnNames = {"teacher_id", "program_id"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherProgramMatchCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(nullable = false) private boolean hasMatchingDiploma;
    @Column(nullable = false) private boolean hasMatchingDegree;
    @Column(nullable = false) private boolean hasPracticalExperience;
    @Column(nullable = false) private boolean hasDissertationSupervision;
    @Column(nullable = false) private boolean point37aCompliant;

    @Column(nullable = false) private Integer qualifiedPublicationsCount;
    @Column(nullable = false) private boolean point37bCompliant;

    @Column(nullable = false) private boolean point37Compliant;

    private LocalDateTime aiLastComputedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }
}
