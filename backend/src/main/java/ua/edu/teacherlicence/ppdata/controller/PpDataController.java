package ua.edu.teacherlicence.ppdata.controller;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.file.model.EntityTypeConstants;
import ua.edu.teacherlicence.file.service.FileAttachmentService;
import ua.edu.teacherlicence.notification.service.ChangeNotificationService;
import ua.edu.teacherlicence.notification.service.FieldDiff;
import ua.edu.teacherlicence.ppdata.dto.PpDataValidationResponse;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.service.PpDataService;
import ua.edu.teacherlicence.ppdata.service.PpDataValidationService;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;

/**
 * Єдиний REST-контролер для всіх 11 структурованих даних пп.5-20.
 * URL-патерн: /api/teachers/{teacherId}/{entity-type}
 */
@RestController
@RequestMapping("/api/teachers/{teacherId}")
@RequiredArgsConstructor
public class PpDataController {

    private final PpDataService ppDataService;
    private final TeacherRepository teacherRepository;
    private final CurrentUserProvider currentUser;
    private final ChangeNotificationService changeNotificationService;
    private final FileAttachmentService fileAttachmentService;

    @Setter(onMethod_ = @Autowired(required = false))
    private PpDataValidationService ppDataValidationService;

    /**
     * Отримати Teacher за teacherId або кинути виняток.
     */
    private Teacher resolveTeacher(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Викладача не знайдено: " + teacherId));
    }

    /**
     * Перевірка прав доступу до даних викладача.
     */
    private void checkAccess(Long teacherId) throws AccessDeniedException {
        if (!currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(teacherId);
        }
    }

    /**
     * Надіслати нотифікацію про зміну ppData.
     */
    private void notifyChange(Long teacherId, String action, String section, String details) {
        try {
            changeNotificationService.notifyDataChanged(teacherId, currentUser.getCurrentUser(),
                    action, "Дані п.38 — " + section, details);
        } catch (Exception ignored) {
            // non-critical
        }
    }

    // =====================================================================
    // пп.6 — ScientificSupervision (Наукове керівництво)
    // =====================================================================

    @GetMapping("/scientific-supervision")
    public List<ScientificSupervision> listScientificSupervision(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findScientificSupervisionByTeacherId(teacherId);
    }

    @PostMapping("/scientific-supervision")
    @ResponseStatus(HttpStatus.CREATED)
    public ScientificSupervision createScientificSupervision(
            @PathVariable Long teacherId,
            @RequestBody ScientificSupervision entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        ScientificSupervision saved = ppDataService.saveScientificSupervision(entity);
        notifyChange(teacherId, "додано", "пп.6 Наукове керівництво", entity.getStudentName());
        return saved;
    }

    @PutMapping("/scientific-supervision/{id}")
    public ScientificSupervision updateScientificSupervision(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody ScientificSupervision entity) throws AccessDeniedException {
        checkAccess(teacherId);
        ScientificSupervision existing = ppDataService.findScientificSupervisionById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Здобувач", existing.getStudentName(), entity.getStudentName())
                .compare("Тема", existing.getTopic(), entity.getTopic())
                .compare("Ступінь", existing.getDegreeType(), entity.getDegreeType())
                .compare("Дата захисту", existing.getDefenseDate(), entity.getDefenseDate());
        existing.setStudentName(entity.getStudentName());
        existing.setTopic(entity.getTopic());
        existing.setDefenseDate(entity.getDefenseDate());
        existing.setDegreeType(entity.getDegreeType());
        existing.setDiplomaNumber(entity.getDiplomaNumber());
        existing.setDocumentUrl(entity.getDocumentUrl());
        ScientificSupervision saved = ppDataService.saveScientificSupervision(existing);
        String details = diff.hasChanges() ? entity.getStudentName() + " | " + diff.build() : entity.getStudentName();
        notifyChange(teacherId, "оновлено", "пп.6 Наукове керівництво", details);
        return saved;
    }

