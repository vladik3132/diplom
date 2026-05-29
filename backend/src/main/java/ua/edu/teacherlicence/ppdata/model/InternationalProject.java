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
 * пп.10 — Участь у міжнародних наукових та/або освітніх проектах,
 * залучення до міжнародної експертизи.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "international_project")
public class InternationalProject extends BaseAuditEntity {

    @Column(columnDefinition = "TEXT")
    private String projectName;

    @Enumerated(EnumType.STRING)
    private InternationalProgram program;

    private String role;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    @Column(columnDefinition = "TEXT")
    private String description;
}
