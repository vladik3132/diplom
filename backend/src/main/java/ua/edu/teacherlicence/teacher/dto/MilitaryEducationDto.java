package ua.edu.teacherlicence.teacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.MilitaryEducation;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilitaryEducationDto {

    private Long id;
    private Long teacherId;
    private String level;          // OPERATIONAL | STRATEGIC
    private String institution;
    private String speciality;
    private String diploma;
    private LocalDate diplomaDate;
    private String issuedBy;
    private Integer graduationYear;

    public static MilitaryEducationDto fromEntity(MilitaryEducation e) {
        return MilitaryEducationDto.builder()
                .id(e.getId())
                .teacherId(e.getTeacher().getId())
                .level(e.getLevel() != null ? e.getLevel().name() : null)
                .institution(e.getInstitution())
                .speciality(e.getSpeciality())
                .diploma(e.getDiploma())
                .diplomaDate(e.getDiplomaDate())
                .issuedBy(e.getIssuedBy())
                .graduationYear(e.getGraduationYear())
                .build();
    }
}
