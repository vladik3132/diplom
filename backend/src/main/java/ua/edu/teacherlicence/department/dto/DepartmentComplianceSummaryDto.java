package ua.edu.teacherlicence.department.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentComplianceSummaryDto {

    private Long departmentId;
    /** Номер кафедри (наприклад "11", "22"). Не той самий що id. */
    private String departmentNumber;
    private String departmentName;
    private String facultyName;

    // --- Кількість викладачів ---
    private int totalTeachers;
    private int mainEmploymentTeachers;
    private int partTimeTeachers;

    // --- п.35: ступінь/звання + основне місце роботи ---
    private int withDegreeAndMainCount;
    private double withDegreeAndMainPercent;
    private boolean point35Compliant;

    // --- п.35: доктори/професори (для магістратури) ---
    private int doctorsOrProfessorsCount;
    private double doctorsOrProfessorsPercent;

    // --- Розширена статистика по ступенях / званнях ---
    /** К-сть викладачів кафедри з ступенем "Доктор філософії" / "Кандидат наук". */
    private int phdCount;
    /** К-сть викладачів кафедри з ступенем "Доктор наук". */
    private int doctorOfScienceCount;
    /** К-сть викладачів зі званням Доцент / СНС (старший науковий співробітник) / СНД (старший дослідник). */
    private int docentOrSeniorResearcherCount;
    /** К-сть викладачів зі званням Професор (звання, не посада). */
    private int professorTitleCount;

    // --- Закон про вищу освіту: ≥3 особи зі ступенем за спеціальністю кафедри ---
    private int defendedInSpecialtyCount;
    private int defendedInSpecialtyRequirement; // = 3
    private boolean defendedInSpecialtyCompliant;
    /** Список ПІБ викладачів, які зараховуються (для tooltip/UI). */
    private List<String> defendedInSpecialtyTeachers;

    // --- п.38 статистика ---
    private int point38Compliant;
    private int point38Warning;
    private int point38NonCompliant;
    private int point38Exempt;

    // --- Загальний статус кафедри ---
    private String overallStatus; // "GOOD" / "WARNING" / "CRITICAL"

    // --- Деталі по викладачах ---
    private List<ComplianceReportDto> teacherReports;

    // ═══════════════════════════════════════════════════════════════
    //  ROW 1 — Основне місце роботи (employmentType = MAIN)
    //  Кожна цифра має список Brief для tooltip з ПІБ + ступенем/званням
    // ═══════════════════════════════════════════════════════════════

    /** Усі MAIN-викладачі кафедри (для tooltip "Всього викладачів"). */
    private List<TeacherDegreeBrief> mainAllTeachers;
    private int mainTotalTeachers;

    /** MAIN з ступенем "Доктор філософії" / PhD. */
    private List<TeacherDegreeBrief> mainPhdTeachers;
    /** MAIN з ступенем "Кандидат наук". */
    private List<TeacherDegreeBrief> mainCandidateTeachers;
    /** MAIN з ступенем "Доктор наук". */
    private List<TeacherDegreeBrief> mainDoctorOfScienceTeachers;

    /** MAIN зі званням "Доцент". */
    private List<TeacherTitleBrief> mainDocentTeachers;
    /** MAIN зі званням "Старший науковий співробітник" (СНС). */
    private List<TeacherTitleBrief> mainSnsTeachers;
    /** MAIN зі званням "Старший дослідник" (СНД). */
    private List<TeacherTitleBrief> mainSndTeachers;
    /** MAIN зі званням "Професор" (звання, не посада). */
    private List<TeacherTitleBrief> mainProfessorTitleTeachers;

    /** К-сть MAIN, які відповідають п.38. */
    private int mainPoint38CompliantCount;
    /** ПІБ MAIN, які відповідають п.38. */
    private List<String> mainPoint38CompliantTeachers;

    // ═══════════════════════════════════════════════════════════════
    //  ROW 2 — Сумісники (employmentType = PART_TIME)
    // ═══════════════════════════════════════════════════════════════

    private List<TeacherDegreeBrief> partTimeAllTeachers;
    private int partTimeTotalTeachers;

    private List<TeacherDegreeBrief> partTimePhdTeachers;
    private List<TeacherDegreeBrief> partTimeCandidateTeachers;
    private List<TeacherDegreeBrief> partTimeDoctorOfScienceTeachers;

    private List<TeacherTitleBrief> partTimeDocentTeachers;
    private List<TeacherTitleBrief> partTimeSnsTeachers;
    private List<TeacherTitleBrief> partTimeSndTeachers;
    private List<TeacherTitleBrief> partTimeProfessorTitleTeachers;

    private int partTimePoint38CompliantCount;
    private List<String> partTimePoint38CompliantTeachers;

    // ═══════════════════════════════════════════════════════════════
    //  ROW 3 — Додаткові метрики
    // ═══════════════════════════════════════════════════════════════

    /** К-сть військових (всі викладачі: MAIN + PART_TIME). */
    private int militaryCount;
    private List<String> militaryTeachers;

    /** К-сть цивільних / Працівників ЗСУ (MAIN + PART_TIME). */
    private int civilianCount;
    private List<String> civilianTeachers;

    /** Середній вік викладачів кафедри (за {@code dateOfBirth}). null якщо нікого з заповненою датою. */
    private Double averageAgeYears;

    /** К-сть штатних позицій кафедри без призначеного викладача. */
    private int vacantPositionsCount;

    /** Місце кафедри в рейтингу активного періоду. null якщо немає активного періоду. */
    private Integer departmentRatingRank;
    /** Загальна к-сть кафедр у рейтингу (для відображення "N / M"). */
    private Integer departmentRatingTotalDepts;
    /** Сумарні бали кафедри. */
    private Integer departmentRatingTotalScore;

    /** Публікації кафедри за останні 5 років, накопичувально по категоріях. */
    private List<PublicationYearBucket> publicationsByYear;

    // ── Укомплектованість педагогічним складом (за ставками) ──
    /** Сума {@code rate} усіх штатних позицій кафедри (повна штатна чисельність). */
    private Double totalStaffRate;
    /** Сума {@code rate} штатних позицій, на яких є викладач (зайняті). */
    private Double occupiedStaffRate;
    /** Відсоток укомплектованості = occupied / total * 100. null якщо штату немає. */
    private Double staffingPercent;

    // ── Відповідність п.37 (виключно MAIN) ──
    /** К-сть MAIN-викладачів, що відповідають п.37 (Блок А ∧ Блок Б). */
    private int point37CompliantCount;
    /** Усі MAIN-викладачі з деталізацією (для tooltip — і compliant, і не). */
    private List<Point37TeacherBrief> point37TeachersDetail;
}
