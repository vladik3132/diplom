package ua.edu.teacherlicence.ppdata.model;

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
 * пп.20 — Досвід практичної роботи за спеціальністю
 * не менше п'яти років (крім педагогічної, науково-педагогічної, наукової діяльності).
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "practical_experience")
public class PracticalExperience extends BaseAuditEntity {

    private String organizationName;

    private String position;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    private Integer yearsCount;

    private String specialtyName;
}
