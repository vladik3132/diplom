package ua.edu.teacherlicence.teacher.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "educations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    private Teacher teacher;

    /** Назва закладу освіти */
    @Column(columnDefinition = "TEXT")
    private String institution;

    /** Місто */
    private String city;

    /** Рівень освіти: Бакалавр, Магістр, Спеціаліст, Фаховий молодший бакалавр */
    private String degree;

    /** Спеціальність */
    @Column(columnDefinition = "TEXT")
    private String speciality;

    /** Кваліфікація (напр. "Інженер електронної техніки") */
    @Column(columnDefinition = "TEXT")
    private String qualification;

    /** Рік закінчення */
    private Integer graduationYear;

    /** Диплом (серія, номер, з відзнакою тощо) */
    @Column(columnDefinition = "TEXT")
    private String diploma;

    /** Дата видачі диплому */
    private LocalDate diplomaDate;
}
