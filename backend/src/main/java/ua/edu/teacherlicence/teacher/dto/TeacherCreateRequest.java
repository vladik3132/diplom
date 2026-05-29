package ua.edu.teacherlicence.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherCreateRequest {

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "First name is required")
    private String firstName;

    private String patronymic;
    private LocalDate dateOfBirth;
    private String militaryRank;
    // Поле position видалено — посада тепер редагується ТІЛЬКИ через staff_positions.
    private String employmentType;
    private Long departmentId;
    private LocalDate experienceStartDate;
    private String university;
    private String universitySpeciality;
    private String universityDiploma;
    private Integer universityGraduationYear;
    private LocalDate universityDiplomaDate;
    private boolean combatVeteranStatus;
    private String combatVeteranDoc;
    private LocalDate combatVeteranDocDate;
    private String combatVeteranDocIssuedBy;
    private String combatExperienceDates;
    // Військова освіта
    private String militaryEducationLevel;
    private String militaryEducationDiploma;
    private LocalDate militaryEducationDiplomaDate;
    private String militaryEducationIssuedBy;
    private String orcidId;
    private String googleScholarUrl;
    private String scopusId;
    private String wosId;
    private String email;
    private String phone;
    private String photoUrl;
    private String notes;
}
