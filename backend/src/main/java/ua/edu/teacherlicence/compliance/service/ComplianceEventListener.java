package ua.edu.teacherlicence.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ua.edu.teacherlicence.compliance.config.ComplianceAsyncConfig;
import ua.edu.teacherlicence.compliance.events.ComplianceEvents;

/**
 * Централізований listener для всіх ComplianceEvents.
 * Виконується ПІСЛЯ коміту транзакції (AFTER_COMMIT) і ASYNC — не блокує
 * користувацький request.
 *
 * Стратегія: простіше і безпечніше — при будь-якій зміні викладача оновлювати
 * обидва рівні: teacher_compliance_cache + усі його дисципліни + усі його програми.
 * Це дорого при масовому imports, але уникає пропусків.
 *
 * Для singleton teacher-level events refresh бере ~100-500 мс (з AI до 2 сек).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceEventListener {

    private final ComplianceCacheService complianceCache;
    private final DisciplineMatchCacheService disciplineMatchCache;
    private final ProgramMatchCacheService programMatchCache;
    private final DepartmentSummaryService departmentSummary;

    // ─── Teacher-level changes → повний refresh teacher ───

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onAchievementChanged(ComplianceEvents.AchievementChanged e) {
        refreshTeacherFully(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onPublicationChanged(ComplianceEvents.PublicationChanged e) {
        refreshTeacherFully(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onEducationChanged(ComplianceEvents.EducationChanged e) {
        // Education впливає на AI-матчинг (диплом/ступінь до дисципліни/ОПП) — обов'язково.
        refreshTeacherFully(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onQualificationChanged(ComplianceEvents.QualificationChanged e) {
        // Впливає тільки на п.38 teacher_compliance_cache.
        complianceCache.refreshTeacherSync(e.teacherId());
        departmentSummary.refreshMaterializedView();
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onLanguageChanged(ComplianceEvents.LanguageChanged e) {
        complianceCache.refreshTeacherSync(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onMilitaryEducationChanged(ComplianceEvents.MilitaryEducationChanged e) {
        complianceCache.refreshTeacherSync(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onTeacherChanged(ComplianceEvents.TeacherChanged e) {
        // Зміна employmentType, experienceStartDate, academic_degree, academic_title →
        // впливає на: п.35/п.38 (teacher_compliance_cache),
        //             п.37 ОПП (teacher_program_match_cache, бо a2 враховує title),
        //             п.36+п.37 дисципліни (teacher_discipline_match_cache, бо a2 враховує degree).
        // Тому повний refresh — як при EducationChanged.
        refreshTeacherFully(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onTeacherDepartmentChanged(ComplianceEvents.TeacherDepartmentChanged e) {
        // Зміна кафедри → teacher_compliance_cache (відповідність кафедрі) +
        // вибуття зі старої кафедри → переглянути ОПП/дисципліни (але cascade ON DELETE
        // не стосується — teacher залишається, просто в іншій кафедрі).
        refreshTeacherFully(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onTeacherDeleted(ComplianceEvents.TeacherDeleted e) {
        // Cascade ON DELETE FK уже прибрав записи; просто refresh MV.
        departmentSummary.refreshMaterializedView();
    }

    // ─── TeacherDiscipline assignments ───

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onTeacherDisciplineAssigned(ComplianceEvents.TeacherDisciplineAssigned e) {
        disciplineMatchCache.refresh(e.teacherId(), e.disciplineId());
        programMatchCache.refreshAllForTeacher(e.teacherId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onTeacherDisciplineRemoved(ComplianceEvents.TeacherDisciplineRemoved e) {
        disciplineMatchCache.remove(e.teacherId(), e.disciplineId());
        // Можливо ОПП більше не веде — programMatchCache.refresh зітре.
        programMatchCache.refreshAllForTeacher(e.teacherId());
    }

    // ─── Discipline / Program changes ───

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onDisciplineChanged(ComplianceEvents.DisciplineChanged e) {
        disciplineMatchCache.refreshAllForDiscipline(e.disciplineId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onDisciplineDeleted(ComplianceEvents.DisciplineDeleted e) {
        disciplineMatchCache.removeAllForDiscipline(e.disciplineId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onEducationalProgramChanged(ComplianceEvents.EducationalProgramChanged e) {
        programMatchCache.refreshAllForProgram(e.programId());
    }

    @Async(ComplianceAsyncConfig.COMPLIANCE_EXECUTOR)
    @TransactionalEventListener
    public void onEducationalProgramDeleted(ComplianceEvents.EducationalProgramDeleted e) {
        programMatchCache.removeAllForProgram(e.programId());
    }

    // ─── helpers ───

    private void refreshTeacherFully(Long teacherId) {
        complianceCache.refreshTeacherSync(teacherId);
        disciplineMatchCache.refreshAllForTeacher(teacherId);
        programMatchCache.refreshAllForTeacher(teacherId);
        departmentSummary.refreshMaterializedView();
    }
}
