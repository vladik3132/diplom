package ua.edu.teacherlicence.rating.dto;

import lombok.Data;

import java.util.List;

/**
 * Bulk оновлення налаштувань рейтингу: список ID кафедр що ВИКЛЮЧЕНІ з рейтингу.
 * Решта кафедр автоматично стають включеними.
 */
@Data
public class RatingDepartmentSettingsRequest {
    private List<Long> excludedDepartmentIds;
}
