package ua.edu.teacherlicence.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for achievement progress (deterministic only, no AI).
 * Used to display "currentCount/requiredCount" in the achievements table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementProgressDto {
    private Long achievementId;
    private String achievementType;
    private int ppNumber;
    private int currentCount;
    private int requiredCount;
    private double progress;   // 0.0 - 1.0
    private boolean fulfilled;
    private String label;      // e.g. "2/5 статей", "1/1 дисертація"
    /**
     * Детальне обґрунтування рішення: правила підрахунку, перелік зарахованих/відсіяних
     * записів, підсумок з БД та текст-fallback. Multiline (з '\n').
     * Будується у {@link ua.edu.teacherlicence.achievement.service.AchievementValidationService}.
     */
    private String reasoning;
}