    @DeleteMapping("/scientific-supervision/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScientificSupervision(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_SCIENTIFIC_SUPERVISION, id);
        ppDataService.deleteScientificSupervisionById(id);
        notifyChange(teacherId, "видалено", "пп.6 Наукове керівництво", "запис #" + id);
    }

    // =====================================================================
    // пп.7 — AttestationActivity (Участь в атестації)
    // =====================================================================

    @GetMapping("/attestation-activity")
    public List<AttestationActivity> listAttestationActivity(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findAttestationActivityByTeacherId(teacherId);
    }

    @PostMapping("/attestation-activity")
    @ResponseStatus(HttpStatus.CREATED)
    public AttestationActivity createAttestationActivity(
            @PathVariable Long teacherId,
            @RequestBody AttestationActivity entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        AttestationActivity saved = ppDataService.saveAttestationActivity(entity);
        notifyChange(teacherId, "додано", "пп.7 Атестація", entity.getCouncilName());
        return saved;
    }

    @PutMapping("/attestation-activity/{id}")
    public AttestationActivity updateAttestationActivity(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody AttestationActivity entity) throws AccessDeniedException {
        checkAccess(teacherId);
        AttestationActivity existing = ppDataService.findAttestationActivityById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Рада", existing.getCouncilName(), entity.getCouncilName())
                .compare("Роль", existing.getRole(), entity.getRole())
                .compare("Здобувач", existing.getStudentName(), entity.getStudentName())
                .compare("Дата захисту", existing.getDefenseDate(), entity.getDefenseDate())
                .compare("Період з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Період по", existing.getDateTo(), entity.getDateTo());
        existing.setRole(entity.getRole());
        existing.setCouncilName(entity.getCouncilName());
        existing.setStudentName(entity.getStudentName());
        existing.setDefenseDate(entity.getDefenseDate());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setDocumentUrl(entity.getDocumentUrl());
        AttestationActivity saved = ppDataService.saveAttestationActivity(existing);
        String details = diff.hasChanges() ? entity.getCouncilName() + " | " + diff.build() : entity.getCouncilName();
        notifyChange(teacherId, "оновлено", "пп.7 Атестація", details);
        return saved;
    }

    @DeleteMapping("/attestation-activity/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttestationActivity(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_ATTESTATION_ACTIVITY, id);
        ppDataService.deleteAttestationActivityById(id);
        notifyChange(teacherId, "видалено", "пп.7 Атестація", "запис #" + id);
    }

    // =====================================================================
    // пп.8 — EditorialActivity (Редакційна діяльність)
    // =====================================================================

    @GetMapping("/editorial-activity")
    public List<EditorialActivity> listEditorialActivity(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findEditorialActivityByTeacherId(teacherId);
    }

    @PostMapping("/editorial-activity")
    @ResponseStatus(HttpStatus.CREATED)
    public EditorialActivity createEditorialActivity(
            @PathVariable Long teacherId,
            @RequestBody EditorialActivity entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        EditorialActivity saved = ppDataService.saveEditorialActivity(entity);
        notifyChange(teacherId, "додано", "пп.8 Редакційна діяльність", entity.getJournalOrProjectName());
        return saved;
    }

    @PutMapping("/editorial-activity/{id}")
    public EditorialActivity updateEditorialActivity(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody EditorialActivity entity) throws AccessDeniedException {
        checkAccess(teacherId);
        EditorialActivity existing = ppDataService.findEditorialActivityById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Видання", existing.getJournalOrProjectName(), entity.getJournalOrProjectName())
                .compare("Роль", existing.getRole(), entity.getRole())
                .compare("Дата з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Дата до", existing.getDateTo(), entity.getDateTo());
        existing.setRole(entity.getRole());
        existing.setJournalOrProjectName(entity.getJournalOrProjectName());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setDescription(entity.getDescription());
        existing.setDocumentUrl(entity.getDocumentUrl());
        EditorialActivity saved = ppDataService.saveEditorialActivity(existing);
        String details = diff.hasChanges() ? entity.getJournalOrProjectName() + " | " + diff.build() : entity.getJournalOrProjectName();
        notifyChange(teacherId, "оновлено", "пп.8 Редакційна діяльність", details);
        return saved;
    }

