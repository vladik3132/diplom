package ua.edu.teacherlicence.ppdata.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ua.edu.teacherlicence.common.model.BaseAuditEntity;

/**
 * пп.13 — Проведення навчальних занять із спеціальних дисциплін
 * іноземною мовою в обсязі не менше 50 аудиторних годин.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "foreign_language_teaching")
public class ForeignLanguageTeaching extends BaseAuditEntity {

    private String disciplineName;

    private String language;

    private Integer hours;

    private String academicYear;

    private Integer semester;
}
