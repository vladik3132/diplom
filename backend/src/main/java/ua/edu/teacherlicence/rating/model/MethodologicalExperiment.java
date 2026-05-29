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
 * Методичний (педагогічний) експеримент.
 * Рейтинг: 20 балів.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "methodological_experiments")
public class MethodologicalExperiment extends BaseAuditEntity {

    /** Назва експерименту */
    @Column(columnDefinition = "TEXT")
    private String title;

    /** Опис */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Дата проведення / завершення */
    private LocalDate date;

    /** Номер наказу */
    private String orderNumber;

    /** Дата наказу */
    private LocalDate orderDate;

    /** Примітки */
    @Column(columnDefinition = "TEXT")
    private String notes;
}
