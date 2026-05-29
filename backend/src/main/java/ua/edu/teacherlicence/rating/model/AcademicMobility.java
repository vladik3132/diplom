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
 * Академічна мобільність.
 * Рейтинг: 10 балів.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "academic_mobilities")
public class AcademicMobility extends BaseAuditEntity {

    /** Назва програми мобільності */
    @Column(columnDefinition = "TEXT")
    private String programName;

    /** Навчальний заклад */
    @Column(columnDefinition = "TEXT")
    private String institution;

    /** Країна */
    private String country;

    /** Дата початку */
    private LocalDate dateFrom;

    /** Дата завершення */
    private LocalDate dateTo;

    /** Опис діяльності */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Примітки */
    @Column(columnDefinition = "TEXT")
    private String notes;
}
