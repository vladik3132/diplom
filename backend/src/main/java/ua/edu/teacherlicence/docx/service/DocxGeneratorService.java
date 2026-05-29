package ua.edu.teacherlicence.docx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.docx.model.DocxTemplate;
import ua.edu.teacherlicence.docx.repository.DocxTemplateRepository;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.AcademicTitle;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking;
import ua.edu.teacherlicence.teacher.util.AcademicTitleRanking;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.repository.AchievementRepository;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocxGeneratorService {

    private final DocxTemplateRepository templateRepository;
    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final PublicationRepository publicationRepository;
    private final AchievementRepository achievementRepository;
    private final QualificationImprovementRepository qualificationRepository;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final AcademicTitleRepository academicTitleRepository;
    private final ObjectMapper objectMapper;

    public List<DocxTemplate> findAllTemplates() {
        return templateRepository.findAll();
    }

    public DocxTemplate findTemplateById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Шаблон не знайдено: " + id));
    }

    @Transactional
    public DocxTemplate saveTemplate(DocxTemplate template) {
        return templateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    /**
     * Генерує DOCX файл на основі шаблону та даних викладача.
     * Шаблон містить JSON-конфігурацію з блоками, кожен блок описує тип контенту.
     */
    public byte[] generateDocument(Long templateId, Long teacherId) throws IOException {
        DocxTemplate template = findTemplateById(templateId);
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Викладач не знайдений: " + teacherId));

        List<Publication> publications = publicationRepository.findByTeacherId(teacherId);
        List<Achievement> achievements = achievementRepository.findByTeacherId(teacherId);
        List<QualificationImprovement> qualifications = qualificationRepository.findByTeacherId(teacherId);

        List<Map<String, Object>> blocks = objectMapper.readValue(
                template.getTemplateConfig(),
                new TypeReference<>() {}
        );

        try (XWPFDocument document = new XWPFDocument()) {
            for (Map<String, Object> block : blocks) {
                String type = (String) block.get("type");
                switch (type) {
                    case "text" -> addTextBlock(document, block, teacher);
                    case "table_publications" -> addPublicationsTable(document, publications);
                    case "table_achievements" -> addAchievementsTable(document, achievements);
                    case "table_qualifications" -> addQualificationsTable(document, qualifications);
                    case "page_break" -> addPageBreak(document);
                    default -> addTextBlock(document, block, teacher);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private void addTextBlock(XWPFDocument document, Map<String, Object> block, Teacher teacher) {
        String content = (String) block.getOrDefault("content", "");
        content = resolvePlaceholders(content, teacher);

        XWPFParagraph paragraph = document.createParagraph();

        String align = (String) block.getOrDefault("align", "LEFT");
        paragraph.setAlignment(ParagraphAlignment.valueOf(align));

        XWPFRun run = paragraph.createRun();
        run.setText(content);

        Boolean bold = (Boolean) block.getOrDefault("bold", false);
        run.setBold(bold);

        Number fontSize = (Number) block.getOrDefault("fontSize", 12);
        run.setFontSize(fontSize.intValue());

        String fontFamily = (String) block.getOrDefault("fontFamily", "Times New Roman");
        run.setFontFamily(fontFamily);
    }

    private String resolvePlaceholders(String text, Teacher teacher) {
        if (text == null) return "";
        String fullName = teacher.getLastName() + " " + teacher.getFirstName()
                + (teacher.getPatronymic() != null ? " " + teacher.getPatronymic() : "");

        var allDegrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());
        var allTitles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(teacher.getId());
        AcademicDegree primaryDegree = AcademicDegreeRanking.primary(allDegrees);
        AcademicTitle primaryTitle = AcademicTitleRanking.primary(allTitles);

        return text
                .replace("{{teacher.fullName}}", fullName)
                .replace("{{teacher.lastName}}", nullSafe(teacher.getLastName()))
                .replace("{{teacher.firstName}}", nullSafe(teacher.getFirstName()))
                .replace("{{teacher.patronymic}}", nullSafe(teacher.getPatronymic()))
                .replace("{{teacher.position}}", nullSafe(teacherPositionService.getEffectivePosition(teacher)))

                // ── Primary (найвищий за рангом) — для backward compat ──
                .replace("{{teacher.degree}}", nullSafe(primaryDegree != null ? primaryDegree.getDegree() : null))
                .replace("{{teacher.title}}", nullSafe(primaryTitle != null ? primaryTitle.getTitleName() : null))
                .replace("{{teacher.degreeDiploma}}", nullSafe(primaryDegree != null ? primaryDegree.getDiploma() : null))
                .replace("{{teacher.degreeDiplomaDate}}", primaryDegree != null && primaryDegree.getDiplomaDate() != null
                        ? formatLocalDate(primaryDegree.getDiplomaDate()) : "")
                .replace("{{teacher.dissertationTopic}}", nullSafe(primaryDegree != null ? primaryDegree.getDissertationTopic() : null))
                .replace("{{teacher.dissertationSpeciality}}", nullSafe(primaryDegree != null ? primaryDegree.getSpeciality() : null))
                .replace("{{teacher.titleAttestat}}", nullSafe(primaryTitle != null ? primaryTitle.getAttestat() : null))
                .replace("{{teacher.titleAttestatDate}}", primaryTitle != null && primaryTitle.getAttestatDate() != null
                        ? formatLocalDate(primaryTitle.getAttestatDate()) : "")

                // ── Усі ступені/звання (списки через '; ') ──
                .replace("{{teacher.degrees}}", joinAllDegreeNames(allDegrees))
                .replace("{{teacher.degrees.full}}", joinAllDegreesFullDetails(allDegrees))
                .replace("{{teacher.titles}}", joinAllTitleNames(allTitles))
                .replace("{{teacher.titles.full}}", joinAllTitlesFullDetails(allTitles))
                .replace("{{teacher.dissertationTopics}}", joinAllDissertationTopics(allDegrees))
                .replace("{{teacher.dissertationSpecialities}}", joinAllDissertationSpecialities(allDegrees))
                .replace("{{teacher.degreeDiplomas}}", joinAllDegreeDiplomas(allDegrees))
                .replace("{{teacher.titleAttestats}}", joinAllTitleAttestats(allTitles))

                // ── Регалії (комбіновано: ступені + звання, тільки назви та скорочено) ──
                .replace("{{teacher.regalia}}", regaliaNames(allDegrees, allTitles))
                .replace("{{teacher.regalia.short}}", regaliaShort(allDegrees, allTitles))

                // ── Освіта (ЗВО) ──
                .replace("{{teacher.university}}", nullSafe(teacher.getUniversity()))
                .replace("{{teacher.universitySpeciality}}", nullSafe(teacher.getUniversitySpeciality()))
                .replace("{{teacher.universityDiploma}}", nullSafe(teacher.getUniversityDiploma()))
                .replace("{{teacher.universityGraduationYear}}", teacher.getUniversityGraduationYear() != null ? teacher.getUniversityGraduationYear().toString() : "")
                .replace("{{teacher.universityDiplomaDate}}", teacher.getUniversityDiplomaDate() != null ? formatLocalDate(teacher.getUniversityDiplomaDate()) : "")

                // ── Бойовий досвід / контакти ──
                .replace("{{teacher.combatVeteranDoc}}", nullSafe(teacher.getCombatVeteranDoc()))
                .replace("{{teacher.combatVeteranDocDate}}", teacher.getCombatVeteranDocDate() != null ? formatLocalDate(teacher.getCombatVeteranDocDate()) : "")
                .replace("{{teacher.combatVeteranDocIssuedBy}}", nullSafe(teacher.getCombatVeteranDocIssuedBy()))
                .replace("{{teacher.email}}", nullSafe(teacher.getEmail()))
                .replace("{{teacher.phone}}", nullSafe(teacher.getPhone()))
                .replace("{{teacher.orcid}}", nullSafe(teacher.getOrcidId()))
                .replace("{{teacher.experience}}", teacher.getExperienceStartDate() != null
                        ? String.valueOf(java.time.Period.between(teacher.getExperienceStartDate(), java.time.LocalDate.now()).getYears())
                        : "");
    }

    // ── Helpers: повні списки регалій ──────────────────────────────

    private String joinAllDegreeNames(List<AcademicDegree> degrees) {
        return degrees.stream()
                .map(AcademicDegree::getDegree)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllDegreesFullDetails(List<AcademicDegree> degrees) {
        List<String> parts = degrees.stream().map(d -> {
            StringBuilder sb = new StringBuilder();
            if (d.getDegree() != null && !d.getDegree().isBlank()) sb.append(d.getDegree());
            if (d.getSpeciality() != null && !d.getSpeciality().isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("спеціальність: ").append(d.getSpeciality());
            }
            if (d.getDiploma() != null && !d.getDiploma().isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("диплом ").append(d.getDiploma());
                if (d.getDiplomaDate() != null) sb.append(" від ").append(formatLocalDate(d.getDiplomaDate()));
            }
            if (d.getDissertationTopic() != null && !d.getDissertationTopic().isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("тема: ").append(d.getDissertationTopic());
            }
            return sb.toString();
        }).filter(s -> !s.isEmpty()).toList();
        return numberedJoin(parts);
    }

    private String joinAllTitleNames(List<AcademicTitle> titles) {
        return titles.stream()
                .map(AcademicTitle::getTitleName)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllTitlesFullDetails(List<AcademicTitle> titles) {
        List<String> parts = titles.stream().map(at -> {
            StringBuilder sb = new StringBuilder();
            if (at.getTitleName() != null && !at.getTitleName().isBlank()) sb.append(at.getTitleName());
            if (at.getAttestat() != null && !at.getAttestat().isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("атестат ").append(at.getAttestat());
                if (at.getAttestatDate() != null) sb.append(" від ").append(formatLocalDate(at.getAttestatDate()));
            }
            return sb.toString();
        }).filter(s -> !s.isEmpty()).toList();
        return numberedJoin(parts);
    }

    /**
     * Якщо у списку 1 елемент — повертає його без нумерації.
     * Якщо ≥2 — повертає формат "1. ...\n2. ...".
     */
    private String numberedJoin(List<String> parts) {
        if (parts == null || parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(parts.get(i));
        }
        return sb.toString();
    }

    private String joinAllDissertationTopics(List<AcademicDegree> degrees) {
        return degrees.stream()
                .map(AcademicDegree::getDissertationTopic)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllDissertationSpecialities(List<AcademicDegree> degrees) {
        return degrees.stream()
                .map(AcademicDegree::getSpeciality)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllDegreeDiplomas(List<AcademicDegree> degrees) {
        return degrees.stream()
                .map(d -> {
                    if (d.getDiploma() == null || d.getDiploma().isBlank()) return null;
                    return d.getDiplomaDate() != null
                            ? d.getDiploma() + " від " + formatLocalDate(d.getDiplomaDate())
                            : d.getDiploma();
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllTitleAttestats(List<AcademicTitle> titles) {
        return titles.stream()
                .map(at -> {
                    if (at.getAttestat() == null || at.getAttestat().isBlank()) return null;
                    return at.getAttestatDate() != null
                            ? at.getAttestat() + " від " + formatLocalDate(at.getAttestatDate())
                            : at.getAttestat();
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String regaliaNames(List<AcademicDegree> degrees, List<AcademicTitle> titles) {
        List<String> parts = new java.util.ArrayList<>();
        degrees.forEach(d -> {
            if (d.getDegree() != null && !d.getDegree().isBlank()) parts.add(d.getDegree());
        });
        titles.forEach(at -> {
            if (at.getTitleName() != null && !at.getTitleName().isBlank()) parts.add(at.getTitleName());
        });
        return String.join(", ", parts);
    }

    private String regaliaShort(List<AcademicDegree> degrees, List<AcademicTitle> titles) {
        List<String> parts = new java.util.ArrayList<>();
        degrees.forEach(d -> {
            String abbr = abbreviateDegree(d.getDegree());
            if (abbr != null && !abbr.isBlank()) parts.add(abbr);
        });
        titles.forEach(at -> {
            String abbr = abbreviateTitle(at.getTitleName());
            if (abbr != null && !abbr.isBlank()) parts.add(abbr);
        });
        return String.join(", ", parts);
    }

    private String abbreviateDegree(String full) {
        if (full == null || full.isBlank()) return null;
        String l = full.toLowerCase();
        String prefix;
        if (l.startsWith("доктор філософії") || l.equalsIgnoreCase("phd")) return "PhD";
        else if (l.startsWith("доктор")) prefix = "д.";
        else if (l.startsWith("кандидат")) prefix = "к.";
        else return full;
        String area;
        if (l.contains("фізико-математичн") || l.contains("фіз.-мат")) area = "ф.-м.н.";
        else if (l.contains("технічн")) area = "т.н.";
        else if (l.contains("військов")) area = "в.н.";
        else if (l.contains("економічн") || l.contains("економ")) area = "е.н.";
        else if (l.contains("філологіч")) area = "філол.н.";
        else if (l.contains("педагогіч")) area = "пед.н.";
        else if (l.contains("психологіч") || l.contains("психолог")) area = "психол.н.";
        else if (l.contains("філософськ") || l.contains("філософ")) area = "філос.н.";
        else if (l.contains("юридичн")) area = "ю.н.";
        else if (l.contains("історичн")) area = "і.н.";
        else if (l.contains("медичн")) area = "м.н.";
        else if (l.contains("хімічн")) area = "х.н.";
        else if (l.contains("біологічн")) area = "б.н.";
        else if (l.contains("географічн")) area = "геогр.н.";
        else if (l.contains("геологічн")) area = "геол.н.";
        else if (l.contains("сільськогосподарськ")) area = "с.-г.н.";
        else if (l.contains("політичн")) area = "політ.н.";
        else if (l.contains("соціолог")) area = "соц.н.";
        else if (l.contains("мистецтвоз")) area = "мист.";
        else if (l.contains("архітектур")) area = "арх.";
        else return full;
        return prefix + area;
    }

    private String abbreviateTitle(String full) {
        if (full == null || full.isBlank()) return null;
        String l = full.toLowerCase();
        if (l.contains("професор")) return "проф.";
        if (l.contains("доцент")) return "доц.";
        if (l.contains("старш") && l.contains("дослід")) return "ст. досл.";
        if (l.contains("старш") && l.contains("науков")) return "с.н.с.";
        return full;
    }

    private String formatLocalDate(java.time.LocalDate date) {
        return String.format("%02d.%02d.%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private void addPublicationsTable(XWPFDocument document, List<Publication> publications) {
        XWPFParagraph title = document.createParagraph();
        XWPFRun titleRun = title.createRun();
        titleRun.setText("Публікації");
        titleRun.setBold(true);
        titleRun.setFontSize(12);

        XWPFTable table = document.createTable(publications.size() + 1, 5);
        setTableCell(table, 0, 0, "№");
        setTableCell(table, 0, 1, "Назва");
        setTableCell(table, 0, 2, "Тип");
        setTableCell(table, 0, 3, "Журнал / Конференція");
        setTableCell(table, 0, 4, "Рік");

        for (int i = 0; i < publications.size(); i++) {
            Publication p = publications.get(i);
            String source = p.getJournalName() != null ? p.getJournalName()
                    : p.getConferenceInfo() != null ? p.getConferenceInfo() : "";
            setTableCell(table, i + 1, 0, String.valueOf(i + 1));
            setTableCell(table, i + 1, 1, nullSafe(p.getTitle()));
            setTableCell(table, i + 1, 2, p.getType() != null ? p.getType().name() : "");
            setTableCell(table, i + 1, 3, source);
            setTableCell(table, i + 1, 4, p.getYear() != null ? p.getYear().toString() : "");
        }
    }

    private void addAchievementsTable(XWPFDocument document, List<Achievement> achievements) {
        XWPFParagraph title = document.createParagraph();
        XWPFRun titleRun = title.createRun();
        titleRun.setText("Досягнення (пункт 38)");
        titleRun.setBold(true);
        titleRun.setFontSize(12);

        XWPFTable table = document.createTable(achievements.size() + 1, 4);
        setTableCell(table, 0, 0, "№");
        setTableCell(table, 0, 1, "Тип");
        setTableCell(table, 0, 2, "Назва");
        setTableCell(table, 0, 3, "Дата");

        for (int i = 0; i < achievements.size(); i++) {
            Achievement a = achievements.get(i);
            setTableCell(table, i + 1, 0, String.valueOf(i + 1));
            setTableCell(table, i + 1, 1, a.getAchievementType() != null ? a.getAchievementType().getDescription() : "");
            setTableCell(table, i + 1, 2, nullSafe(a.getTitle()));
            setTableCell(table, i + 1, 3, a.getDateAchieved() != null ? a.getDateAchieved().toString() : "");
        }
    }

    private void addQualificationsTable(XWPFDocument document, List<QualificationImprovement> qualifications) {
        XWPFParagraph title = document.createParagraph();
        XWPFRun titleRun = title.createRun();
        titleRun.setText("Підвищення кваліфікації");
        titleRun.setBold(true);
        titleRun.setFontSize(12);

        XWPFTable table = document.createTable(qualifications.size() + 1, 5);
        setTableCell(table, 0, 0, "№");
        setTableCell(table, 0, 1, "Назва");
        setTableCell(table, 0, 2, "Організація");
        setTableCell(table, 0, 3, "Період");
        setTableCell(table, 0, 4, "Сертифікат");

        for (int i = 0; i < qualifications.size(); i++) {
            QualificationImprovement q = qualifications.get(i);
            String period = (q.getStartDate() != null ? q.getStartDate().toString() : "") +
                    " — " + (q.getEndDate() != null ? q.getEndDate().toString() : "");
            setTableCell(table, i + 1, 0, String.valueOf(i + 1));
            setTableCell(table, i + 1, 1, nullSafe(q.getTitle()));
            setTableCell(table, i + 1, 2, nullSafe(q.getOrganization()));
            setTableCell(table, i + 1, 3, period);
            setTableCell(table, i + 1, 4, nullSafe(q.getCertificateNumber()));
        }
    }

    private void addPageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setPageBreak(true);
    }

    private void setTableCell(XWPFTable table, int row, int col, String text) {
        XWPFTableRow tableRow = table.getRow(row);
        if (tableRow == null) return;
        XWPFTableCell cell = tableRow.getCell(col);
        if (cell == null) return;
        cell.setText(text != null ? text : "");
    }
}