    @DeleteMapping("/editorial-activity/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEditorialActivity(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_EDITORIAL_ACTIVITY, id);
        ppDataService.deleteEditorialActivityById(id);
        notifyChange(teacherId, "видалено", "пп.8 Редакційна діяльність", "запис #" + id);
    }

    // =====================================================================
    // пп.9 — ExpertCouncil (Експертна рада)
    // =====================================================================

    @GetMapping("/expert-council")
    public List<ExpertCouncil> listExpertCouncil(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findExpertCouncilByTeacherId(teacherId);
    }

    @PostMapping("/expert-council")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpertCouncil createExpertCouncil(
            @PathVariable Long teacherId,
            @RequestBody ExpertCouncil entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        ExpertCouncil saved = ppDataService.saveExpertCouncil(entity);
        notifyChange(teacherId, "додано", "пп.9 Експертна рада", entity.getCouncilName());
        return saved;
    }

    @PutMapping("/expert-council/{id}")
    public ExpertCouncil updateExpertCouncil(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody ExpertCouncil entity) throws AccessDeniedException {
        checkAccess(teacherId);
        ExpertCouncil existing = ppDataService.findExpertCouncilById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Рада", existing.getCouncilName(), entity.getCouncilName())
                .compare("Тип", existing.getType(), entity.getType())
                .compare("Роль", existing.getRole(), entity.getRole())
                .compare("Дата з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Дата до", existing.getDateTo(), entity.getDateTo());
        existing.setCouncilName(entity.getCouncilName());
        existing.setType(entity.getType());
        existing.setRole(entity.getRole());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setOrderNumber(entity.getOrderNumber());
        existing.setDocumentUrl(entity.getDocumentUrl());
        ExpertCouncil saved = ppDataService.saveExpertCouncil(existing);
        String details = diff.hasChanges() ? entity.getCouncilName() + " | " + diff.build() : entity.getCouncilName();
        notifyChange(teacherId, "оновлено", "пп.9 Експертна рада", details);
        return saved;
    }

    @DeleteMapping("/expert-council/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpertCouncil(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_EXPERT_COUNCIL, id);
        ppDataService.deleteExpertCouncilById(id);
        notifyChange(teacherId, "видалено", "пп.9 Експертна рада", "запис #" + id);
    }

    // =====================================================================
    // пп.10 — InternationalProject (Міжнародні проекти)
    // =====================================================================

    @GetMapping("/international-project")
    public List<InternationalProject> listInternationalProject(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findInternationalProjectByTeacherId(teacherId);
    }

    @PostMapping("/international-project")
    @ResponseStatus(HttpStatus.CREATED)
    public InternationalProject createInternationalProject(
            @PathVariable Long teacherId,
            @RequestBody InternationalProject entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        InternationalProject saved = ppDataService.saveInternationalProject(entity);
        notifyChange(teacherId, "додано", "пп.10 Міжнар. проекти", entity.getProjectName());
        return saved;
    }

    @PutMapping("/international-project/{id}")
    public InternationalProject updateInternationalProject(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody InternationalProject entity) throws AccessDeniedException {
        checkAccess(teacherId);
        InternationalProject existing = ppDataService.findInternationalProjectById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Проект", existing.getProjectName(), entity.getProjectName())
                .compare("Програма", existing.getProgram(), entity.getProgram())
                .compare("Роль", existing.getRole(), entity.getRole())
                .compare("Дата з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Дата до", existing.getDateTo(), entity.getDateTo());
        existing.setProjectName(entity.getProjectName());
        existing.setProgram(entity.getProgram());
        existing.setRole(entity.getRole());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setDescription(entity.getDescription());
        existing.setDocumentUrl(entity.getDocumentUrl());
        InternationalProject saved = ppDataService.saveInternationalProject(existing);
        String details = diff.hasChanges() ? entity.getProjectName() + " | " + diff.build() : entity.getProjectName();
        notifyChange(teacherId, "оновлено", "пп.10 Міжнар. проекти", details);
        return saved;
    }

