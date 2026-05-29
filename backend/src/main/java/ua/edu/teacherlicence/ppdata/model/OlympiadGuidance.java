package ua.edu.teacherlicence.ppdata.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ua.edu.teacherlicence.common.model.BaseAuditEntity;

import java.time.LocalDate;

/**
 * пп.14+15 — Діяльність зі здобувачами освіти: олімпіади, конкурси,
 * наукові гуртки, спортивні/мистецькі змагання, оргкомітети, журі.
 *
 * <p>Охоплює всі варіанти пп.14 Ліцензійних умов:
 * <ul>
 *   <li>Підготовка переможців олімпіад, конкурсів наукових робіт</li>
 *   <li>Керівництво науковим гуртком / товариством</li>
 *   <li>Тренерство спортивних збірних</li>
 *   <li>Робота в оргкомітетах/журі</li>
 *   <li>Мистецькі конкурси/виступи</li>
 * </ul>
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "olympiad_guidance")
public class OlympiadGuidance extends BaseAuditEntity {

    /** Рівень: студентський / шкільний (для олімпіад/конкурсів). Nullable для гуртків. */
    @Enumerated(EnumType.STRING)
    private OlympiadLevel level;

    /** Тип діяльності (олімпіада, гурток, спорт, мистецтво, тощо) */
    @Enumerated(EnumType.STRING)
    private Pp14ActivityType activityType;

    /** Назва олімпіади/конкурсу */
    private String olympiadName;

    /** ПІБ учасника (студента/школяра) */
    private String studentName;

    /** Результат (місце, нагорода, тощо) */
    private String result;

    /** Рік проведення */
    @Column(name = "competition_year")
    private Integer year;

    /** Масштаб заходу: міжнародний / всеукраїнський / регіональний */
    @Enumerated(EnumType.STRING)
    private CompetitionScope competitionScope;

    /** Роль викладача */
    @Enumerated(EnumType.STRING)
    private OlympiadRole role;

    /** Назва конкурсу (альт. поле, для зворотної сумісності) */
    private String competitionName;

    // ──────── Нові поля для розширеної моделі ────────

    /** Назва кафедри (для наукових гуртків) */
    private String departmentName;

    /** Кількість учасників (для гуртків/секцій) */
    private Integer participantCount;

    /** Навчальний рік (напр. "2023-2024") */
    private String academicYear;

    /** Номер наказу */
    private String orderNumber;

    /** Дата наказу */
    private LocalDate orderDate;

    /** Вільний текст опису (для нестандартних записів) */
    @Column(columnDefinition = "TEXT")
    private String description;
}
