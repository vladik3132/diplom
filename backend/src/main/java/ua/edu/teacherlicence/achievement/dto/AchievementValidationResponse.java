package ua.edu.teacherlicence.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementValidationResponse {
    private int totalValidated;
    private int fulfilledCount;
    private int notFulfilledCount;
    private String sessionId;
    private List<AchievementValidationSuggestion> suggestions;
}
