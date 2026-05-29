package ua.edu.teacherlicence.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchReclassifyRequest {

    private List<ReclassifyItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReclassifyItem {
        private Long achievementId;
        private String newType;
    }
}
