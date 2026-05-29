package ua.edu.teacherlicence.ppdata.model;

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
 * пп.9 — Робота у складі експертної ради з питань проведення експертизи
 * дисертацій МОН, НАЗЯВО, Акредитаційної комісії тощо.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "expert_council")
public class ExpertCouncil extends BaseAuditEntity {

    private String councilName;

    @Enumerated(EnumType.STRING)
    private ExpertCouncilType type;

    private String role;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    private String orderNumber;
}
