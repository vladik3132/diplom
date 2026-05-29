package ua.edu.teacherlicence.teacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.AcademicTitle;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicTitleDto {

    private Long id;
    private Long teacherId;
    private String titleName;
    private String attestat;
    private LocalDate attestatDate;
    private String issuedBy;

    public static AcademicTitleDto fromEntity(AcademicTitle t) {
        return AcademicTitleDto.builder()
                .id(t.getId())
                .teacherId(t.getTeacher() != null ? t.getTeacher().getId() : null)
                .titleName(t.getTitleName())
                .attestat(t.getAttestat())
                .attestatDate(t.getAttestatDate())
                .issuedBy(t.getIssuedBy())
                .build();
    }
}
