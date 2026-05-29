package ua.edu.teacherlicence.discipline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.opp.model.EducationalProgram;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "disciplines")
public class Discipline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Назва дисципліни */
    private String name;

    /** Код компоненти (напр. ОК 6, ВК 1.3) */
    private String code;

    /** Кафедра, що забезпечує дисципліну */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** ОПП, до якої належить дисципліна */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "educational_program_id")
    private EducationalProgram educationalProgram;

    /** Кредити ЄКТС */
    private Double credits;

    /** Загальний обсяг годин */
    private Integer totalHours;

    /** Аудиторних годин (лекції + групові + практичні + лабораторні) */
    private Integer auditoryHours;

    /** Лекції (год) */
    private Integer hoursLecture;

    /** Групові заняття (год) */
    private Integer hoursGroup;

    /** Практичні / семінарські (год) */
    private Integer hoursPractical;

    /** Лабораторні (год) */
    private Integer hoursLab;

    /** Самостійна підготовка (год) */
    private Integer hoursSelfStudy;

    /** Семестри з іспитами (напр. "3,5") */
    private String examSemesters;

    /** Семестри з заліками (напр. "2,4") */
    private String creditSemesters;

    /** Години по семестрах (JSON: {"1":60,"2":90,...}) */
    @Column(columnDefinition = "TEXT")
    private String hoursBySemester;

    /** Кредити по семестрах (JSON: {"1":2.0,"2":3.0,...}) */
    @Column(columnDefinition = "TEXT")
    private String creditsBySemester;

    /** Типи контролю: іспит, залік, МКР, курсова робота тощо (JSON array) */
    @Column(columnDefinition = "TEXT")
    private String controlTypes;
}