    @DeleteMapping("/international-project/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInternationalProject(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_INTERNATIONAL_PROJECT, id);
        ppDataService.deleteInternationalProjectById(id);
        notifyChange(teacherId, "видалено", "пп.10 Міжнар. проекти", "запис #" + id);
    }

    // =====================================================================
    // пп.11 — ScientificConsulting (Наукове консультування)
    // =====================================================================

    @GetMapping("/scientific-consulting")
    public List<ScientificConsulting> listScientificConsulting(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findScientificConsultingByTeacherId(teacherId);
    }

    @PostMapping("/scientific-consulting")
    @ResponseStatus(HttpStatus.CREATED)
    public ScientificConsulting createScientificConsulting(
            @PathVariable Long teacherId,
            @RequestBody ScientificConsulting entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        ScientificConsulting saved = ppDataService.saveScientificConsulting(entity);
        notifyChange(teacherId, "додано", "пп.11 Наук. консультування", entity.getOrganizationName());
        return saved;
    }

    @PutMapping("/scientific-consulting/{id}")
    public ScientificConsulting updateScientificConsulting(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody ScientificConsulting entity) throws AccessDeniedException {
        checkAccess(teacherId);
        ScientificConsulting existing = ppDataService.findScientificConsultingById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Організація", existing.getOrganizationName(), entity.getOrganizationName())
                .compare("№ договору", existing.getContractNumber(), entity.getContractNumber())
                .compare("Дата з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Дата до", existing.getDateTo(), entity.getDateTo());
        existing.setOrganizationName(entity.getOrganizationName());
        existing.setContractNumber(entity.getContractNumber());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setYearsCount(entity.getYearsCount());
        existing.setDocumentUrl(entity.getDocumentUrl());
        ScientificConsulting saved = ppDataService.saveScientificConsulting(existing);
        String details = diff.hasChanges() ? entity.getOrganizationName() + " | " + diff.build() : entity.getOrganizationName();
        notifyChange(teacherId, "оновлено", "пп.11 Наук. консультування", details);
        return saved;
    }

    @DeleteMapping("/scientific-consulting/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScientificConsulting(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_SCIENTIFIC_CONSULTING, id);
        ppDataService.deleteScientificConsultingById(id);
        notifyChange(teacherId, "видалено", "пп.11 Наук. консультування", "запис #" + id);
    }

    // =====================================================================
    // пп.13 — ForeignLanguageTeaching (Викладання іноземною мовою)
    // =====================================================================

    @GetMapping("/foreign-language-teaching")
    public List<ForeignLanguageTeaching> listForeignLanguageTeaching(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findForeignLanguageTeachingByTeacherId(teacherId);
    }

    @PostMapping("/foreign-language-teaching")
    @ResponseStatus(HttpStatus.CREATED)
    public ForeignLanguageTeaching createForeignLanguageTeaching(
            @PathVariable Long teacherId,
            @RequestBody ForeignLanguageTeaching entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        ForeignLanguageTeaching saved = ppDataService.saveForeignLanguageTeaching(entity);
        notifyChange(teacherId, "додано", "пп.13 Іноземна мова", entity.getDisciplineName());
        return saved;
    }

    @PutMapping("/foreign-language-teaching/{id}")
    public ForeignLanguageTeaching updateForeignLanguageTeaching(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody ForeignLanguageTeaching entity) throws AccessDeniedException {
        checkAccess(teacherId);
        ForeignLanguageTeaching existing = ppDataService.findForeignLanguageTeachingById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Дисципліна", existing.getDisciplineName(), entity.getDisciplineName())
                .compare("Мова", existing.getLanguage(), entity.getLanguage())
                .compare("Години", existing.getHours(), entity.getHours());
        existing.setDisciplineName(entity.getDisciplineName());
        existing.setLanguage(entity.getLanguage());
        existing.setHours(entity.getHours());
        existing.setAcademicYear(entity.getAcademicYear());
        existing.setSemester(entity.getSemester());
        existing.setDocumentUrl(entity.getDocumentUrl());
        ForeignLanguageTeaching saved = ppDataService.saveForeignLanguageTeaching(existing);
        String details = diff.hasChanges() ? entity.getDisciplineName() + " | " + diff.build() : entity.getDisciplineName();
        notifyChange(teacherId, "оновлено", "пп.13 Іноземна мова", details);
        return saved;
    }

