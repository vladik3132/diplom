package ua.edu.teacherlicence.rating.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Період рейтингування.
 * За замовчуванням: 21.06.попереднього року – 20.06.поточного року.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "rating_periods")
public class RatingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Назва періоду, напр. "2025-2026" */
    private String name;

    /** Дата початку рейтингового періоду */
    private LocalDate startDate;

    /** Дата завершення рейтингового періоду */
    private LocalDate endDate;

    /** Чи є цей період поточним (активним) */
    @Builder.Default
    private boolean active = false;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Створити стандартний рейтинговий період для даного навчального року.
     * Наприклад: для 2026 -> 21.06.2025 – 20.06.2026
     */
    public static RatingPeriod forYear(int year) {
        return RatingPeriod.builder()
                .name((year - 1) + "-" + year)
                .startDate(LocalDate.of(year - 1, 6, 21))
                .endDate(LocalDate.of(year, 6, 20))
                .active(true)
                .build();
    }
}
