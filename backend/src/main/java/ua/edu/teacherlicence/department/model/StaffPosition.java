package ua.edu.teacherlicence.department.model;

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
import ua.edu.teacherlicence.teacher.model.Teacher;

/**
 * Штатна посада кафедри (організаційно-штатна структура).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "staff_positions")
public class StaffPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** Порядковий номер у штатці */
    private Integer orderNumber;

    /** Назва посади: "Начальник кафедри", "Професор", "Доцент" */
    private String positionTitle;

    /** Штатно-посадова категорія (військове звання): "Полковник", "Підполковник" */
    private String militaryRankCategory;

    /** Військово-облікова спеціальність (код): "5302003" */
    private String militarySpecialtyCode;

    /** Тарифний розряд */
    private Integer tariffGrade;

    /** Ставка (1.0 = повна, 0.5 = пів, 0.25 = чверть) */
    @Builder.Default
    private Double rate = 1.0;

    /** ПІБ з імпортованого штатного розпису (зберігається незалежно від лінкування) */
    private String importedTeacherName;

    /** Призначений викладач (null = ВАКАНТ) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    /**
     * Прапорець "створено автоматично під час bootstrap-міграції з Teacher.position".
     * Значення true означає, що запис створено системою без перевірки реальних даних
     * штатного розпису — адмін має перевірити та оновити (ставка, ШПК, тариф, ВОС).
     * Скидається у false при будь-якому ручному редагуванні через UI.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean bootstrapped = false;
}
