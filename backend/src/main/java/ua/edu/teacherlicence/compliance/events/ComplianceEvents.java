package ua.edu.teacherlicence.compliance.events;

/**
 * Domain events для compliance cache invalidation / refresh.
 *
 * Публікуються через {@link org.springframework.context.ApplicationEventPublisher}
 * після успішних CUD операцій (publishEvent після save/delete у сервісах).
 *
 * Обробляються {@link ua.edu.teacherlicence.compliance.service.ComplianceEventListener}
 * з {@code @TransactionalEventListener(phase=AFTER_COMMIT)} + {@code @Async}.
 *
 * Правило: **ПУБЛІКУЄМО після успішного збереження у БД**. Якщо CUD у tx упав —
 * event не піде (AFTER_COMMIT), відповідно refresh не стартує.
 */
public final class ComplianceEvents {

    private ComplianceEvents() {}

    /** Achievement додано/оновлено/видалено для teacher. */
    public record AchievementChanged(Long teacherId) {}

    /** Publication додано/оновлено/видалено для teacher. */
    public record PublicationChanged(Long teacherId) {}

    /** Education додано/оновлено/видалено для teacher. */
    public record EducationChanged(Long teacherId) {}

    /** QualificationImprovement CUD. */
    public record QualificationChanged(Long teacherId) {}

    /** LanguageSkill CUD. */
    public record LanguageChanged(Long teacherId) {}

    /** MilitaryEducation CUD. */
    public record MilitaryEducationChanged(Long teacherId) {}

    /** Teacher entity CUD — зміна employmentType, experienceStartDate, departmentId тощо. */
    public record TeacherChanged(Long teacherId) {}

    /** Зміна кафедри викладача — інвалідує ВСІ program-match + весь teacher_compliance_cache. */
    public record TeacherDepartmentChanged(Long teacherId, Long oldDepartmentId, Long newDepartmentId) {}

    /** Teacher повністю видалено (cascade автоматично через FK). */
    public record TeacherDeleted(Long teacherId) {}

    /** TeacherDiscipline призначено. */
    public record TeacherDisciplineAssigned(Long teacherId, Long disciplineId) {}

    /** TeacherDiscipline знято. */
    public record TeacherDisciplineRemoved(Long teacherId, Long disciplineId) {}

    /** Discipline оновлено (змінена назва/ОПП/кафедра → перераховуємо усі матчі). */
    public record DisciplineChanged(Long disciplineId) {}

    /** Discipline видалено — рядки cache каскадно прибираються FK. */
    public record DisciplineDeleted(Long disciplineId) {}

    /** EducationalProgram оновлено (спеціальність, галузь, і т.д.) — інвалідує всі ProgramMatch. */
    public record EducationalProgramChanged(Long programId) {}

    /** EducationalProgram видалено — cascade чистить program_match_cache. */
    public record EducationalProgramDeleted(Long programId) {}
}
