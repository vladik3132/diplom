package ua.edu.teacherlicence.rating.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ua.edu.teacherlicence.common.model.BaseAuditEntity;

import java.time.LocalDate;

/**
 * Міжнародне стажування. Окрема активність викладача, відмінна від:
 *  - {@link AcademicMobility} — академічна мобільність (студент/викладач їде у іншу країну на тривалий час)
 *  - {@code QualificationImprovement} — курси підвищення кваліфікації (можуть бути будь-де,
 *    рейтингуються лише за credits)
 *
 * <p>Рейтинговий критерій: {@code FOREIGN_INTERNSHIP} (10 балів).
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "foreign_internships")
public class ForeignInternship extends BaseAuditEntity {

    /** Назва програми стажування / напряму. */
    @Column(columnDefinition = "TEXT")
    private String programName;

    /** Навчальний/науковий заклад приймаючої сторони. */
    @Column(columnDefinition = "TEXT")
    private String institution;

    /** Країна стажування. */
    private String country;

    /** Дата початку. */
    private LocalDate dateFrom;

    /** Дата завершення. */
    private LocalDate dateTo;

    /** Опис діяльності. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Примітки. */
    @Column(columnDefinition = "TEXT")
    private String notes;
}
