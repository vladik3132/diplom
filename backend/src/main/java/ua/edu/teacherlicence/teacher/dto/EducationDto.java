package ua.edu.teacherlicence.teacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.Education;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationDto {

    private Long id;
    private Long teacherId;
    private String institution;
    private String city;
    private String degree;
    private String speciality;
    private String qualification;
    private Integer graduationYear;
    private String diploma;
    private LocalDate diplomaDate;

    public static EducationDto fromEntity(Education e) {
        return EducationDto.builder()
                .id(e.getId())
                .teacherId(e.getTeacher().getId())
                .institution(e.getInstitution())
                .city(e.getCity())
                .degree(e.getDegree())
                .speciality(e.getSpeciality())
                .qualification(e.getQualification())
                .graduationYear(e.getGraduationYear())
                .diploma(e.getDiploma())
                .diplomaDate(e.getDiplomaDate())
                .build();
    }
}
