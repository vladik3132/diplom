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
 * пп.7 — Участь в атестації наукових кадрів:
 * офіційний опонент, рецензент, голова разової спецради,
 * або член постійної спеціалізованої вченої ради.
 *
 * <p>Для разових ролей ({@link AttestationRole#OPPONENT}, {@link AttestationRole#REVIEWER},
 * {@link AttestationRole#CHAIR}) використовується {@link #defenseDate} — конкретна дата захисту.
 *
 * <p>Для {@link AttestationRole#COUNCIL_MEMBER} (постійна спецрада) використовуються
 * {@link #dateFrom} / {@link #dateTo} — період членства.
 * Поле {@link #defenseDate} опціонально — конкретний захист, де член ради був присутній.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "attestation_activity")
public class AttestationActivity extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    private AttestationRole role;

    /** Назва спеціалізованої вченої ради (напр. "Спецрада Д 26.062.01"). */
    private String councilName;

    /** ПІБ здобувача наукового ступеня. */
    private String studentName;

    /** Дата захисту (для разових ролей; опційно для COUNCIL_MEMBER). */
    private LocalDate defenseDate;

    /** Початок членства у постійній спецраді (для COUNCIL_MEMBER). */
    private LocalDate dateFrom;

    /** Кінець членства у постійній спецраді (для COUNCIL_MEMBER). */
    private LocalDate dateTo;
}
