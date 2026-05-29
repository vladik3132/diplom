package ua.edu.teacherlicence.teacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDto {

    private Long id;
    private String lastName;
    private String firstName;
    private String patronymic;
    private LocalDate dateOfBirth;
    private String militaryRank;
    // Legacy поле position видалено — використовуйте effectivePosition нижче.
    private String employmentType;
    private Long departmentId;
    private String departmentNumber;
    private String departmentName;
    private LocalDate experienceStartDate;
    private Integer experienceYears; // computed from experienceStartDate
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
    // ── Військова освіта ──
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Computed: назва primary (найвищого за рангом) наукового ступеня з academic_degrees.
     * Заповнюється у сервісі через batch-fetch — НЕ в {@link #fromEntity(Teacher)}.
     */
    private String academicDegree;
    /**
     * Computed: назва primary (найвищого за рангом) вченого звання з academic_titles.
     */
    private String academicTitle;
    /** Кількість записів у academic_degrees (для UI індикації "має ще ступені"). */
    private Integer academicDegreesCount;
    /** Кількість записів у academic_titles. */
    private Integer academicTitlesCount;

    /**
     * Computed: primary назва посади — з {@code staff_positions} (за seniority).
     * Заповнюється у сервісі через batch-fetch
     * ({@code TeacherPositionService.getEffectivePositions}).
     * {@code null} якщо у викладача немає штатних позицій.
     */
    private String effectivePosition;
    /**
     * Computed: сума {@code rate} усіх штатних позицій викладача.
     * Викладач може ділити одну штатну одиницю (0.5+0.5) — повертаємо суму.
     * {@code null} якщо штатних позицій немає.
     */
    private Double totalRate;
    /**
     * Computed: чи primary штатна позиція має прапорець bootstrapped=true
     * (запис створено автоматично при міграції — потребує перевірки адміна).
     */
    private Boolean bootstrappedPosition;

    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (lastName != null) sb.append(lastName);
        if (firstName != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(firstName);
        }
        if (patronymic != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(patronymic);
        }
        return sb.toString();
    }

    public static TeacherDto fromEntity(Teacher t) {
        if (t == null) return null;

        TeacherDtoBuilder builder = TeacherDto.builder()
                .id(t.getId())
                .lastName(t.getLastName())
                .firstName(t.getFirstName())
                .patronymic(t.getPatronymic())
                .dateOfBirth(t.getDateOfBirth())
                .militaryRank(t.getMilitaryRank())
                .employmentType(t.getEmploymentType())
                .experienceStartDate(t.getExperienceStartDate())
                .experienceYears(t.getExperienceStartDate() != null
                        ? Period.between(t.getExperienceStartDate(), LocalDate.now()).getYears()
                        : null)
                .university(t.getUniversity())
                .universitySpeciality(t.getUniversitySpeciality())
                .universityDiploma(t.getUniversityDiploma())
                .universityGraduationYear(t.getUniversityGraduationYear())
                .universityDiplomaDate(t.getUniversityDiplomaDate())
                .combatVeteranStatus(t.isCombatVeteranStatus())
                .combatVeteranDoc(t.getCombatVeteranDoc())
                .combatVeteranDocDate(t.getCombatVeteranDocDate())
                .combatVeteranDocIssuedBy(t.getCombatVeteranDocIssuedBy())
                .combatExperienceDates(t.getCombatExperienceDates())
                .militaryEducationLevel(t.getMilitaryEducationLevel() != null ? t.getMilitaryEducationLevel().name() : null)
                .militaryEducationDiploma(t.getMilitaryEducationDiploma())
                .militaryEducationDiplomaDate(t.getMilitaryEducationDiplomaDate())
                .militaryEducationIssuedBy(t.getMilitaryEducationIssuedBy())
                .orcidId(t.getOrcidId())
                .googleScholarUrl(t.getGoogleScholarUrl())
                .scopusId(t.getScopusId())
                .wosId(t.getWosId())
                .email(t.getEmail())
                .phone(t.getPhone())
                .photoUrl(t.getPhotoUrl())
                .notes(t.getNotes())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt());

        if (t.getDepartment() != null) {
            builder.departmentId(t.getDepartment().getId());
            builder.departmentNumber(t.getDepartment().getNumber());
            builder.departmentName(t.getDepartment().getName());
        }

        return builder.build();
    }
}
