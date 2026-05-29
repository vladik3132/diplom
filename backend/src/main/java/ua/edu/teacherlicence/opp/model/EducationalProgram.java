package ua.edu.teacherlicence.opp.model;

import jakarta.persistence.*;
import lombok.*;
import ua.edu.teacherlicence.department.model.Department;

import java.time.LocalDateTime;

/**
 * Освітньо-професійна програма (ОПП).
 * Одна ОПП належить одній кафедрі. Кафедра може мати багато ОПП.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "educational_programs")
public class EducationalProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Назва ОПП */
    @Column(nullable = false)
    private String name;

    /** Скорочене позначення (напр. F3 КН, F5 КЗІ (121501)) */
    private String shortCode;

    /** Рівень вищої освіти (перший (бакалаврський), другий (магістерський)) */
    private String educationLevel;

    /** Форма здобуття освіти (очна (денна), заочна) */
    private String educationForm;

    /** Ступінь вищої освіти (бакалавр, магістр) */
    private String degree;

    /** Освітня кваліфікація */
    private String educationalQualification;

    /** Галузь знань (код + назва) */
    private String fieldOfKnowledge;

    /** Професійна кваліфікація */
    private String professionalQualification;

    /** Спеціальність (код + назва) */
    private String specialty;

    /** Обсяг ОПП (кредити ЄКТС) */
    private Integer credits;

    /** Спеціалізація */
    @Column(columnDefinition = "TEXT")
    private String specialization;

    /** Строк навчання */
    private String duration;

    /** Рік набору */
    private Integer enrollmentYear;

    /** Кафедра, що відповідає за ОПП */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
