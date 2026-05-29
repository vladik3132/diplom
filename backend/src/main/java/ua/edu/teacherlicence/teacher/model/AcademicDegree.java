package ua.edu.teacherlicence.teacher.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Науковий ступінь викладача. Один викладач може мати кілька ступенів
 * (наприклад спершу Доктор філософії за однією спеціальністю, потім
 * Доктор наук за іншою).
 *
 * Є єдиним джерелом правди — flat-полів у Teacher більше немає.
 * Для отримання «первинного» ступеня використовуйте {@link ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking#primary}.
 */
@Entity
@Table(name = "academic_degrees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicDegree {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    private Teacher teacher;

    /**
     * Назва ступеня (наприклад "Доктор технічних наук", "Доктор філософії",
     * "Кандидат технічних наук", "PhD").
     */
    private String degree;

    /** Спеціальність ступеня (шифр+назва, напр. "20.02.14 – Озброєння і військова техніка"). */
    @Column(columnDefinition = "TEXT")
    private String speciality;

    /** Тема дисертації. */
    @Column(columnDefinition = "TEXT")
    private String dissertationTopic;

    /** Реквізити диплома (серія/номер). */
    private String diploma;

    /** Дата видачі диплома. */
    private LocalDate diplomaDate;

    /** Ким видано (рада з захисту). */
    @Column(length = 500)
    private String issuedBy;
}
