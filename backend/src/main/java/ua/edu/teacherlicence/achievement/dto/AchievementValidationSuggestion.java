package ua.edu.teacherlicence.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementValidationSuggestion {
    private Long achievementId;
    private String teacherName;
    private String achievementType;
    private int ppNumber;

    /** Поточна кількість (наприклад, 2 публікації з 5) */
    private int currentCount;
    /** Вимога підпункту (наприклад, 5) */
    private int requiredCount;
    /** Прогрес виконання 0.0–1.0 */
    private double progress;
    /** Чи виконано вимогу */
    private boolean fulfilled;

    private String reasoning;
    private String descriptionPreview;
}
