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
 * пп.19 — Діяльність за спеціальністю у формі участі
 * у професійних та/або громадських об'єднаннях.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "professional_association")
public class ProfessionalAssociation extends BaseAuditEntity {

    private String organizationName;

    private String role;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    private String certificateNumber;
}
