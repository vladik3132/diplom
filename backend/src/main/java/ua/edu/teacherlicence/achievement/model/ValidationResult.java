package ua.edu.teacherlicence.achievement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "validation_results")
public class ValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ідентифікатор сесії валідації (одна перевірка = один sessionId) */
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id")
    private Achievement achievement;

    /** Номер підпункту п.38 (1-20) */
    private int ppNumber;

    /** Чи виконано вимогу підпункту */
    @Builder.Default
    private boolean fulfilled = false;

    /** Поточна кількість (наприклад, 2 публікації) */
    private int currentCount;

    /** Вимога (наприклад, 5 публікацій) */
    private int requiredCount;

    /** Прогрес виконання (0.0 - 1.0) */
    private double progress;

    /** Обґрунтування AI */
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /** Короткий опис досягнення */
    @Column(columnDefinition = "TEXT")
    private String descriptionPreview;

    private LocalDateTime validatedAt;

    @PrePersist
    protected void onCreate() {
        validatedAt = LocalDateTime.now();
    }
}
