package ua.edu.teacherlicence.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.service.AchievementService;
import ua.edu.teacherlicence.achievement.service.ComplianceService;
import ua.edu.teacherlicence.department.dto.DepartmentComplianceSummaryDto;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.service.DepartmentComplianceService;
import ua.edu.teacherlicence.department.service.DepartmentService;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.service.PublicationService;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.service.QualificationService;
import ua.edu.teacherlicence.teacher.model.CareerRecord;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.CareerRecordRepository;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Будує smart-контекст для AI-чату на основі повідомлення користувача.
 * Контекст адаптується до запиту: загальна статистика + деталі тільки по згаданих сутностях.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
@Transactional(readOnly = true)
public class AiContextService {

    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository academicTitleRepository;
    private final ComplianceService complianceService;
    private final DepartmentComplianceService departmentComplianceService;
    private final DepartmentService departmentService;
    private final AchievementService achievementService;
    private final QualificationService qualificationService;
    private final PublicationService publicationService;
    private final LanguageSkillRepository languageSkillRepository;
    private final CareerRecordRepository careerRecordRepository;

    /**
     * Компактний контекст для tool-based AI chat.
     *
     * Містить лише overview (загальну статистику + список кафедр з короткою статистикою).
     * Деталі по викладачах, досягненнях, публікаціях AI отримує самостійно через @Tool методи
     * (див. {@link AiToolsService}). Це радикально зменшує шум у контексті та дає AI точні
     * актуальні дані на вимогу, замість дампу всіх 150+ викладачів у кожне повідомлення.
     */
    public String buildCompactContext() {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== ОГЛЯД СИСТЕМИ (").append(LocalDate.now()).append(") ===\n\n");

        List<DepartmentComplianceSummaryDto> deptSummaries = departmentComplianceService.getAllSummaries();
        List<ComplianceReportDto> allReports = complianceService.checkComplianceAll();
        List<Teacher> allTeachers = teacherRepository.findAll();

        appendOverallStats(ctx, allTeachers, allReports, deptSummaries);
        appendDepartmentSummaries(ctx, deptSummaries);

        return ctx.toString();
    }

    /**
     * Побудувати контекст відповідно до повідомлення користувача
     */
    public String buildContext(String userMessage) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== АКТУАЛЬНІ ДАНІ СИСТЕМИ (").append(LocalDate.now()).append(") ===\n\n");

        List<DepartmentComplianceSummaryDto> deptSummaries = departmentComplianceService.getAllSummaries();
        List<ComplianceReportDto> allReports = complianceService.checkComplianceAll();
        List<Teacher> allTeachers = teacherRepository.findAll();

        // 1. Завжди: загальна статистика
        appendOverallStats(ctx, allTeachers, allReports, deptSummaries);

        // 2. Завжди: кафедри (компактно)
        appendDepartmentSummaries(ctx, deptSummaries);

        // 3. Аналіз запиту: конкретний викладач?
        List<Teacher> matchedTeachers = findMentionedTeachers(userMessage, allTeachers);
        if (!matchedTeachers.isEmpty()) {
            appendTeacherDetails(ctx, matchedTeachers, allReports);
        }

        // 4. Аналіз запиту: конкретна кафедра?
        DepartmentComplianceSummaryDto matchedDept = findMentionedDepartment(userMessage, deptSummaries);
        if (matchedDept != null) {
            appendDepartmentTeachers(ctx, matchedDept, allTeachers, allReports);
        }

        // 5. Загальний запит (не конкретний викладач/кафедра) — компактний список ВСІХ викладачів
        if (matchedTeachers.isEmpty() && matchedDept == null) {
            appendAllTeachersCompact(ctx, allTeachers, allReports);
        }

        // 6. Питання про кваліфікацію/курси: дані всіх викладачів
        if (isQualificationQuestion(userMessage) && matchedTeachers.isEmpty()) {
            appendAllQualifications(ctx, allTeachers);
        }

