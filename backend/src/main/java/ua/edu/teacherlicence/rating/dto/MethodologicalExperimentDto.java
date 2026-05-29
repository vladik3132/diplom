package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.rating.model.MethodologicalExperiment;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodologicalExperimentDto {
    private Long id;
    private Long teacherId;
    private String title;
    private String description;
    private LocalDate date;
    private String orderNumber;
    private LocalDate orderDate;
    private String notes;
    private String documentUrl;

    public static MethodologicalExperimentDto fromEntity(MethodologicalExperiment e) {
        return MethodologicalExperimentDto.builder()
                .id(e.getId())
                .teacherId(e.getTeacher() != null ? e.getTeacher().getId() : null)
                .title(e.getTitle())
                .description(e.getDescription())
                .date(e.getDate())
                .orderNumber(e.getOrderNumber())
                .orderDate(e.getOrderDate())
                .notes(e.getNotes())
                .documentUrl(e.getDocumentUrl())
                .build();
    }
}
