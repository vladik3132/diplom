package ua.edu.teacherlicence.teacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicDegreeDto {

    private Long id;
    private Long teacherId;
    private String degree;
    private String speciality;
    private String dissertationTopic;
    private String diploma;
    private LocalDate diplomaDate;
    private String issuedBy;

    public static AcademicDegreeDto fromEntity(AcademicDegree e) {
        return AcademicDegreeDto.builder()
                .id(e.getId())
                .teacherId(e.getTeacher() != null ? e.getTeacher().getId() : null)
                .degree(e.getDegree())
                .speciality(e.getSpeciality())
                .dissertationTopic(e.getDissertationTopic())
                .diploma(e.getDiploma())
                .diplomaDate(e.getDiplomaDate())
                .issuedBy(e.getIssuedBy())
                .build();
    }
}
