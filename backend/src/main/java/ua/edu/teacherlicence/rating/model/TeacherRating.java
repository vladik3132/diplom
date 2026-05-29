package ua.edu.teacherlicence.rating.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDateTime;

/**
 * Один рядок рейтингу: один критерій одного викладача за один період.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "teacher_ratings",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"period_id", "teacher_id", "criterion"}
       ))
public class TeacherRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private RatingPeriod period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    /** Критерій рейтингу */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RatingCriterion criterion;

    /** Кількість одиниць (напр. 3 статті Scopus) */
    @Builder.Default
    private int count = 0;

    /** Бали за критерій (= count * criterion.points) */
    @Builder.Default
    private int score = 0;

    /** Деталі розрахунку (для аудиту) */
    @Column(columnDefinition = "TEXT")
    private String details;

    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        calculatedAt = LocalDateTime.now();
    }
}
