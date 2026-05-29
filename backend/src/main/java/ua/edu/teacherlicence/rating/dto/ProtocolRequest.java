package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolRequest {
    private String institutionName;
    private String protocolDate;   // ДД.ММ.РРРР
    private String protocolNumber;
    private String orderNumber;
    private String orderDate;      // ДД.ММ РРРР
    private List<CommissionMember> commissionMembers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommissionMember {
        private String role;  // CHAIR, VICE_CHAIR, SECRETARY, MEMBER
        private String rank;  // полковник, підполковник, майор ...
        private String name;  // Ім'я ПРІЗВИЩЕ
        private String shortName; // Прізвище І.П. (для тексту)
    }
}