    @DeleteMapping("/foreign-language-teaching/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForeignLanguageTeaching(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_FOREIGN_LANGUAGE_TEACHING, id);
        ppDataService.deleteForeignLanguageTeachingById(id);
        notifyChange(teacherId, "видалено", "пп.13 Іноземна мова", "запис #" + id);
    }

    // =====================================================================
    // пп.14+15 — OlympiadGuidance (Олімпіади та конкурси)
    // =====================================================================

    @GetMapping("/olympiad-guidance")
    public List<OlympiadGuidance> listOlympiadGuidance(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findOlympiadGuidanceByTeacherId(teacherId);
    }

    @PostMapping("/olympiad-guidance")
    @ResponseStatus(HttpStatus.CREATED)
    public OlympiadGuidance createOlympiadGuidance(
            @PathVariable Long teacherId,
            @RequestBody OlympiadGuidance entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        OlympiadGuidance saved = ppDataService.saveOlympiadGuidance(entity);
        notifyChange(teacherId, "додано", "пп.14-15 Олімпіади", entity.getOlympiadName());
        return saved;
    }

    @PutMapping("/olympiad-guidance/{id}")
    public OlympiadGuidance updateOlympiadGuidance(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody OlympiadGuidance entity) throws AccessDeniedException {
        checkAccess(teacherId);
        OlympiadGuidance existing = ppDataService.findOlympiadGuidanceById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Олімпіада", existing.getOlympiadName(), entity.getOlympiadName())
                .compare("Учасник", existing.getStudentName(), entity.getStudentName())
                .compare("Результат", existing.getResult(), entity.getResult());
        existing.setLevel(entity.getLevel());
        existing.setOlympiadName(entity.getOlympiadName());
        existing.setStudentName(entity.getStudentName());
        existing.setResult(entity.getResult());
        existing.setYear(entity.getYear());
        existing.setRole(entity.getRole());
        existing.setCompetitionName(entity.getCompetitionName());
        existing.setDocumentUrl(entity.getDocumentUrl());
        OlympiadGuidance saved = ppDataService.saveOlympiadGuidance(existing);
        String details = diff.hasChanges() ? entity.getOlympiadName() + " | " + diff.build() : entity.getOlympiadName();
        notifyChange(teacherId, "оновлено", "пп.14-15 Олімпіади", details);
        return saved;
    }

    @DeleteMapping("/olympiad-guidance/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOlympiadGuidance(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_OLYMPIAD_GUIDANCE, id);
        ppDataService.deleteOlympiadGuidanceById(id);
        notifyChange(teacherId, "видалено", "пп.14-15 Олімпіади", "запис #" + id);
    }

    // =====================================================================
    // пп.17+18 — MilitaryMission (Міжнародні військові місії)
    // =====================================================================

    @GetMapping("/military-mission")
    public List<MilitaryMission> listMilitaryMission(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findMilitaryMissionByTeacherId(teacherId);
    }

    @PostMapping("/military-mission")
    @ResponseStatus(HttpStatus.CREATED)
    public MilitaryMission createMilitaryMission(
            @PathVariable Long teacherId,
            @RequestBody MilitaryMission entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        MilitaryMission saved = ppDataService.saveMilitaryMission(entity);
        notifyChange(teacherId, "додано", "пп.17-18 Військові місії", entity.getMissionName());
        return saved;
    }

