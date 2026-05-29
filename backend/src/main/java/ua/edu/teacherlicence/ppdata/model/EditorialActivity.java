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
 * пп.8 — Виконання функцій наукового керівника або відповідального виконавця
 * наукової теми (проекту), або головного редактора/члена редакційної колегії/
 * експерта (рецензента) наукового видання.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "editorial_activity")
public class EditorialActivity extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    private EditorialRole role;

    @Column(columnDefinition = "TEXT")
    private String journalOrProjectName;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    @Column(columnDefinition = "TEXT")
    private String description;
}
