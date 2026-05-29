package ua.edu.teacherlicence.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationSessionDto {
    private String sessionId;
    private LocalDateTime validatedAt;
    private int totalCount;
    private int fulfilledCount;
    private int notFulfilledCount;
}
