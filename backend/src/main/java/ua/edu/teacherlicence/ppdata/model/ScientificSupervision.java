package ua.edu.teacherlicence.ppdata.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ua.edu.teacherlicence.common.model.BaseAuditEntity;

import java.time.LocalDate;

/**
 * пп.6 — Наукове керівництво (консультування) здобувача,
 * який одержав документ про присудження наукового ступеня.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "scientific_supervision")
public class ScientificSupervision extends BaseAuditEntity {

    private String studentName;

    @Column(columnDefinition = "TEXT")
    private String topic;

    private LocalDate defenseDate;

    @Enumerated(EnumType.STRING)
    private DegreeType degreeType;

    private String diplomaNumber;
}
