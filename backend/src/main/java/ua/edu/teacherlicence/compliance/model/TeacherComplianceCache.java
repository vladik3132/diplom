package ua.edu.teacherlicence.compliance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;

import java.time.LocalDateTime;

/**
 * Персистентний кеш compliance-статусу (п.38) + відповідності кафедрі (п.37 → dept) для викладача.
 *
 * Оновлюється event-driven (див. ComplianceCacheService) при змінах:
 *  - Achievement, Publication, Education, Qualification, Language, MilitaryEducation
 *  - Teacher (departmentId, employmentType, experienceStartDate)
 *
 * Читається hot-path endpoint-ами замість on-the-fly обчислення (2400+ SQL → 1 SQL).
 */
@Entity
@Table(name = "teacher_compliance_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherComplianceCache {

    @Id
    @Column(name = "teacher_id")
    private Long teacherId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ComplianceStatus status;

    @Column(length = 500)
    private String exemptionReason;

    @Column(nullable = false)
    private Integer uniqueTypeCount;

    @Column(nullable = false)
    private Integer achievementCount;

    @Column(nullable = false)
    private Integer publicationsCount;

    @Column(nullable = false)
    private Integer relevantPublicationsCount;

    /** JSON array of AchievementType names (PP_1_PUBLICATIONS, …). */
    @Column(columnDefinition = "TEXT")
    private String achievementTypes;

    /** JSON array of short strings describing missing pieces. */
    @Column(columnDefinition = "TEXT")
    private String missingInfo;

    @Column(nullable = false)
    private boolean diplomaMatchesDepartment;

    @Column(nullable = false)
    private boolean degreeMatchesDepartment;

    @Column(nullable = false)
    private boolean qualificationMatchesDepartment;

    /** Хоча б одне вчене звання відповідає напряму кафедри (AI). */
    @Column(name = "title_matches_department", nullable = false)
    private boolean titleMatchesDepartment;

    /** Коли саме AI був залучений до обчислення п.37 до кафедри (null — без AI). */
    private LocalDateTime aiLastComputedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
