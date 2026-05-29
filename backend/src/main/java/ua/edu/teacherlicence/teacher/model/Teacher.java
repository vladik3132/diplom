package ua.edu.teacherlicence.teacher.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.department.model.Department;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "teachers")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String lastName;

    private String firstName;

    private String patronymic;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    private String militaryRank;

    /**
     * @deprecated Колонка БД видалена у міграції 015. Поле залишається
     * як {@link jakarta.persistence.Transient} — використовується лише
     * як runtime-проміжне значення в імпортерах перед викликом
     * {@link ua.edu.teacherlicence.teacher.service.TeacherPositionService#ensureStaffPosition(Teacher)}.
     *
     * <p>Джерело правди для посади викладача — {@code staff_positions}
     * (через {@code TeacherPositionService.getEffectivePosition}).
     */
    @Deprecated
    @jakarta.persistence.Transient
    private String position;

    private String employmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    private LocalDate experienceStartDate;

    @Column(columnDefinition = "TEXT")
    private String university;

    @Column(columnDefinition = "TEXT")
    private String universitySpeciality;

    @Column(columnDefinition = "TEXT")
    private String universityDiploma;

    private Integer universityGraduationYear;

    private LocalDate universityDiplomaDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean combatVeteranStatus = false;

    private String combatVeteranDoc;

    private LocalDate combatVeteranDocDate;

    @Column(length = 500)
    private String combatVeteranDocIssuedBy;

    private String combatExperienceDates;

    // ── Військова освіта ──

    /** Рівень воєнної освіти (оперативний / стратегічний) */
    @Enumerated(EnumType.STRING)
    private MilitaryEducationLevel militaryEducationLevel;

    /** Номер диплома військової освіти */
    private String militaryEducationDiploma;

    /** Дата диплома військової освіти */
    private LocalDate militaryEducationDiplomaDate;

    /** Ким видано диплом військової освіти */
    @Column(length = 500)
    private String militaryEducationIssuedBy;

    private String orcidId;

    private String googleScholarUrl;

    private String scopusId;

    private String wosId;

    private String email;

    private String phone;

    private String photoUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
