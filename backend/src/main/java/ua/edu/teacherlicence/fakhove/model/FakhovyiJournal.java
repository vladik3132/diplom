package ua.edu.teacherlicence.fakhove.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Фахове видання з переліку МОН України.
 * Імпортується з Excel-файлу (реєстр фахових видань).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fakhovi_journals")
public class FakhovyiJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** НазваВидання */
    @Column(columnDefinition = "TEXT")
    private String name;

    /** ЗасновникСпівзасновники */
    @Column(columnDefinition = "TEXT")
    private String founders;

    /** КодСпеціальності */
    @Column(columnDefinition = "TEXT")
    private String specialtyCodes;

    /** ДатаВключення */
    private LocalDate inclusionDate;

    /** Категорія (A/B) */
    @Enumerated(EnumType.STRING)
    private JournalCategory category;

    /** Нормалізована назва (lowercase, без лапок) для пошуку */
    @Column(columnDefinition = "TEXT")
    private String nameNormalized;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
