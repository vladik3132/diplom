package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для одного запису, що підтвердив нарахування балу за певний критерій рейтингу.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionRecordDto {
    /** ID сутності-джерела */
    private Long id;
    /** Основний текст (назва публікації, організація ПК, тощо) */
    private String title;
    /** Додаткова інформація (рік, журнал, підтип...) */
    private String subtitle;
    /** Тип сутності: PUBLICATION, QUALIFICATION, SUPERVISION, тощо */
    private String entityType;
}
