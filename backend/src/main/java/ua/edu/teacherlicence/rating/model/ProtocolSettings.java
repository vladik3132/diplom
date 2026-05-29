package ua.edu.teacherlicence.rating.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Зберігає налаштування протоколу рейтингової комісії (назва закладу, склад комісії тощо).
 * Зберігається як єдиний запис із JSON-полем.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "protocol_settings")
public class ProtocolSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JSON-рядок з усіма полями: institutionName, commissionMembers[], orderNumber тощо */
    @Column(columnDefinition = "TEXT")
    private String settingsJson;
}
