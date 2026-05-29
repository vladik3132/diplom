package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.rating.model.OpenLesson;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLessonDto {
    private Long id;
    private Long teacherId;
    private String topic;
    private LocalDate date;
    private String hostDepartment;
    private String lessonType;
    private String orderNumber;
    private LocalDate orderDate;
    private String notes;
    private String documentUrl;

    public static OpenLessonDto fromEntity(OpenLesson e) {
        return OpenLessonDto.builder()
                .id(e.getId())
                .teacherId(e.getTeacher() != null ? e.getTeacher().getId() : null)
                .topic(e.getTopic())
                .date(e.getDate())
                .hostDepartment(e.getHostDepartment())
                .lessonType(e.getLessonType())
                .orderNumber(e.getOrderNumber())
                .orderDate(e.getOrderDate())
                .notes(e.getNotes())
                .documentUrl(e.getDocumentUrl())
                .build();
    }
}
