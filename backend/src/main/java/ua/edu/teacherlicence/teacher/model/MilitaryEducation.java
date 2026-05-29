package ua.edu.teacherlicence.teacher.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "military_educations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilitaryEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    private Teacher teacher;

    /** Рівень воєнної освіти (оперативний / стратегічний) */
    @Enumerated(EnumType.STRING)
    private MilitaryEducationLevel level;

    /** Назва закладу освіти */
    @Column(columnDefinition = "TEXT")
    private String institution;

    /** Спеціальність */
    @Column(columnDefinition = "TEXT")
    private String speciality;

    /** Номер диплома */
    @Column(columnDefinition = "TEXT")
    private String diploma;

    /** Дата видачі диплома */
    private LocalDate diplomaDate;

    /** Ким видано диплом */
    @Column(length = 500)
    private String issuedBy;

    /** Рік закінчення */
    private Integer graduationYear;
}
