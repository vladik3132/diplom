package ua.edu.teacherlicence.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportDto {

    public enum ComplianceStatus {
        COMPLIANT,      // >= 4 різних типи досягнень
        WARNING,        // 3 типи (потрібен ще 1)
        NON_COMPLIANT,  // < 3 типи
        EXEMPT          // звільнений від вимог
    }

    private Long teacherId;
    private String teacherName;
    private ComplianceStatus status;
    private String exemptionReason;
    private int achievementCount;
    private int uniqueTypeCount;
    private List<String> achievementTypes;
    private List<String> missingInfo;
    private int publicationsCount;

    // ── Поля для відображення у списку викладачів кафедри (Стадія 4 refactor) ──
    /** Ефективна посада (зі staff_positions через TeacherPositionService). */
    private String position;
    /** Військове звання людини (Teacher.militaryRank). */
    private String militaryRank;
    /** "MAIN" / "PART_TIME" — для тегу "Сумісник" в UI. */
    private String employmentType;
    /** Primary staff_position бутстрапована (потребує перегляду адміном) → UI показує ⚠️. */
    private boolean bootstrappedPosition;

    // ─── Відповідність кафедрі ───
    /** Диплом відповідає напряму кафедри */
    private boolean diplomaMatchesDepartment;
    /** Ступінь відповідає напряму кафедри */
    private boolean degreeMatchesDepartment;
    /** Диплом або ступінь відповідає (A1 || A2) */
    private boolean qualificationMatchesDepartment;
    /** Хоча б одне вчене звання відповідає напряму кафедри (AI). */
    private boolean titleMatchesDepartment;
    /** Кількість фахових статей за напрямком кафедри (fieldRelevant=true) */
    private int relevantPublicationsCount;
}
