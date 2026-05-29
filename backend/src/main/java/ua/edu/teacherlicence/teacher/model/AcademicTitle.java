package ua.edu.teacherlicence.teacher.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Вчене звання викладача.
 * Один викладач може мати декілька (Доцент кафедри X → пізніше Професор кафедри Y).
 * Текст titleName зазвичай довший за просто «Доцент»: «Доцент кафедри комп'ютерних наук».
 */
@Entity
@Table(name = "academic_titles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    private Teacher teacher;

    /** Повна назва звання, напр. «Доцент кафедри комп'ютерних наук» */
    @Column(name = "title_name", columnDefinition = "TEXT")
    private String titleName;

    /** Реквізити атестата (серія, номер) */
    @Column(name = "attestat", length = 255)
    private String attestat;

    /** Дата видачі атестата */
    @Column(name = "attestat_date")
    private LocalDate attestatDate;

    /** Ким видано (Атестаційна колегія МОН тощо) */
    @Column(name = "issued_by", length = 255)
    private String issuedBy;
}
