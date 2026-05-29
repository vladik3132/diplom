package ua.edu.teacherlicence.ppdata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PpDataValidationResponse {
    private String sessionId;
    private int totalChecked;
    private int validCount;
    private int warningCount;
    private int errorCount;
    private LocalDateTime validatedAt;
    private List<PpDataValidationItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PpDataValidationItem {
        private String entityType;
        private int ppNumber;
        private String ppLabel;
        private Long entityId;
        private String entitySummary;
        private String status;       // OK, WARNING, ERROR
        private String reasoning;
    }
}
