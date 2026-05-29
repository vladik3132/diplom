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
 * пп.11 — Наукове консультування підприємств, установ, організацій
 * не менше трьох років на підставі договору із ЗВО.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "scientific_consulting")
public class ScientificConsulting extends BaseAuditEntity {

    private String organizationName;

    private String contractNumber;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    private Integer yearsCount;
}