    @PutMapping("/military-mission/{id}")
    public MilitaryMission updateMilitaryMission(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody MilitaryMission entity) throws AccessDeniedException {
        checkAccess(teacherId);
        MilitaryMission existing = ppDataService.findMilitaryMissionById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Місія", existing.getMissionName(), entity.getMissionName())
                .compare("Країна", existing.getCountry(), entity.getCountry())
                .compare("Тип", existing.getMissionType(), entity.getMissionType())
                .compare("Дата з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Дата до", existing.getDateTo(), entity.getDateTo());
        existing.setMissionType(entity.getMissionType());
        existing.setMissionName(entity.getMissionName());
        existing.setCountry(entity.getCountry());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setDocumentUrl(entity.getDocumentUrl());
        MilitaryMission saved = ppDataService.saveMilitaryMission(existing);
        String details = diff.hasChanges() ? entity.getMissionName() + " | " + diff.build() : entity.getMissionName();
        notifyChange(teacherId, "оновлено", "пп.17-18 Військові місії", details);
        return saved;
    }

    @DeleteMapping("/military-mission/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMilitaryMission(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_MILITARY_MISSION, id);
        ppDataService.deleteMilitaryMissionById(id);
        notifyChange(teacherId, "видалено", "пп.17-18 Військові місії", "запис #" + id);
    }

    // =====================================================================
    // пп.19 — ProfessionalAssociation (Професійні об'єднання)
    // =====================================================================

    @GetMapping("/professional-association")
    public List<ProfessionalAssociation> listProfessionalAssociation(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findProfessionalAssociationByTeacherId(teacherId);
    }

    @PostMapping("/professional-association")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfessionalAssociation createProfessionalAssociation(
            @PathVariable Long teacherId,
            @RequestBody ProfessionalAssociation entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        ProfessionalAssociation saved = ppDataService.saveProfessionalAssociation(entity);
        notifyChange(teacherId, "додано", "пп.19 Проф. об'єднання", entity.getOrganizationName());
        return saved;
    }

    @PutMapping("/professional-association/{id}")
    public ProfessionalAssociation updateProfessionalAssociation(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody ProfessionalAssociation entity) throws AccessDeniedException {
        checkAccess(teacherId);
        ProfessionalAssociation existing = ppDataService.findProfessionalAssociationById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Організація", existing.getOrganizationName(), entity.getOrganizationName())
                .compare("Роль", existing.getRole(), entity.getRole())
                .compare("Дата з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Дата до", existing.getDateTo(), entity.getDateTo());
        existing.setOrganizationName(entity.getOrganizationName());
        existing.setRole(entity.getRole());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setCertificateNumber(entity.getCertificateNumber());
        existing.setDocumentUrl(entity.getDocumentUrl());
        ProfessionalAssociation saved = ppDataService.saveProfessionalAssociation(existing);
        String details = diff.hasChanges() ? entity.getOrganizationName() + " | " + diff.build() : entity.getOrganizationName();
        notifyChange(teacherId, "оновлено", "пп.19 Проф. об'єднання", details);
        return saved;
    }

    @DeleteMapping("/professional-association/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfessionalAssociation(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_PROFESSIONAL_ASSOCIATION, id);
        ppDataService.deleteProfessionalAssociationById(id);
        notifyChange(teacherId, "видалено", "пп.19 Проф. об'єднання", "запис #" + id);
    }

    // =====================================================================
    // пп.20 — PracticalExperience (Досвід практичної роботи)
    // =====================================================================

    @GetMapping("/practical-experience")
    public List<PracticalExperience> listPracticalExperience(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        return ppDataService.findPracticalExperienceByTeacherId(teacherId);
    }

    @PostMapping("/practical-experience")
    @ResponseStatus(HttpStatus.CREATED)
    public PracticalExperience createPracticalExperience(
            @PathVariable Long teacherId,
            @RequestBody PracticalExperience entity) throws AccessDeniedException {
        checkAccess(teacherId);
        entity.setTeacher(resolveTeacher(teacherId));
        PracticalExperience saved = ppDataService.savePracticalExperience(entity);
        notifyChange(teacherId, "додано", "пп.20 Практ. досвід", entity.getOrganizationName());
        return saved;
    }

