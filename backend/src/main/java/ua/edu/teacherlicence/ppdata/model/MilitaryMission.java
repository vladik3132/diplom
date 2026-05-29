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
 * пп.17+18 (merged) — Участь у міжнародних операціях з підтримання миру
 * під егідою ООН та у міжнародних військових навчаннях НАТО.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "military_mission")
public class MilitaryMission extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    private MissionType missionType;

    private String missionName;

    private String country;

    private LocalDate dateFrom;

    private LocalDate dateTo;
}
