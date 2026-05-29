package ua.edu.teacherlicence.ppdata.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDateTime;

/**
 * Результат ШІ-валідації окремого запису ppData.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ppdata_validation_results")
public class PpDataValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ідентифікатор сесії (одна перевірка = один sessionId) */
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    /** Тип сутності (scientific-supervision, attestation-activity, ...) */
    private String entityType;

    /** ID запису в відповідній таблиці */
    private Long entityId;

    /** Номер підпункту п.38 */
    private int ppNumber;

    /** Мітка підпункту */
    private String ppLabel;

    /** Короткий опис запису */
    @Column(columnDefinition = "TEXT")
    private String entitySummary;

    /** OK / WARNING / ERROR */
    private String status;

    /** Обґрунтування ШІ */
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    private LocalDateTime validatedAt;

    @PrePersist
    protected void onCreate() {
        validatedAt = LocalDateTime.now();
    }
}
