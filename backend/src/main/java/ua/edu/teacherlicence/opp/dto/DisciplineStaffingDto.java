package ua.edu.teacherlicence.opp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Статус забезпеченості дисципліни (освітнього компонента) згідно п.36 та п.37.
 * Розраховується для кожної дисципліни ОПП.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisciplineStaffingDto {

    private Long disciplineId;

    /** Список статусів кожного призначеного викладача */
    private List<TeacherQualificationDto> teachers;

    /** Чи забезпечена дисципліна: хоча б один викладач відповідає п.36 І п.37 */
    private boolean staffed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherQualificationDto {
        private Long teacherId;
        private String teacherName;
        private String position;
        private String employmentType;
        private String academicDegree;
        private String academicTitle;

        // ─── п.36: ≥4 типи досягнень п.38 ───
        private int point38TypeCount;
        private boolean point36Compliant; // ≥4

        // ─── п.37 Блок А: відповідність кваліфікації (хоча б 1 з 4) ───
        /** А1: диплом вищої освіти відповідної спеціальності */
        private boolean hasMatchingDiploma;
        /** А2: науковий ступінь відповідної спеціальності */
        private boolean hasMatchingDegree;
        /** А3: досвід за фахом ≥5 років (пп.20) */
        private boolean hasPracticalExperience;
        /** А4: керівництво захищеною дисертацією (пп.6) */
        private boolean hasDissertationSupervision;
        /** Блок А виконано (хоча б один з А1-А4) */
        private boolean point37aCompliant;

        // ─── п.37 Блок Б: ≥5 фахових публікацій (пп.1) ───
        /** Кількість кваліфікованих публікацій (fieldRelevant != false) */
        private int qualifiedPublicationsCount;
        private boolean point37bCompliant;

        /** п.37 повністю виконано (А і Б) */
        private boolean point37Compliant;

        /** Загальний статус: п.36 І п.37 */
        private boolean fullyCompliant;
    }
}
