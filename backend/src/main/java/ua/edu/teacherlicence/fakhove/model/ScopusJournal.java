package ua.edu.teacherlicence.fakhove.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Журнал з переліку Scopus (Scopus Source List).
 * Імпортується з Excel-файлу.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "scopus_journals")
public class ScopusJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source Title */
    @Column(columnDefinition = "TEXT")
    private String name;

    /** Print ISSN */
    @Column(length = 30)
    private String issn;

    /** E-ISSN */
    @Column(length = 30)
    private String eissn;

    /** Нормалізована назва (lowercase, без лапок) для пошуку */
    @Column(columnDefinition = "TEXT")
    private String nameNormalized;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