    @PutMapping("/practical-experience/{id}")
    public PracticalExperience updatePracticalExperience(
            @PathVariable Long teacherId,
            @PathVariable Long id,
            @RequestBody PracticalExperience entity) throws AccessDeniedException {
        checkAccess(teacherId);
        PracticalExperience existing = ppDataService.findPracticalExperienceById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        FieldDiff diff = new FieldDiff()
                .compare("Організація", existing.getOrganizationName(), entity.getOrganizationName())
                .compare("Посада", existing.getPosition(), entity.getPosition())
                .compare("Дата з", existing.getDateFrom(), entity.getDateFrom())
                .compare("Дата до", existing.getDateTo(), entity.getDateTo());
        existing.setOrganizationName(entity.getOrganizationName());
        existing.setPosition(entity.getPosition());
        existing.setDateFrom(entity.getDateFrom());
        existing.setDateTo(entity.getDateTo());
        existing.setYearsCount(entity.getYearsCount());
        existing.setSpecialtyName(entity.getSpecialtyName());
        existing.setDocumentUrl(entity.getDocumentUrl());
        PracticalExperience saved = ppDataService.savePracticalExperience(existing);
        String details = diff.hasChanges() ? entity.getOrganizationName() + " | " + diff.build() : entity.getOrganizationName();
        notifyChange(teacherId, "оновлено", "пп.20 Практ. досвід", details);
        return saved;
    }

    @DeleteMapping("/practical-experience/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePracticalExperience(
            @PathVariable Long teacherId,
            @PathVariable Long id) throws AccessDeniedException {
        checkAccess(teacherId);
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PP_PRACTICAL_EXPERIENCE, id);
        ppDataService.deletePracticalExperienceById(id);
        notifyChange(teacherId, "видалено", "пп.20 Практ. досвід", "запис #" + id);
    }

    // =====================================================================
    // AI валідація даних п.38
    // =====================================================================

    @GetMapping("/ppdata/ai-status")
    public Map<String, Boolean> aiStatus() {
        return Map.of("available", ppDataValidationService != null);
    }

    @PostMapping("/ppdata/validate")
    public PpDataValidationResponse validatePpData(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        if (ppDataValidationService == null) {
            throw new RuntimeException("AI-валідація недоступна");
        }
        return ppDataValidationService.validateAll(teacherId);
    }

    @PostMapping("/ppdata/validate/{entityType}/{entityId}")
    public PpDataValidationResponse.PpDataValidationItem validateSinglePpData(
            @PathVariable Long teacherId,
            @PathVariable String entityType,
            @PathVariable Long entityId) throws AccessDeniedException {
        checkAccess(teacherId);
        if (ppDataValidationService == null) {
            throw new RuntimeException("AI-валідація недоступна");
        }
        return ppDataValidationService.validateSingleEntry(teacherId, entityType, entityId);
    }

    @GetMapping("/ppdata/validation-history")
    public List<Map<String, Object>> validationHistory(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        if (ppDataValidationService == null) {
            return List.of();
        }
        return ppDataValidationService.getValidationHistory(teacherId);
    }

    /**
     * Отримати останні статуси валідації для всіх записів викладача.
     * Повертає Map: "entityType:entityId" → { status, reasoning }
     */
    @GetMapping("/ppdata/validation-statuses")
    public Map<String, Map<String, String>> validationStatuses(
            @PathVariable Long teacherId) throws AccessDeniedException {
        checkAccess(teacherId);
        if (ppDataValidationService == null) {
            return Map.of();
        }
        return ppDataValidationService.getLatestStatuses(teacherId);
    }

    @GetMapping("/ppdata/validation-session/{sessionId}")
    public PpDataValidationResponse validationSession(
            @PathVariable Long teacherId,
            @PathVariable String sessionId) throws AccessDeniedException {
        checkAccess(teacherId);
        if (ppDataValidationService == null) {
            throw new RuntimeException("AI-валідація недоступна");
        }
        return ppDataValidationService.getSessionResults(sessionId);
    }
}
