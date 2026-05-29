package ua.edu.teacherlicence.qualification.model;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "qualification_improvements")
public class QualificationImprovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    private String title;

    private String organization;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer hours;

    private Double credits;

    private String certificateNumber;

    private LocalDate certificateDate;

    private String certificateUrl;

    /** Країна (для визначення закордонного стажування) */
    private String country;

    /** Категорія: загальне або курси військової освіти */
    @Enumerated(EnumType.STRING)
    private QualificationCategory category;

    /** Рівень курсу ВО: L2 (5 балів), L3 (10), L4 (15) */
    @Enumerated(EnumType.STRING)
    private MilitaryCourseLevel militaryCourseLevel;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
