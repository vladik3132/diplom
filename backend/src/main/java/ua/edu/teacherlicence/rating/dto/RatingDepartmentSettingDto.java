package ua.edu.teacherlicence.rating.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для UI налаштування "Які кафедри беруть участь у рейтингу".
 * Адмін бачить всі кафедри і може помітити будь-яку як виключену.
 */
@Data
@Builder
public class RatingDepartmentSettingDto {
    private Long departmentId;
    private String number;
    private String name;
    private String facultyName;
    /** true — кафедра виключена з рейтингу (за замовч. false). */
    private boolean ratingExcluded;
}