        // 7. Питання про мови/сертифікати: дані всіх викладачів
        if (isLanguageQuestion(userMessage) && matchedTeachers.isEmpty()) {
            appendAllLanguageSkills(ctx, allTeachers);
        }

        return ctx.toString();
    }

    // ── Загальна статистика ─────────────────────────────────────────────

    private void appendOverallStats(StringBuilder ctx, List<Teacher> teachers,
                                     List<ComplianceReportDto> reports,
                                     List<DepartmentComplianceSummaryDto> depts) {
        long mainCount = teachers.stream()
                .filter(t -> "MAIN".equals(t.getEmploymentType())).count();

        long compliant = reports.stream()
                .filter(r -> r.getStatus() == ComplianceStatus.COMPLIANT).count();
        long warning = reports.stream()
                .filter(r -> r.getStatus() == ComplianceStatus.WARNING).count();
        long nonCompliant = reports.stream()
                .filter(r -> r.getStatus() == ComplianceStatus.NON_COMPLIANT).count();
        long exempt = reports.stream()
                .filter(r -> r.getStatus() == ComplianceStatus.EXEMPT).count();

        long goodDepts = depts.stream().filter(d -> "GOOD".equals(d.getOverallStatus())).count();
        long warningDepts = depts.stream().filter(d -> "WARNING".equals(d.getOverallStatus())).count();
        long criticalDepts = depts.stream().filter(d -> "CRITICAL".equals(d.getOverallStatus())).count();

        ctx.append("--- ЗАГАЛЬНА СТАТИСТИКА ---\n");
        ctx.append("Викладачів: ").append(teachers.size())
                .append(" (основне м.р.: ").append(mainCount)
                .append(", сумісники: ").append(teachers.size() - mainCount).append(")\n");
        ctx.append("п.38: відповідають=").append(compliant)
                .append(", попередження=").append(warning)
                .append(", не відповідають=").append(nonCompliant)
                .append(", звільнені=").append(exempt).append("\n");
        ctx.append("Кафедр: ").append(depts.size())
                .append(" (відповідають=").append(goodDepts)
                .append(", увага=").append(warningDepts)
                .append(", критичні=").append(criticalDepts).append(")\n\n");
    }

    // ── Кафедри (компактно) ─────────────────────────────────────────────

    private void appendDepartmentSummaries(StringBuilder ctx,
                                            List<DepartmentComplianceSummaryDto> summaries) {
        ctx.append("--- КАФЕДРИ (").append(summaries.size()).append(") ---\n");
        int i = 1;
        for (DepartmentComplianceSummaryDto s : summaries) {
            String p35Mark = s.isPoint35Compliant() ? "✓" : "✗";
            ctx.append(i++).append(". ").append(s.getDepartmentName());
            if (s.getFacultyName() != null) {
                ctx.append(" (").append(s.getFacultyName()).append(")");
            }
            ctx.append(": ").append(s.getTotalTeachers()).append(" викл.");
            ctx.append(", п.35: ").append(s.getWithDegreeAndMainPercent()).append("% ").append(p35Mark);
            ctx.append(", п.38: ")
                    .append(s.getPoint38Compliant()).append("✓ ")
                    .append(s.getPoint38Warning()).append("⚠ ")
                    .append(s.getPoint38NonCompliant()).append("✗ ")
                    .append(s.getPoint38Exempt()).append("~");
            ctx.append(", Статус: ").append(s.getOverallStatus()).append("\n");
        }
        ctx.append("\n");
    }

    // ── Деталі викладачів (тільки згаданих) ─────────────────────────────

    private void appendTeacherDetails(StringBuilder ctx, List<Teacher> teachers,
                                       List<ComplianceReportDto> allReports) {
        Map<Long, ComplianceReportDto> reportMap = allReports.stream()
                .collect(Collectors.toMap(ComplianceReportDto::getTeacherId, r -> r, (a, b) -> a));

        for (Teacher t : teachers) {
            String fullName = t.getLastName() + " " + t.getFirstName()
                    + (t.getPatronymic() != null ? " " + t.getPatronymic() : "");

            ctx.append("--- ВИКЛАДАЧ: ").append(fullName).append(" ---\n");
            ctx.append("  Кафедра: ").append(t.getDepartment() != null ? t.getDepartment().getName() : "—").append("\n");
            if (t.getDateOfBirth() != null) {
                int age = java.time.Period.between(t.getDateOfBirth(), LocalDate.now()).getYears();
                ctx.append("  Дата народження: ")
                        .append(t.getDateOfBirth().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                        .append(" (вік: ").append(age).append(")\n");
            }
            if (t.getMilitaryRank() != null && !t.getMilitaryRank().isBlank()) {
                ctx.append("  Військове звання: ").append(t.getMilitaryRank()).append("\n");
            }
            ctx.append("  Посада: ").append(nvl(teacherPositionService.getEffectivePosition(t)))
                    .append(", ").append(nvl(t.getEmploymentType())).append("\n");
            // Усі ступені (зі списку academic_degrees) — кожен на своєму рядку.
            appendAllDegreesDetailed(ctx, t, "  Ступінь: ");
            // Усі вчені звання (зі списку academic_titles).
            appendAllTitlesDetailed(ctx, t, "  Звання: ");
            if (t.getUniversity() != null) {
                ctx.append("  Освіта: ").append(t.getUniversity());
                if (t.getUniversityGraduationYear() != null) ctx.append(", ").append(t.getUniversityGraduationYear()).append(" р.");
                if (t.getUniversitySpeciality() != null) ctx.append(", спец.: ").append(t.getUniversitySpeciality());
                ctx.append("\n");
            }
            ctx.append("  Стаж: ").append(t.getExperienceStartDate() != null
                    ? java.time.Period.between(t.getExperienceStartDate(), java.time.LocalDate.now()).getYears() + " р."
                    : "—").append("\n");
            ctx.append("  УБД: ").append(t.isCombatVeteranStatus() ? "так" : "ні");
            if (t.isCombatVeteranStatus() && t.getCombatVeteranDoc() != null) {
                ctx.append(", ").append(t.getCombatVeteranDoc());
                if (t.getCombatVeteranDocDate() != null) ctx.append(" від ").append(t.getCombatVeteranDocDate());
                if (t.getCombatVeteranDocIssuedBy() != null) ctx.append(", видано: ").append(t.getCombatVeteranDocIssuedBy());
            }
            ctx.append("\n");

            ComplianceReportDto report = reportMap.get(t.getId());
            if (report != null) {
                ctx.append("  п.38: ").append(report.getStatus())
                        .append(" (").append(report.getUniqueTypeCount()).append("/4 типів, ")
                        .append(report.getAchievementCount()).append(" досягнень)");
                if (report.getExemptionReason() != null) {
                    ctx.append(" — ").append(report.getExemptionReason());
                }
                ctx.append("\n");
                if (report.getAchievementTypes() != null && !report.getAchievementTypes().isEmpty()) {
                    ctx.append("  Типи: ").append(String.join(", ", report.getAchievementTypes())).append("\n");
                }
            }

            // Досягнення
            List<Achievement> achievements = achievementService.findByTeacherId(t.getId());
            if (!achievements.isEmpty()) {
                ctx.append("  Досягнення:\n");
                for (Achievement a : achievements) {
                    ctx.append("    - ").append(a.getAchievementType().name())
                            .append(": ").append(truncate(a.getTitle(), 80));
                    if (a.getDateAchieved() != null) {
                        ctx.append(" (").append(a.getDateAchieved()).append(")");
                    }
                    ctx.append("\n");
                }
            }

            // Підвищення кваліфікації
            List<QualificationImprovement> qualifications = qualificationService.findByTeacherId(t.getId());
            if (!qualifications.isEmpty()) {
                ctx.append("  Підвищення кваліфікації:\n");
                for (QualificationImprovement q : qualifications) {
                    ctx.append("    - ").append(truncate(q.getTitle(), 80));
                    ctx.append(" (").append(nvl(q.getOrganization())).append(")");
                    if (q.getStartDate() != null && q.getEndDate() != null) {
                        ctx.append(" ").append(q.getStartDate()).append("—").append(q.getEndDate());
                    }
                    if (q.getHours() != null) {
                        ctx.append(", ").append(q.getHours()).append(" год.");
                    }
                    if (q.getCertificateNumber() != null) {
                        ctx.append(", серт.: ").append(q.getCertificateNumber());
                        if (q.getCertificateDate() != null) {
                            ctx.append(" від ").append(q.getCertificateDate());
                        }
                    }
                    ctx.append("\n");
                }
            }

            // Публікації
            List<Publication> publications = publicationService.findByTeacherId(t.getId());
            if (!publications.isEmpty()) {
                ctx.append("  Публікації (").append(publications.size()).append("):\n");
                for (Publication p : publications) {
                    ctx.append("    - ").append(truncate(p.getTitle(), 80));
                    if (p.getJournalName() != null) ctx.append(" // ").append(p.getJournalName());
                    if (p.getYear() != null) ctx.append(", ").append(p.getYear());
                    if (p.getArticleCategory() != null) ctx.append(" [").append(p.getArticleCategory()).append("]");
                    if (p.getPpType() != null) ctx.append(" (").append(p.getPpType().name()).append(")");
                    if (Boolean.TRUE.equals(p.getFieldRelevant())) ctx.append(" ✓фахова");
                    ctx.append("\n");
                }
            }

            // Мовні навички
            List<LanguageSkill> languages = languageSkillRepository.findByTeacherId(t.getId());
            if (!languages.isEmpty()) {
                ctx.append("  Мовні навички:\n");
                for (LanguageSkill ls : languages) {
                    ctx.append("    - ").append(nvl(ls.getLanguage()))
                            .append(": рівень ").append(nvl(ls.getLevel()));
                    if (ls.getCertificateNumber() != null) {
                        ctx.append(", серт. №").append(ls.getCertificateNumber());
                    }
                    if (ls.getCertificateDate() != null) {
                        ctx.append(" від ").append(ls.getCertificateDate());
                    }
                    if (ls.getCertificateOrganization() != null) {
                        ctx.append(", ").append(ls.getCertificateOrganization());
                    }
                    if (ls.getCertificateDetails() != null && !ls.getCertificateDetails().isBlank()) {
                        ctx.append(" (").append(ls.getCertificateDetails()).append(")");
                    }
                    ctx.append("\n");
                }
            }

            // Послужний список
            List<CareerRecord> careers = careerRecordRepository.findByTeacherId(t.getId());
            if (!careers.isEmpty()) {
                ctx.append("  Послужний список:\n");
                for (CareerRecord cr : careers) {
                    ctx.append("    - ").append(nvl(cr.getPosition()));
                    if (cr.getOrganization() != null) ctx.append(", ").append(cr.getOrganization());
                    if (cr.getStartDate() != null) ctx.append(" (").append(cr.getStartDate());
                    if (cr.getEndDate() != null) ctx.append("—").append(cr.getEndDate());
                    else if (cr.getStartDate() != null) ctx.append("—т.ч.");
                    if (cr.getStartDate() != null) ctx.append(")");
                    ctx.append("\n");
                }
            }

            // Ідентифікатори
            if (t.getOrcidId() != null || t.getScopusId() != null || t.getWosId() != null || t.getGoogleScholarUrl() != null || t.getEmail() != null) {
                ctx.append("  Ідентифікатори:");
                if (t.getOrcidId() != null) ctx.append(" ORCID:").append(t.getOrcidId());
                if (t.getScopusId() != null) ctx.append(" Scopus:").append(t.getScopusId());
                if (t.getWosId() != null) ctx.append(" WoS:").append(t.getWosId());
                if (t.getGoogleScholarUrl() != null) ctx.append(" Scholar:").append(t.getGoogleScholarUrl());
                if (t.getEmail() != null) ctx.append(" Email:").append(t.getEmail());
                ctx.append("\n");
            }

            ctx.append("\n");
        }
    }

    // ── Викладачі кафедри ───────────────────────────────────────────────

    private void appendDepartmentTeachers(StringBuilder ctx,
                                           DepartmentComplianceSummaryDto dept,
                                           List<Teacher> allTeachers,
                                           List<ComplianceReportDto> allReports) {
        Map<Long, ComplianceReportDto> reportMap = allReports.stream()
                .collect(Collectors.toMap(ComplianceReportDto::getTeacherId, r -> r, (a, b) -> a));

        List<Teacher> deptTeachers = allTeachers.stream()
                .filter(t -> t.getDepartment() != null && t.getDepartment().getId().equals(dept.getDepartmentId()))
                .toList();

        ctx.append("--- ВИКЛАДАЧІ КАФЕДРИ: ").append(dept.getDepartmentName())
                .append(" (").append(deptTeachers.size()).append(") ---\n");

        for (Teacher t : deptTeachers) {
            String shortName = t.getLastName()
                    + (t.getFirstName() != null && !t.getFirstName().isEmpty()
                        ? " " + t.getFirstName().charAt(0) + "." : "")
                    + (t.getPatronymic() != null && !t.getPatronymic().isEmpty()
                        ? t.getPatronymic().charAt(0) + "." : "");

            ComplianceReportDto report = reportMap.get(t.getId());
            String status = report != null ? report.getStatus().name() : "—";
            int types = report != null ? report.getUniqueTypeCount() : 0;

            ctx.append("  ").append(shortName)
                    .append(" — ").append(nvl(teacherPositionService.getEffectivePosition(t)))
                    .append(", ").append(nvl(t.getEmploymentType()))
                    .append(", ступінь: ").append(joinDegrees(t))
                    .append(", звання: ").append(joinTitles(t))
                    .append(", п.38: ").append(status).append(" (").append(types).append("/4)");

            if (report != null && report.getExemptionReason() != null) {
                ctx.append(" — ").append(report.getExemptionReason());
            }
            ctx.append("\n");
        }
        ctx.append("\n");
    }

    // ── Компактний список ВСІХ викладачів ──────────────────────────────

    private void appendAllTeachersCompact(StringBuilder ctx, List<Teacher> allTeachers,
                                           List<ComplianceReportDto> allReports) {
        Map<Long, ComplianceReportDto> reportMap = allReports.stream()
                .collect(Collectors.toMap(ComplianceReportDto::getTeacherId, r -> r, (a, b) -> a));

        ctx.append("--- ВСІ ВИКЛАДАЧІ (").append(allTeachers.size()).append(") ---\n");
        for (Teacher t : allTeachers) {
            String fullName = t.getLastName()
                    + (t.getFirstName() != null ? " " + t.getFirstName() : "")
                    + (t.getPatronymic() != null ? " " + t.getPatronymic() : "");

            ComplianceReportDto report = reportMap.get(t.getId());
            String p38 = report != null ? report.getStatus().name() : "—";

            ctx.append("  ").append(fullName);
            if (t.getDateOfBirth() != null) {
                int age = java.time.Period.between(t.getDateOfBirth(), LocalDate.now()).getYears();
                ctx.append(" | ")
                        .append(t.getDateOfBirth().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                        .append(" (").append(age).append(" р.)");
            }
            ctx.append(" | ").append(nvl(teacherPositionService.getEffectivePosition(t)));
            ctx.append(" | ").append(nvl(t.getEmploymentType()));
            ctx.append(" | каф: ").append(t.getDepartment() != null ? t.getDepartment().getName() : "—");
            ctx.append(" | ступінь: ").append(joinDegrees(t));
            ctx.append(" | звання: ").append(joinTitles(t));
            if (t.getMilitaryRank() != null && !t.getMilitaryRank().isBlank()) {
                ctx.append(" | в/з: ").append(t.getMilitaryRank());
            }
            ctx.append(" | стаж: ").append(t.getExperienceStartDate() != null
                    ? java.time.Period.between(t.getExperienceStartDate(), LocalDate.now()).getYears() + "р"
                    : "—");
            if (t.getUniversity() != null) {
                ctx.append(" | освіта: ").append(t.getUniversity());
                if (t.getUniversitySpeciality() != null) ctx.append(", спец.: ").append(t.getUniversitySpeciality());
                if (t.getUniversityGraduationYear() != null) ctx.append(" (").append(t.getUniversityGraduationYear()).append(")");
            }
            // Спец. дисертації + теми — для всіх ступенів зі списку
            String dissSpecs = joinDissertationSpecs(t);
            if (!dissSpecs.isEmpty()) ctx.append(" | спец. дисертації: ").append(dissSpecs);
            String dissTopics = joinDissertationTopics(t);
            if (!dissTopics.isEmpty()) ctx.append(" | тема дисертації: ").append(truncate(dissTopics, 100));
            ctx.append(" | УБД: ").append(t.isCombatVeteranStatus() ? "так" : "ні");
            if (t.isCombatVeteranStatus() && t.getCombatExperienceDates() != null) {
                ctx.append(" (").append(t.getCombatExperienceDates()).append(")");
            }
            // Publications count
            int pubCount = publicationService.findByTeacherId(t.getId()).size();
            if (pubCount > 0) ctx.append(" | публікацій: ").append(pubCount);
            ctx.append(" | п.38: ").append(p38);
            if (report != null) {
                ctx.append(" (").append(report.getUniqueTypeCount()).append("/4)");
                if (report.getExemptionReason() != null) {
                    ctx.append(" — ").append(shortenExemption(report.getExemptionReason()));
                }
            }
            // ORCID, Scopus, email
            if (t.getOrcidId() != null && !t.getOrcidId().isBlank()) ctx.append(" | ORCID: ").append(t.getOrcidId());
            if (t.getScopusId() != null && !t.getScopusId().isBlank()) ctx.append(" | Scopus: ").append(t.getScopusId());
            if (t.getEmail() != null && !t.getEmail().isBlank()) ctx.append(" | email: ").append(t.getEmail());
            ctx.append("\n");
        }
        ctx.append("\n");
    }

    // ── Підвищення кваліфікації (всі викладачі) ─────────────────────────

    private void appendAllQualifications(StringBuilder ctx, List<Teacher> allTeachers) {
        ctx.append("--- ПІДВИЩЕННЯ КВАЛІФІКАЦІЇ (всі викладачі) ---\n");
        boolean hasAny = false;
        for (Teacher t : allTeachers) {
            List<QualificationImprovement> quals = qualificationService.findByTeacherId(t.getId());
            if (!quals.isEmpty()) {
                hasAny = true;
                String shortName = t.getLastName() + " " + t.getFirstName().charAt(0) + "."
                        + (t.getPatronymic() != null ? t.getPatronymic().charAt(0) + "." : "");
                for (QualificationImprovement q : quals) {
                    ctx.append("  ").append(shortName).append(": ")
                            .append(truncate(q.getTitle(), 80))
                            .append(" (").append(nvl(q.getOrganization())).append(")");
                    if (q.getStartDate() != null && q.getEndDate() != null) {
                        ctx.append(" ").append(q.getStartDate()).append("—").append(q.getEndDate());
                    }
                    if (q.getHours() != null) {
                        ctx.append(", ").append(q.getHours()).append(" год.");
                    }
                    ctx.append("\n");
                }
            }
        }
        if (!hasAny) {
            ctx.append("  Немає даних про підвищення кваліфікації\n");
        }
        ctx.append("\n");
    }

    // ── Мовні навички (всі викладачі) ─────────────────────────────────

    private void appendAllLanguageSkills(StringBuilder ctx, List<Teacher> allTeachers) {
        ctx.append("--- МОВНІ НАВИЧКИ / СЕРТИФІКАТИ (всі викладачі) ---\n");
        ctx.append("  Формат рівня СМР/STANAG: 4 цифри = аудіювання/говоріння/читання/письмо\n");
        ctx.append("  Рівні: 0, 0+, 1, 1+, 2, 2+, 3, 3+\n");
        boolean hasAny = false;
        for (Teacher t : allTeachers) {
            List<LanguageSkill> skills = languageSkillRepository.findByTeacherId(t.getId());
            if (!skills.isEmpty()) {
                hasAny = true;
                String shortName = t.getLastName() + " " + t.getFirstName().charAt(0) + "."
                        + (t.getPatronymic() != null ? t.getPatronymic().charAt(0) + "." : "");
                for (LanguageSkill ls : skills) {
                    ctx.append("  ").append(shortName).append(": ")
                            .append(nvl(ls.getLanguage()))
                            .append(" — рівень ").append(nvl(ls.getLevel()));
                    if (ls.getCertificateNumber() != null) {
                        ctx.append(", серт. №").append(ls.getCertificateNumber());
                    }
                    if (ls.getCertificateDate() != null) {
                        ctx.append(" від ").append(ls.getCertificateDate());
                    }
                    if (ls.getCertificateOrganization() != null) {
                        ctx.append(", ").append(ls.getCertificateOrganization());
                    }
                    if (ls.getCertificateDetails() != null && !ls.getCertificateDetails().isBlank()) {
                        ctx.append(" (").append(ls.getCertificateDetails()).append(")");
                    }
                    ctx.append("\n");
                }
            }
        }
        if (!hasAny) {
            ctx.append("  Немає даних про мовні навички\n");
        }
        ctx.append("\n");
    }

    // ── Пошук сутностей у повідомленні ──────────────────────────────────

    private List<Teacher> findMentionedTeachers(String message, List<Teacher> allTeachers) {
        String lower = message.toLowerCase();
        return allTeachers.stream()
                .filter(t -> t.getLastName() != null && lower.contains(t.getLastName().toLowerCase()))
                .collect(Collectors.toList());
    }

    private DepartmentComplianceSummaryDto findMentionedDepartment(String message,
                                                                     List<DepartmentComplianceSummaryDto> summaries) {
        String lower = message.toLowerCase();
        return summaries.stream()
                .filter(d -> {
                    String deptName = d.getDepartmentName().toLowerCase();
                    // Будь-яке значуще слово з назви кафедри (>4 букв)
                    return Arrays.stream(deptName.split("\\s+"))
                            .filter(w -> w.length() > 4)
                            .anyMatch(lower::contains);
                })
                .findFirst().orElse(null);
    }

    private boolean isQualificationQuestion(String message) {
        String lower = message.toLowerCase();
        return lower.contains("кваліфікац") || lower.contains("курс")
                || lower.contains("стажуванн") || lower.contains("підвищенн")
                || lower.contains("сертифікат") || lower.contains("nato")
                || lower.contains("нато") || lower.contains("oberammergau")
                || lower.contains("обераммергау") || lower.contains("ccdcoe")
                || lower.contains("sans") || lower.contains("навчанн");
    }

    private boolean isLanguageQuestion(String message) {
        String lower = message.toLowerCase();
        return lower.contains("мов") || lower.contains("англійськ")
                || lower.contains("німецьк") || lower.contains("французьк")
                || lower.contains("stanag") || lower.contains("смр")
                || lower.contains("сmp") || lower.contains("рівень мов")
                || lower.contains("2222") || lower.contains("1111")
                || lower.contains("мовн") || lower.contains("language");
    }

    // ── Утиліти ─────────────────────────────────────────────────────────

    /** Усі ступені одним рядком через "; ". Для inline-блоків. */
    private String joinDegrees(Teacher t) {
        var list = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        if (list.isEmpty()) return "—";
        return list.stream()
                .map(d -> {
                    String s = d.getDegree() != null ? d.getDegree() : "";
                    if (d.getSpeciality() != null && !d.getSpeciality().isBlank())
                        s += " (" + d.getSpeciality() + ")";
                    return s;
                })
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("; "));
    }

    /** Усі звання одним рядком через "; ". */
    private String joinTitles(Teacher t) {
        var list = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
        if (list.isEmpty()) return "—";
        return list.stream()
                .map(at -> at.getTitleName() != null ? at.getTitleName() : "")
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("; "));
    }

    /** Усі спеціальності дисертацій (з усіх ступенів). */
    private String joinDissertationSpecs(Teacher t) {
        var list = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        if (list.isEmpty()) return "";
        return list.stream()
                .map(d -> d.getSpeciality() != null ? d.getSpeciality() : "")
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining("; "));
    }

    /** Усі теми дисертацій (з усіх ступенів). */
    private String joinDissertationTopics(Teacher t) {
        var list = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        if (list.isEmpty()) return "";
        return list.stream()
                .map(d -> d.getDissertationTopic() != null ? d.getDissertationTopic() : "")
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    /** Детальний block-вивід ступенів — кожен на своєму рядку (для повного профілю). */
    private void appendAllDegreesDetailed(StringBuilder ctx, Teacher t, String prefix) {
        var list = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        if (list.isEmpty()) {
            ctx.append(prefix).append("—\n");
            return;
        }
        for (var d : list) {
            if (d.getDegree() == null || d.getDegree().isBlank()) continue;
            ctx.append(prefix).append(d.getDegree());
            if (d.getSpeciality() != null && !d.getSpeciality().isBlank())
                ctx.append(", спец.: ").append(d.getSpeciality());
            if (d.getDissertationTopic() != null && !d.getDissertationTopic().isBlank())
                ctx.append(", тема: ").append(truncate(d.getDissertationTopic(), 80));
            if (d.getDiplomaDate() != null) ctx.append(" (диплом від ").append(d.getDiplomaDate()).append(")");
            ctx.append("\n");
        }
    }

    /** Детальний block-вивід звань — кожне на своєму рядку. */
    private void appendAllTitlesDetailed(StringBuilder ctx, Teacher t, String prefix) {
        var list = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
        if (list.isEmpty()) {
            ctx.append(prefix).append("—\n");
            return;
        }
        for (var at : list) {
            if (at.getTitleName() == null || at.getTitleName().isBlank()) continue;
            ctx.append(prefix).append(at.getTitleName());
            if (at.getAttestatDate() != null) ctx.append(" (атестат від ").append(at.getAttestatDate()).append(")");
            ctx.append("\n");
        }
    }

    private String nvl(String value) {
        return value != null && !value.isBlank() ? value : "—";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "—";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String shortenName(String fullName) {
        if (fullName == null) return "—";
        String[] parts = fullName.split("\\s+");
        if (parts.length >= 3) {
            return parts[0] + " " + parts[1].charAt(0) + "." + parts[2].charAt(0) + ".";
        } else if (parts.length == 2) {
            return parts[0] + " " + parts[1].charAt(0) + ".";
        }
        return fullName;
    }

    private String shortenExemption(String reason) {
        if (reason == null) return "";
        if (reason.contains("менше")) return "стаж<3р";
        if (reason.contains("сумісни")) return "сумісник";
        return reason.length() > 20 ? reason.substring(0, 20) : reason;
    }
}
