package ua.edu.teacherlicence.docx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.repository.AchievementRepository;
import ua.edu.teacherlicence.discipline.model.Discipline;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.DisciplineRepository;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.docx.model.DataFieldKey;
import ua.edu.teacherlicence.docx.model.DocxExportTemplate;
import ua.edu.teacherlicence.docx.repository.DocxExportTemplateRepository;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.AcademicTitle;
import ua.edu.teacherlicence.teacher.model.CareerRecord;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository;
import ua.edu.teacherlicence.teacher.repository.CareerRecordRepository;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking;
import ua.edu.teacherlicence.teacher.util.AcademicTitleRanking;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentExportService {

    private final DocxExportTemplateRepository exportTemplateRepository;
    private final TemplateParserService templateParserService;
    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final PublicationRepository publicationRepository;
    private final AchievementRepository achievementRepository;
    private final QualificationImprovementRepository qualificationRepository;
    private final CareerRecordRepository careerRecordRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final DisciplineRepository disciplineRepository;
    private final TeacherDisciplineRepository teacherDisciplineRepository;
    private final ua.edu.teacherlicence.teacher.repository.EducationRepository educationRepository;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final AcademicTitleRepository academicTitleRepository;
    private final ua.edu.teacherlicence.achievement.service.AchievementValidationService achievementValidationService;
    private final ScientificSupervisionRepository scientificSupervisionRepository;
    private final AttestationActivityRepository attestationActivityRepository;
    private final EditorialActivityRepository editorialActivityRepository;
    private final ExpertCouncilRepository expertCouncilRepository;
    private final InternationalProjectRepository internationalProjectRepository;
    private final ScientificConsultingRepository scientificConsultingRepository;
    private final ForeignLanguageTeachingRepository foreignLanguageTeachingRepository;
    private final OlympiadGuidanceRepository olympiadGuidanceRepository;
    private final MilitaryMissionRepository militaryMissionRepository;
    private final ProfessionalAssociationRepository professionalAssociationRepository;
    private final PracticalExperienceRepository practicalExperienceRepository;
    private final ObjectMapper objectMapper;

    // ── CRUD for export templates ──────────────────────────────────────

    public List<DocxExportTemplate> findAllTemplates() {
        return exportTemplateRepository.findAll();
    }

    public DocxExportTemplate findTemplateById(Long id) {
        return exportTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Export template not found: " + id));
    }

    @Transactional
    public DocxExportTemplate saveTemplate(DocxExportTemplate template) {
        return exportTemplateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        exportTemplateRepository.deleteById(id);
    }

    // ── Export generation ──────────────────────────────────────────────

    public byte[] exportDepartment(Long templateId, Long departmentId) throws IOException {
        return exportDepartments(templateId, List.of(departmentId));
    }

    public byte[] exportByTeacherIds(Long templateId, List<Long> teacherIds) throws IOException {
        List<Teacher> teachers = new ArrayList<>(teacherRepository.findAllById(teacherIds));
        return exportTeachers(templateId, teachers);
    }

    /**
     * Експорт усіх унікальних викладачів, що забезпечують дану освітню програму.
     * Ланцюг: EducationalProgram → Discipline → TeacherDiscipline → Teacher (без дублів).
     */
    public byte[] exportByProgram(Long templateId, Long programId) throws IOException {
        List<Discipline> disciplines = disciplineRepository.findByEducationalProgramId(programId);
        Set<Long> uniqueTeacherIds = disciplines.stream()
                .flatMap(d -> teacherDisciplineRepository.findByDisciplineId(d.getId()).stream())
                .map(td -> td.getTeacher().getId())
                .collect(Collectors.toSet());
        if (uniqueTeacherIds.isEmpty()) {
            throw new RuntimeException("Немає призначених викладачів для цієї освітньої програми");
        }
        List<Teacher> teachers = new ArrayList<>(teacherRepository.findAllById(uniqueTeacherIds));
        return exportTeachers(templateId, teachers);
    }

    public byte[] exportDepartments(Long templateId, List<Long> departmentIds) throws IOException {
        List<Teacher> teachers;
        if (departmentIds == null || departmentIds.isEmpty()) {
            teachers = new ArrayList<>(teacherRepository.findAll());
        } else {
            teachers = new ArrayList<>(teacherRepository.findByDepartmentIdIn(departmentIds));
        }
        return exportTeachers(templateId, teachers);
    }

    private byte[] exportTeachers(Long templateId, List<Teacher> teachers) throws IOException {
        DocxExportTemplate exportTemplate = findTemplateById(templateId);
        Path templatePath = templateParserService.getTemplatePath(exportTemplate.getTemplateFileName());

        // Parse column mappings — support both old (fieldKey) and new (fieldKeys) format
        List<ColumnMapping> mappings = parseColumnMappings(exportTemplate.getColumnMappingsJson());
        teachers.sort(Comparator
                .comparing((Teacher t) -> t.getDepartment() != null ? t.getDepartment().getName() : "")
                .thenComparing((Teacher t) -> !isMilitary(t))       // military first
                .thenComparingInt(this::getPositionRank)
                .thenComparing(Teacher::getLastName, String.CASE_INSENSITIVE_ORDER));

        if (teachers.isEmpty()) {
            throw new RuntimeException("Немає викладачів для експорту");
        }

        // Batch-load related data
        List<Long> teacherIds = teachers.stream().map(Teacher::getId).toList();
        Map<Long, List<Publication>> pubMap = publicationRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(p -> p.getTeacher().getId()));
        Map<Long, List<Achievement>> achMap = achievementRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(a -> a.getTeacher().getId()));
        Map<Long, List<QualificationImprovement>> qualMap = qualificationRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(q -> q.getTeacher().getId()));
        Map<Long, List<CareerRecord>> careerMap = careerRecordRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(c -> c.getTeacher().getId()));
        Map<Long, List<LanguageSkill>> langMap = languageSkillRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(l -> l.getTeacher().getId()));
        Map<Long, List<TeacherDiscipline>> discMap = teacherDisciplineRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(d -> d.getTeacher().getId()));

        // ppData batch-load
        Map<Long, List<ScientificSupervision>> pp6Map = scientificSupervisionRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<AttestationActivity>> pp7Map = attestationActivityRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<EditorialActivity>> pp8Map = editorialActivityRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<ExpertCouncil>> pp9Map = expertCouncilRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<InternationalProject>> pp10Map = internationalProjectRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<ScientificConsulting>> pp11Map = scientificConsultingRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<ForeignLanguageTeaching>> pp13Map = foreignLanguageTeachingRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<OlympiadGuidance>> pp14Map = olympiadGuidanceRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<MilitaryMission>> pp17Map = militaryMissionRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<ProfessionalAssociation>> pp19Map = professionalAssociationRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));
        Map<Long, List<PracticalExperience>> pp20Map = practicalExperienceRepository.findByTeacherIdIn(teacherIds)
                .stream().collect(Collectors.groupingBy(e -> e.getTeacher().getId()));

        // Open template
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(templatePath.toFile()))) {
            int tableIndex = exportTemplate.getTableIndex() != null ? exportTemplate.getTableIndex() : 0;
            int headerRows = exportTemplate.getHeaderRowCount() != null ? exportTemplate.getHeaderRowCount() : 1;

            List<XWPFTable> tables = doc.getTables();
            if (tableIndex >= tables.size()) {
                throw new RuntimeException("Table index " + tableIndex + " not found");
            }

            XWPFTable table = tables.get(tableIndex);
            int totalRows = table.getNumberOfRows();

            // Remove placeholder rows (keep only header rows)
            for (int i = totalRows - 1; i >= headerRows; i--) {
                table.removeRow(i);
            }

            // Get column count from header
            int colCount = table.getRow(0).getTableCells().size();

            // Add a row for each teacher
            for (int ti = 0; ti < teachers.size(); ti++) {
                Teacher teacher = teachers.get(ti);
                XWPFTableRow newRow = table.createRow();

                // Ensure correct number of cells
                while (newRow.getTableCells().size() < colCount) {
                    newRow.addNewTableCell();
                }

                // Fill each cell according to mapping
                for (ColumnMapping mapping : mappings) {
                    int colIdx = mapping.columnIndex();
                    if (colIdx >= colCount) continue;

                    List<String> fieldKeys = mapping.fieldKeys();
                    if (fieldKeys == null || fieldKeys.isEmpty()) continue;

                    // Resolve each field and join non-empty results with newline
                    List<String> parts = new ArrayList<>();
                    for (String fk : fieldKeys) {
                        DataFieldKey key;
                        try {
                            key = DataFieldKey.valueOf(fk);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }

                        Long tid = teacher.getId();
                        TeacherData td = new TeacherData(
                                pubMap.getOrDefault(tid, List.of()), achMap.getOrDefault(tid, List.of()),
                                qualMap.getOrDefault(tid, List.of()), careerMap.getOrDefault(tid, List.of()),
                                langMap.getOrDefault(tid, List.of()), discMap.getOrDefault(tid, List.of()),
                                pp6Map.getOrDefault(tid, List.of()), pp7Map.getOrDefault(tid, List.of()),
                                pp8Map.getOrDefault(tid, List.of()), pp9Map.getOrDefault(tid, List.of()),
                                pp10Map.getOrDefault(tid, List.of()), pp11Map.getOrDefault(tid, List.of()),
                                pp13Map.getOrDefault(tid, List.of()), pp14Map.getOrDefault(tid, List.of()),
                                pp17Map.getOrDefault(tid, List.of()), pp19Map.getOrDefault(tid, List.of()),
                                pp20Map.getOrDefault(tid, List.of()));
                        String value = resolveField(key, teacher, ti + 1, td);

                        if (value != null && !value.isEmpty()) {
                            parts.add(value);
                        }
                    }

                    setCellContent(newRow.getCell(colIdx), String.join("\n", parts));
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Parse column mappings JSON, supporting both old format (fieldKey: string)
     * and new format (fieldKeys: string[]).
     */
    @SuppressWarnings("unchecked")
    private List<ColumnMapping> parseColumnMappings(String json) throws IOException {
        List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<>() {});
        List<ColumnMapping> result = new ArrayList<>();

        for (Map<String, Object> raw : rawList) {
            int columnIndex = ((Number) raw.get("columnIndex")).intValue();
            String headerText = (String) raw.getOrDefault("headerText", "");

            List<String> fieldKeys;
            if (raw.containsKey("fieldKeys") && raw.get("fieldKeys") instanceof List) {
                // New format: fieldKeys is an array
                fieldKeys = ((List<Object>) raw.get("fieldKeys")).stream()
                        .map(String::valueOf)
                        .filter(s -> !s.isEmpty())
                        .toList();
            } else if (raw.containsKey("fieldKey") && raw.get("fieldKey") instanceof String fk && !fk.isEmpty()) {
                // Old format: single fieldKey string → wrap in list
                fieldKeys = List.of(fk);
            } else {
                fieldKeys = List.of();
            }

            result.add(new ColumnMapping(columnIndex, headerText, fieldKeys));
        }
        return result;
    }

    // ── Field resolvers ───────────────────────────────────────────────

    private String resolveField(DataFieldKey key, Teacher t, int rowNum, TeacherData d) {
        List<Publication> pubs = d.pubs(); List<Achievement> achs = d.achs();
        List<QualificationImprovement> quals = d.quals(); List<CareerRecord> careers = d.careers();
        List<LanguageSkill> langs = d.langs(); List<TeacherDiscipline> discs = d.discs();
        return switch (key) {
            // ── Service ──
            case ROW_NUMBER -> String.valueOf(rowNum);

            // ── Presets ──
            case FULL_NAME_DETAILS -> buildFullNameDetails(t);
            case POSITION_EMPLOYMENT -> buildPositionEmployment(t);
            case EDUCATION -> buildEducation(t);
            case EDUCATION_ALL -> buildAllEducations(t);
            case SCIENTIFIC_QUALIFICATION -> buildScientificQualification(t);

            // ── Викладач: Основна інформація ──
            case FULL_NAME -> buildFullName(t);
            case LAST_NAME -> safe(t.getLastName());
            case FIRST_NAME -> safe(t.getFirstName());
            case PATRONYMIC -> safe(t.getPatronymic());
            case DATE_OF_BIRTH -> t.getDateOfBirth() != null
                    ? t.getDateOfBirth().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    : "";
            case MILITARY_RANK -> safe(t.getMilitaryRank());
            case EXPERIENCE_YEARS -> t.getExperienceStartDate() != null
                    ? "стаж " + java.time.Period.between(t.getExperienceStartDate(), java.time.LocalDate.now()).getYears() + " р."
                    : "";
            case POSITION_TITLE -> safe(teacherPositionService.getEffectivePosition(t));
            case POSITION_FULL -> buildPositionFull(t);
            case EMPLOYMENT_TYPE -> "PART_TIME".equals(t.getEmploymentType()) ? "сумісник" : "основне місце роботи";
            case TEACHER_NOTES -> safe(t.getNotes());

            // ── Викладач: Освіта та наука ──
            case ACADEMIC_DEGREE -> safe(primaryDegreeName(t));
            case ACADEMIC_TITLE -> safe(primaryTitleName(t));
            case UNIVERSITY -> safe(t.getUniversity());
            case UNIVERSITY_SPECIALITY -> safe(t.getUniversitySpeciality());
            case UNIVERSITY_DIPLOMA -> safe(t.getUniversityDiploma());
            case UNIVERSITY_GRADUATION_YEAR -> t.getUniversityGraduationYear() != null ? t.getUniversityGraduationYear() + " р." : "";
            case UNIVERSITY_DIPLOMA_DATE -> t.getUniversityDiplomaDate() != null ? formatDate(t.getUniversityDiplomaDate()) : "";
            case DISSERTATION_TOPIC -> safe(primaryDegreeField(t, AcademicDegree::getDissertationTopic));
            case DISSERTATION_SPECIALITY -> safe(primaryDegreeField(t, AcademicDegree::getSpeciality));
            case DEGREE_DIPLOMA -> safe(primaryDegreeField(t, AcademicDegree::getDiploma));
            case DEGREE_DIPLOMA_DATE -> {
                AcademicDegree pd = primaryDegree(t);
                yield pd != null && pd.getDiplomaDate() != null ? formatDate(pd.getDiplomaDate()) : "";
            }
            case TITLE_ATTESTAT -> {
                AcademicTitle pt = primaryTitle(t);
                yield safe(pt != null ? pt.getAttestat() : null);
            }
            case TITLE_ATTESTAT_DATE -> {
                AcademicTitle pt = primaryTitle(t);
                yield pt != null && pt.getAttestatDate() != null ? formatDate(pt.getAttestatDate()) : "";
            }

            // ── Викладач: Освіта та наука (повні списки всіх ступенів/звань) ──
            case ACADEMIC_DEGREES_ALL -> joinAllDegreeNames(t);
            case ACADEMIC_DEGREES_FULL -> joinAllDegreesFullDetails(t);
            case ACADEMIC_TITLES_ALL -> joinAllTitleNames(t);
            case ACADEMIC_TITLES_FULL -> joinAllTitlesFullDetails(t);
            case DISSERTATION_TOPICS_ALL -> joinAllDissertationTopics(t);
            case DISSERTATION_SPECIALITIES_ALL -> joinAllDissertationSpecialities(t);
            case DEGREE_DIPLOMAS_ALL -> joinAllDegreeDiplomas(t);
            case TITLE_ATTESTATS_ALL -> joinAllTitleAttestats(t);
            case REGALIA_NAMES -> regaliaNames(t);
            case REGALIA_SHORT -> regaliaShort(t);

            // ── Викладач: Бойовий досвід ──
            case COMBAT_EXPERIENCE -> buildCombatExperience(t);
            case COMBAT_STATUS -> t.isCombatVeteranStatus() ? "Учасник бойових дій" : "—";
            case COMBAT_DOC -> safe(t.getCombatVeteranDoc());
            case COMBAT_DOC_DATE -> t.getCombatVeteranDocDate() != null ? formatDate(t.getCombatVeteranDocDate()) : "";
            case COMBAT_DOC_ISSUED_BY -> safe(t.getCombatVeteranDocIssuedBy());
            case COMBAT_DATES -> safe(t.getCombatExperienceDates());

            // ── Викладач: Контакти ──
            case EMAIL -> safe(t.getEmail());
            case PHONE -> safe(t.getPhone());
            case ORCID -> safe(t.getOrcidId());
            case SCOPUS_ID -> safe(t.getScopusId());
            case WOS_ID -> safe(t.getWosId());
            case GOOGLE_SCHOLAR -> safe(t.getGoogleScholarUrl());

            // ── Дисципліни ──
            case DISC_ALL -> discs.stream()
                    .map(td -> {
                        String name = td.getDiscipline() != null ? td.getDiscipline().getName() : "";
                        String year = safe(td.getAcademicYear());
                        Integer sem = td.getSemester();
                        return name + (!year.isEmpty() ? ", " + year : "") + (sem != null ? ", сем. " + sem : "");
                    })
                    .filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(";\n"));
            case DISC_NAMES -> discs.stream()
                    .map(td -> td.getDiscipline() != null ? td.getDiscipline().getName() : "")
                    .filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(";\n"));
            case DISC_CODES -> discs.stream()
                    .map(td -> td.getDiscipline() != null ? safe(td.getDiscipline().getCode()) : "")
                    .filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(";\n"));
            case DISC_CREDITS -> discs.stream()
                    .filter(td -> td.getDiscipline() != null && td.getDiscipline().getCredits() != null)
                    .map(td -> td.getDiscipline().getName() + " — " + td.getDiscipline().getCredits() + " кр.")
                    .distinct().collect(Collectors.joining(";\n"));
            case DISC_HOURS -> discs.stream()
                    .filter(td -> td.getDiscipline() != null)
                    .map(td -> {
                        var disc = td.getDiscipline();
                        return disc.getName() + ": лекц." + orZero(disc.getHoursLecture())
                                + " практ." + orZero(disc.getHoursPractical())
                                + " лаб." + orZero(disc.getHoursLab());
                    })
                    .distinct().collect(Collectors.joining(";\n"));
            case DISC_YEAR_SEMESTER -> discs.stream()
                    .map(td -> safe(td.getAcademicYear()) + (td.getSemester() != null ? ", сем. " + td.getSemester() : ""))
                    .filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(";\n"));

            // ── Послужний список ──
            case CAREER_ALL -> careers.stream()
                    .map(c -> safe(c.getPosition()) + " — " + safe(c.getOrganization())
                            + " (" + formatDateRange(c.getStartDate(), c.getEndDate()) + ")")
                    .collect(Collectors.joining(";\n"));
            case CAREER_POSITIONS -> careers.stream()
                    .map(c -> safe(c.getPosition())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case CAREER_ORGANIZATIONS -> careers.stream()
                    .map(c -> safe(c.getOrganization())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case CAREER_DATES -> careers.stream()
                    .map(c -> formatDateRange(c.getStartDate(), c.getEndDate()))
                    .collect(Collectors.joining(";\n"));
            case CAREER_NOTES -> careers.stream()
                    .map(c -> safe(c.getNotes())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));

            // ── Іноземні мови ──
            case LANG_ALL -> langs.stream()
                    .map(l -> {
                        String base = safe(l.getLanguage()) + " — " + safe(l.getLevel());
                        String cert = "";
                        if (l.getCertificateNumber() != null) cert += ", серт. " + l.getCertificateNumber();
                        if (l.getCertificateDate() != null) cert += " від " + formatDate(l.getCertificateDate());
                        if (l.getCertificateOrganization() != null) cert += ", " + l.getCertificateOrganization();
                        if (l.getCertificateUrl() != null) cert += ", URL: " + l.getCertificateUrl();
                        if (cert.isEmpty() && l.getCertificateDetails() != null) cert = " (" + l.getCertificateDetails() + ")";
                        return base + cert;
                    })
                    .collect(Collectors.joining(";\n"));
            case LANG_NAMES -> langs.stream()
                    .map(l -> safe(l.getLanguage())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case LANG_LEVELS -> langs.stream()
                    .map(l -> safe(l.getLanguage()) + ": " + safe(l.getLevel())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case LANG_CERTIFICATES -> langs.stream()
                    .map(l -> {
                        if (l.getCertificateNumber() != null) {
                            String s = l.getCertificateNumber();
                            if (l.getCertificateDate() != null) s += " від " + formatDate(l.getCertificateDate());
                            if (l.getCertificateOrganization() != null) s += ", " + l.getCertificateOrganization();
                            return s;
                        }
                        return safe(l.getCertificateDetails());
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case LANG_CERT_NUMBERS -> langs.stream()
                    .map(l -> safe(l.getCertificateNumber())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case LANG_CERT_DATES -> langs.stream()
                    .filter(l -> l.getCertificateDate() != null)
                    .map(l -> safe(l.getLanguage()) + ": " + formatDate(l.getCertificateDate()))
                    .collect(Collectors.joining(";\n"));
            case LANG_CERT_ORGS -> langs.stream()
                    .map(l -> safe(l.getCertificateOrganization())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case LANG_CERT_URLS -> langs.stream()
                    .map(l -> safe(l.getCertificateUrl())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));

            // ── Підвищення кваліфікації ──
            case QUAL_ALL -> numbered(quals.stream()
                    .map(q -> {
                        String period = formatDateRange(q.getStartDate(), q.getEndDate());
                        String credits = q.getCredits() != null ? q.getCredits() + " кр. ЄКТС" : "";
                        String cert = q.getCertificateNumber() != null ? ", серт. " + q.getCertificateNumber() : "";
                        if (q.getCertificateDate() != null) cert += " від " + formatDate(q.getCertificateDate());
                        return safe(q.getTitle()) + ", " + safe(q.getOrganization()) + ", " + period
                                + (credits.isEmpty() ? "" : ", " + credits) + cert;
                    })
                    .toList());
            case QUAL_TITLES -> quals.stream()
                    .map(q -> safe(q.getTitle())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case QUAL_ORGANIZATIONS -> quals.stream()
                    .map(q -> safe(q.getOrganization())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case QUAL_DATES -> quals.stream()
                    .map(q -> formatDateRange(q.getStartDate(), q.getEndDate()))
                    .collect(Collectors.joining(";\n"));
            case QUAL_CREDITS -> quals.stream()
                    .filter(q -> q.getCredits() != null)
                    .map(q -> safe(q.getTitle()) + ": " + q.getCredits() + " кр.")
                    .collect(Collectors.joining(";\n"));
            case QUAL_HOURS -> quals.stream()
                    .filter(q -> q.getHours() != null)
                    .map(q -> safe(q.getTitle()) + ": " + q.getHours() + " год.")
                    .collect(Collectors.joining(";\n"));
            case QUAL_CERTIFICATES -> quals.stream()
                    .map(q -> safe(q.getCertificateNumber())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case QUAL_CERT_DATES -> quals.stream()
                    .filter(q -> q.getCertificateDate() != null)
                    .map(q -> safe(q.getTitle()) + ": " + formatDate(q.getCertificateDate()))
                    .collect(Collectors.joining(";\n"));

            // ── Публікації ──
            case PUB_SUMMARY -> buildPublicationsSummary(pubs);
            case PUB_ALL -> numbered(pubs.stream()
                    .map(p -> {
                        String source = p.getJournalName() != null ? p.getJournalName()
                                : p.getConferenceInfo() != null ? p.getConferenceInfo() : null;
                        return safe(p.getTitle())
                                + (source != null ? " // " + source : "")
                                + (p.getYear() != null ? ", " + p.getYear() : "")
                                + (p.getType() != null ? " [" + pubTypeLabel(p.getType()) + "]" : "");
                    })
                    .toList());
            case PUB_DSTU -> numbered(pubs.stream()
                    .map(p -> safe(p.getDstuCitation())).filter(s -> !s.isEmpty())
                    .toList());
            case PUB_TITLES -> pubs.stream()
                    .map(p -> safe(p.getTitle())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case PUB_TYPES -> pubs.stream()
                    .filter(p -> p.getType() != null)
                    .map(p -> safe(p.getTitle()) + " [" + pubTypeLabel(p.getType()) + "]")
                    .collect(Collectors.joining(";\n"));
            case PUB_JOURNALS -> pubs.stream()
                    .map(p -> {
                        if (p.getJournalName() != null && !p.getJournalName().isEmpty()) return p.getJournalName();
                        if (p.getConferenceInfo() != null && !p.getConferenceInfo().isEmpty()) return p.getConferenceInfo();
                        return "";
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case PUB_YEARS -> pubs.stream()
                    .filter(p -> p.getYear() != null)
                    .map(p -> safe(p.getTitle()) + " (" + p.getYear() + ")")
                    .collect(Collectors.joining(";\n"));
            case PUB_DOI -> pubs.stream()
                    .map(p -> safe(p.getDoi())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case PUB_AUTHORS -> pubs.stream()
                    .map(p -> safe(p.getAuthors())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));

            // ── ppData ──
            case PP6_ALL -> numbered(d.pp6().stream()
                    .map(e -> safe(e.getStudentName())
                            + (e.getTopic() != null ? ", тема: " + e.getTopic() : "")
                            + (e.getDegreeType() != null ? " (" + degreeTypeLabel(e.getDegreeType()) + ")" : "")
                            + (e.getDefenseDate() != null ? ", захист: " + formatDate(e.getDefenseDate()) : ""))
                    .toList());
            case PP6_STUDENTS -> d.pp6().stream()
                    .map(e -> safe(e.getStudentName())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case PP7_ALL -> numbered(d.pp7().stream()
                    .map(e -> (e.getRole() != null ? attestationRoleLabel(e.getRole()) + ": " : "")
                            + safe(e.getCouncilName())
                            + (e.getStudentName() != null ? ", " + e.getStudentName() : "")
                            + (e.getDefenseDate() != null ? ", " + formatDate(e.getDefenseDate()) : ""))
                    .toList());
            case PP8_ALL -> numbered(d.pp8().stream()
                    .map(e -> (e.getRole() != null ? editorialRoleLabel(e.getRole()) + ": " : "")
                            + safe(e.getJournalOrProjectName())
                            + (e.getDateFrom() != null ? ", з " + formatDate(e.getDateFrom()) : ""))
                    .toList());
            case PP9_ALL -> numbered(d.pp9().stream()
                    .map(e -> safe(e.getCouncilName())
                            + (e.getType() != null ? " (" + expertCouncilTypeLabel(e.getType()) + ")" : "")
                            + (e.getRole() != null ? ", " + e.getRole() : "")
                            + (e.getDateFrom() != null ? ", з " + formatDate(e.getDateFrom()) : ""))
                    .toList());
            case PP10_ALL -> numbered(d.pp10().stream()
                    .map(e -> safe(e.getProjectName())
                            + (e.getProgram() != null ? " [" + internationalProgramLabel(e.getProgram()) + "]" : "")
                            + (e.getRole() != null ? ", " + e.getRole() : "")
                            + ", " + formatDateRange(e.getDateFrom(), e.getDateTo()))
                    .toList());
            case PP11_ALL -> numbered(d.pp11().stream()
                    .map(e -> safe(e.getOrganizationName())
                            + (e.getContractNumber() != null ? ", №" + e.getContractNumber() : "")
                            + ", " + formatDateRange(e.getDateFrom(), e.getDateTo()))
                    .toList());
            case PP13_ALL -> numbered(d.pp13().stream()
                    .map(e -> safe(e.getDisciplineName())
                            + (e.getLanguage() != null ? " (" + e.getLanguage() + ")" : "")
                            + (e.getHours() != null ? ", " + e.getHours() + " год." : "")
                            + (e.getAcademicYear() != null ? ", " + e.getAcademicYear() : "")
                            + (e.getSemester() != null ? ", сем. " + e.getSemester() : ""))
                    .toList());
            case PP14_ALL -> numbered(d.pp14().stream()
                    .map(e -> {
                        String base = safe(e.getOlympiadName());
                        if (e.getActivityType() != null) base = activityTypeLabel(e.getActivityType()) + ": " + base;
                        if (e.getRole() != null) base += ", " + olympiadRoleLabel(e.getRole());
                        if (e.getStudentName() != null) base += ", " + e.getStudentName();
                        if (e.getResult() != null) base += " — " + e.getResult();
                        if (e.getYear() != null) base += " (" + e.getYear() + ")";
                        return base;
                    })
                    .toList());
            case PP17_ALL -> numbered(d.pp17().stream()
                    .map(e -> (e.getMissionType() != null ? missionTypeLabel(e.getMissionType()) + ": " : "")
                            + safe(e.getMissionName())
                            + (e.getCountry() != null ? ", " + e.getCountry() : "")
                            + ", " + formatDateRange(e.getDateFrom(), e.getDateTo()))
                    .toList());
            case PP19_ALL -> numbered(d.pp19().stream()
                    .map(e -> safe(e.getOrganizationName())
                            + (e.getRole() != null ? ", " + e.getRole() : "")
                            + (e.getDateFrom() != null ? ", з " + formatDate(e.getDateFrom()) : "")
                            + (e.getCertificateNumber() != null ? ", серт. " + e.getCertificateNumber() : ""))
                    .toList());
            case PP20_ALL -> numbered(d.pp20().stream()
                    .map(e -> safe(e.getOrganizationName())
                            + (e.getPosition() != null ? ", " + e.getPosition() : "")
                            + ", " + formatDateRange(e.getDateFrom(), e.getDateTo())
                            + (e.getYearsCount() != null ? " (" + e.getYearsCount() + " р.)" : ""))
                    .toList());

            // ── Досягнення (п.38) ──
            case ACH_ALL -> buildAchievements(achs);
            case ACH_ALL_FULFILLED -> buildAchievements(filterFulfilled(t.getId(), achs));
            case ACH_TYPES -> achs.stream()
                    .filter(a -> a.getAchievementType() != null)
                    .map(a -> "п." + a.getAchievementType().getNumber())
                    .distinct().sorted().collect(Collectors.joining(", "));
            case ACH_TITLES -> achs.stream()
                    .map(a -> safe(a.getTitle())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case ACH_DATES -> achs.stream()
                    .filter(a -> a.getDateAchieved() != null)
                    .map(a -> safe(a.getTitle()) + " — " + a.getDateAchieved())
                    .collect(Collectors.joining(";\n"));
            case ACH_DESCRIPTIONS -> achs.stream()
                    .map(a -> safe(a.getDescription())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
            case ACH_DOCUMENTS -> achs.stream()
                    .map(a -> safe(a.getDocumentUrl())).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(";\n"));
        };
    }

    // ── Composite builders ────────────────────────────────────────────

    private String buildFullName(Teacher t) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(t.getLastName())).append(" ").append(safe(t.getFirstName()));
        if (t.getPatronymic() != null) sb.append(" ").append(t.getPatronymic());
        return sb.toString().trim();
    }

    private String buildFullNameDetails(Teacher t) {
        StringBuilder sb = new StringBuilder(buildFullName(t));
        if (t.getDateOfBirth() != null) sb.append(", ").append(
                t.getDateOfBirth().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(" р.н.");
        if (t.getMilitaryRank() != null) sb.append(", ").append(t.getMilitaryRank());
        if (t.getExperienceStartDate() != null) sb.append(", стаж ").append(java.time.Period.between(t.getExperienceStartDate(), java.time.LocalDate.now()).getYears()).append(" р.");
        return sb.toString();
    }

    private String buildPositionEmployment(Teacher t) {
        String pos = safe(teacherPositionService.getEffectivePosition(t));
        String type = "PART_TIME".equals(t.getEmploymentType()) ? "сумісник" : "основне місце роботи";
        return pos + " (" + type + ")";
    }

    /**
     * Повна посада з назвою кафедри:
     * "Викладач" + "Кафедра комп'ютерних наук" → "Викладач кафедри комп'ютерних наук"
     * "Начальник кафедри" + "Кафедра комп'ютерних наук" → "Начальник кафедри комп'ютерних наук"
     * "Заступник начальника кафедри" + "Кафедра X" → "Заступник начальника кафедри X"
     * "Професор кафедри" + "Кафедра X" → "Професор кафедри X"
     *
     * Логіка:
     *   1) Назва кафедри очищується від префікса "Кафедра"/"кафедра"/"Кафедри"/"кафедри".
     *   2) Якщо посада вже містить слово "кафедри" / "кафедра" (будь-де в рядку) — лише
     *      приклеюємо очищену назву; інакше додаємо "кафедри <назва>".
     */
    private String buildPositionFull(Teacher t) {
        String pos = safe(teacherPositionService.getEffectivePosition(t));
        if (pos.isEmpty()) return "";
        if (t.getDepartment() == null || t.getDepartment().getName() == null) return pos;
        String deptName = stripDepartmentPrefix(t.getDepartment().getName());
        if (deptName == null || deptName.isEmpty()) return pos;
        if (positionMentionsKafedra(pos)) {
            return pos + " " + deptName;
        }
        // Інші посади (Викладач, Доцент, Професор, Старший викладач тощо) — додаємо "кафедри <назва>"
        return pos + " кафедри " + deptName;
    }

    /**
     * Чи в назві посади вже є згадка кафедри (у будь-якому відмінку):
     * "Начальник кафедри", "Заступник начальника кафедри", "Завідувач кафедри",
     * "Професор кафедри", "Начальник кафедри — професор" тощо.
     */
    private boolean positionMentionsKafedra(String pos) {
        if (pos == null) return false;
        String lower = pos.toLowerCase();
        return lower.contains("кафедри") || lower.contains("кафедра") || lower.contains("кафедрою");
    }

    /**
     * Відсікає префікс "Кафедра" / "кафедра" / "Кафедри" / "кафедри" з початку назви кафедри.
     * "Кафедра комп'ютерних наук" → "комп'ютерних наук"
     * Якщо префікса немає — повертає назву без змін.
     */
    private String stripDepartmentPrefix(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return trimmed;
        String lower = trimmed.toLowerCase();
        String[] prefixes = {"кафедра ", "кафедри "};
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }

    private String buildEducation(Teacher t) {
        // Use Education records if available
        var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(t.getId());
        if (!educations.isEmpty()) {
            List<String> blocks = new ArrayList<>();
            for (var edu : educations) {
                List<String> parts = new ArrayList<>();
                if (edu.getInstitution() != null) parts.add(edu.getInstitution());
                if (edu.getGraduationYear() != null) parts.add(edu.getGraduationYear() + " р.");
                if (edu.getSpeciality() != null) parts.add("спеціальність: \"" + edu.getSpeciality() + "\"");
                if (edu.getQualification() != null) parts.add("кваліфікація: \"" + edu.getQualification() + "\"");
                if (edu.getDiploma() != null) {
                    String diploma = "диплом " + edu.getDiploma();
                    if (edu.getDiplomaDate() != null) diploma += " від " + formatDate(edu.getDiplomaDate());
                    parts.add(diploma);
                }
                blocks.add(String.join(", ", parts));
            }
            return String.join("\n", blocks);
        }
        // Fallback to flat fields
        List<String> parts = new ArrayList<>();
        if (t.getUniversity() != null) parts.add(t.getUniversity());
        if (t.getUniversityGraduationYear() != null) parts.add(t.getUniversityGraduationYear() + " р.");
        if (t.getUniversitySpeciality() != null) parts.add("спеціальність: " + t.getUniversitySpeciality());
        if (t.getUniversityDiploma() != null) {
            String diploma = "диплом " + t.getUniversityDiploma();
            if (t.getUniversityDiplomaDate() != null) diploma += " від " + formatDate(t.getUniversityDiplomaDate());
            parts.add(diploma);
        }
        return String.join(", ", parts);
    }

    private String buildAllEducations(Teacher t) {
        var educations = educationRepository.findByTeacherIdOrderByGraduationYearDesc(t.getId());
        if (educations.isEmpty()) return buildEducation(t); // fallback
        List<String> blocks = new ArrayList<>();
        for (var edu : educations) {
            List<String> parts = new ArrayList<>();
            if (edu.getInstitution() != null) parts.add(edu.getInstitution());
            if (edu.getCity() != null) parts.add(edu.getCity());
            if (edu.getGraduationYear() != null) parts.add(edu.getGraduationYear() + " р.");
            if (edu.getDegree() != null) parts.add(edu.getDegree());
            if (edu.getSpeciality() != null) parts.add("спеціальність: \"" + edu.getSpeciality() + "\"");
            if (edu.getQualification() != null) parts.add("кваліфікація: \"" + edu.getQualification() + "\"");
            if (edu.getDiploma() != null) {
                String diploma = edu.getDiploma();
                if (edu.getDiplomaDate() != null) diploma += " від " + formatDate(edu.getDiplomaDate());
                parts.add(diploma);
            }
            blocks.add(String.join(", ", parts));
        }
        return String.join("\n", blocks);
    }

    private String buildScientificQualification(Teacher t) {
        List<String> parts = new ArrayList<>();
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        for (AcademicDegree d : degrees) {
            if (d.getDegree() != null) parts.add(d.getDegree());
            if (d.getSpeciality() != null) parts.add("спеціальність: " + d.getSpeciality());
            if (d.getDissertationTopic() != null) parts.add("тема: " + d.getDissertationTopic());
            if (d.getDiploma() != null) {
                String diploma = "диплом " + d.getDiploma();
                if (d.getDiplomaDate() != null) diploma += " від " + formatDate(d.getDiplomaDate());
                parts.add(diploma);
            }
        }
        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
        for (AcademicTitle at : titles) {
            if (at.getTitleName() != null) parts.add(at.getTitleName());
            if (at.getAttestat() != null) {
                String attestat = "атестат " + at.getAttestat();
                if (at.getAttestatDate() != null) attestat += " від " + formatDate(at.getAttestatDate());
                parts.add(attestat);
            }
        }
        return String.join(", ", parts);
    }

    private AcademicDegree primaryDegree(Teacher t) {
        return AcademicDegreeRanking.primary(
                academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()));
    }

    private AcademicTitle primaryTitle(Teacher t) {
        return AcademicTitleRanking.primary(
                academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId()));
    }

    private String primaryDegreeName(Teacher t) {
        AcademicDegree d = primaryDegree(t);
        return d != null ? d.getDegree() : null;
    }

    private String primaryTitleName(Teacher t) {
        AcademicTitle at = primaryTitle(t);
        return at != null ? at.getTitleName() : null;
    }

    private String primaryDegreeField(Teacher t, java.util.function.Function<AcademicDegree, String> getter) {
        AcademicDegree d = primaryDegree(t);
        return d != null ? getter.apply(d) : null;
    }

    // ── Helpers: повні списки всіх ступенів та звань ───────────────────

    private String joinAllDegreeNames(Teacher t) {
        return academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).stream()
                .map(AcademicDegree::getDegree)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllDegreesFullDetails(Teacher t) {
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        if (degrees.isEmpty()) return "";
        List<String> parts = degrees.stream()
                .map(this::degreeFullText)
                .filter(s -> !s.isEmpty())
                .toList();
        return numberedJoin(parts);
    }

    private String degreeFullText(AcademicDegree d) {
        StringBuilder sb = new StringBuilder();
        if (d.getDegree() != null && !d.getDegree().isBlank()) sb.append(d.getDegree());
        if (d.getSpeciality() != null && !d.getSpeciality().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("спеціальність: ").append(d.getSpeciality());
        }
        if (d.getDiploma() != null && !d.getDiploma().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("диплом ").append(d.getDiploma());
            if (d.getDiplomaDate() != null) sb.append(" від ").append(formatDate(d.getDiplomaDate()));
        }
        if (d.getDissertationTopic() != null && !d.getDissertationTopic().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("тема: ").append(d.getDissertationTopic());
        }
        return sb.toString();
    }

    private String joinAllTitleNames(Teacher t) {
        return academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId()).stream()
                .map(AcademicTitle::getTitleName)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllTitlesFullDetails(Teacher t) {
        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
        if (titles.isEmpty()) return "";
        List<String> parts = titles.stream()
                .map(this::titleFullText)
                .filter(s -> !s.isEmpty())
                .toList();
        return numberedJoin(parts);
    }

    /**
     * Якщо у списку 1 елемент — повертає його як є (без нумерації).
     * Якщо ≥2 — повертає формат:
     * <pre>
     * 1. перший елемент
     * 2. другий елемент
     * </pre>
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

    private String titleFullText(AcademicTitle at) {
        StringBuilder sb = new StringBuilder();
        if (at.getTitleName() != null && !at.getTitleName().isBlank()) sb.append(at.getTitleName());
        if (at.getAttestat() != null && !at.getAttestat().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("атестат ").append(at.getAttestat());
            if (at.getAttestatDate() != null) sb.append(" від ").append(formatDate(at.getAttestatDate()));
        }
        return sb.toString();
    }

    private String joinAllDissertationTopics(Teacher t) {
        return academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).stream()
                .map(AcademicDegree::getDissertationTopic)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllDissertationSpecialities(Teacher t) {
        return academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).stream()
                .map(AcademicDegree::getSpeciality)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllDegreeDiplomas(Teacher t) {
        return academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).stream()
                .map(d -> {
                    if (d.getDiploma() == null || d.getDiploma().isBlank()) return null;
                    return d.getDiplomaDate() != null
                            ? d.getDiploma() + " від " + formatDate(d.getDiplomaDate())
                            : d.getDiploma();
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String joinAllTitleAttestats(Teacher t) {
        return academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId()).stream()
                .map(at -> {
                    if (at.getAttestat() == null || at.getAttestat().isBlank()) return null;
                    return at.getAttestatDate() != null
                            ? at.getAttestat() + " від " + formatDate(at.getAttestatDate())
                            : at.getAttestat();
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String regaliaNames(Teacher t) {
        List<String> names = new ArrayList<>();
        academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).forEach(d -> {
            if (d.getDegree() != null && !d.getDegree().isBlank()) names.add(d.getDegree());
        });
        academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId()).forEach(at -> {
            if (at.getTitleName() != null && !at.getTitleName().isBlank()) names.add(at.getTitleName());
        });
        return String.join(", ", names);
    }

    private String regaliaShort(Teacher t) {
        List<String> abbrs = new ArrayList<>();
        academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId()).forEach(d -> {
            String abbr = abbreviateDegree(d.getDegree());
            if (abbr != null && !abbr.isBlank()) abbrs.add(abbr);
        });
        academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId()).forEach(at -> {
            String abbr = abbreviateTitle(at.getTitleName());
            if (abbr != null && !abbr.isBlank()) abbrs.add(abbr);
        });
        return String.join(", ", abbrs);
    }

    /**
     * Скорочує назву ступеня до загальноприйнятої абревіатури:
     * "Доктор технічних наук" → "д.т.н.", "Кандидат економічних наук" → "к.е.н.".
     * Якщо область науки не розпізнано — повертає повну назву.
     */
    private String abbreviateDegree(String full) {
        if (full == null || full.isBlank()) return null;
        String l = full.toLowerCase();
        String prefix;
        if (l.startsWith("доктор філософії") || l.equalsIgnoreCase("phd")) {
            prefix = "PhD";
            return prefix; // PhD не має «області»
        } else if (l.startsWith("доктор")) prefix = "д.";
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
        else if (l.contains("мистецтвознавств") || l.contains("мистецтвоз")) area = "мист.";
        else if (l.contains("архітектур")) area = "арх.";
        else if (l.contains("державного управління") || l.contains("держ. упр")) area = "держ.упр.";
        else return full;  // невідома область — повертаємо повну назву
        return prefix + area;
    }

    /**
     * Скорочує назву звання: "Професор" → "проф.", "Доцент" → "доц.".
     * Якщо звання містить уточнення кафедри ("Доцент кафедри X") — все одно скорочує до "доц.".
     */
    private String abbreviateTitle(String full) {
        if (full == null || full.isBlank()) return null;
        String l = full.toLowerCase();
        if (l.contains("професор")) return "проф.";
        if (l.contains("доцент")) return "доц.";
        if (l.contains("старш") && l.contains("дослід")) return "ст. досл.";
        if (l.contains("старш") && l.contains("науков")) return "с.н.с.";
        return full;
    }

    private String buildCombatExperience(Teacher t) {
        if (!t.isCombatVeteranStatus()) return "—";
        List<String> parts = new ArrayList<>();
        parts.add("Учасник бойових дій");
        if (t.getCombatVeteranDoc() != null) {
            String doc = t.getCombatVeteranDoc();
            if (t.getCombatVeteranDocDate() != null) doc += " від " + formatDate(t.getCombatVeteranDocDate());
            if (t.getCombatVeteranDocIssuedBy() != null) doc += ", видано: " + t.getCombatVeteranDocIssuedBy();
            parts.add(doc);
        }
        if (t.getCombatExperienceDates() != null) parts.add(t.getCombatExperienceDates());
        return String.join(", ", parts);
    }

    private String buildPublicationsSummary(List<Publication> pubs) {
        if (pubs.isEmpty()) return "—";
        int cutoffYear = LocalDate.now().getYear() - 5;
        List<Publication> recent = pubs.stream()
                .filter(p -> p.getYear() != null && p.getYear() >= cutoffYear)
                .toList();

        Map<PublicationType, Long> counts = recent.stream()
                .filter(p -> p.getType() != null)
                .collect(Collectors.groupingBy(Publication::getType, Collectors.counting()));

        List<String> parts = new ArrayList<>();
        addIfPresent(parts, counts, PublicationType.ARTICLE, "наукові статті");
        addIfPresent(parts, counts, PublicationType.PATENT, "патенти");
        addIfPresent(parts, counts, PublicationType.DECLARATIVE_PATENT, "декл. патенти");
        addIfPresent(parts, counts, PublicationType.COPYRIGHT, "авт. право");
        addIfPresent(parts, counts, PublicationType.TEXTBOOK, "підручники");
        addIfPresent(parts, counts, PublicationType.STUDY_GUIDE, "навч. посібники");
        addIfPresent(parts, counts, PublicationType.MONOGRAPH, "монографії");
        addIfPresent(parts, counts, PublicationType.METHODICAL, "навч.-методичні");
        addIfPresent(parts, counts, PublicationType.APPROBATION, "апробації");
        addIfPresent(parts, counts, PublicationType.POPULAR_SCIENTIFIC, "наук.-популярні");
        addIfPresent(parts, counts, PublicationType.OTHER, "інші");

        long total = recent.size();
        return "Всього: " + total + (parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")");
    }

    private void addIfPresent(List<String> parts, Map<PublicationType, Long> counts,
                              PublicationType type, String label) {
        Long count = counts.get(type);
        if (count != null && count > 0) {
            parts.add(label + " — " + count);
        }
    }

    /**
     * Фільтрує досягнення викладача — лишає лише ті, що ВИКОНАНІ за нормативом п.38
     * (currentCount &gt;= requiredCount), згідно з тією ж логікою що на вкладці "Досягнення п.38".
     *
     * <p>На відміну від {@code Achievement.isVerified()} (флаг у БД, який може бути
     * встановлений вручну або автоматично composer-ом), це РЕАЛЬНА перевірка compliance:
     * скільки публікацій/керівництв/тощо в БД vs. norm.
     */
    private List<Achievement> filterFulfilled(Long teacherId, List<Achievement> achs) {
        if (achs == null || achs.isEmpty()) return List.of();
        try {
            List<ua.edu.teacherlicence.achievement.dto.AchievementProgressDto> progress =
                    achievementValidationService.getProgressForTeacher(teacherId);
            java.util.Set<Long> fulfilledIds = progress.stream()
                    .filter(ua.edu.teacherlicence.achievement.dto.AchievementProgressDto::isFulfilled)
                    .map(ua.edu.teacherlicence.achievement.dto.AchievementProgressDto::getAchievementId)
                    .collect(Collectors.toSet());
            return achs.stream()
                    .filter(a -> fulfilledIds.contains(a.getId()))
                    .toList();
        } catch (Exception e) {
            log.warn("filterFulfilled: validation failed for teacherId={}, falling back to isVerified flag: {}",
                    teacherId, e.getMessage());
            // Безпечний fallback — якщо validation падає, повертаємо verified-only
            return achs.stream().filter(Achievement::isVerified).toList();
        }
    }

    private String buildAchievements(List<Achievement> achs) {
        if (achs.isEmpty()) return "—";
        Map<Integer, List<Achievement>> grouped = achs.stream()
                .filter(a -> a.getAchievementType() != null)
                .collect(Collectors.groupingBy(a -> a.getAchievementType().getNumber(),
                        TreeMap::new, Collectors.toList()));

        List<String> parts = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            // Use description (full text) if available, otherwise title
            // Descriptions from Composer already contain internal numbering
            List<String> items = new ArrayList<>();
            for (Achievement a : entry.getValue()) {
                String text = (a.getDescription() != null && !a.getDescription().isBlank())
                        ? a.getDescription() : safe(a.getTitle());
                items.add(text);
            }
            String content = String.join("\n", items);
            // **bold** marker for setCellContent
            parts.add("**п.38 пп." + entry.getKey() + ":** " + content);
        }
        return String.join("\n", parts);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void setCellContent(XWPFTableCell cell, String text) {
        while (cell.getParagraphs().size() > 1) {
            cell.removeParagraph(cell.getParagraphs().size() - 1);
        }
        XWPFParagraph p = cell.getParagraphs().get(0);
        while (p.getRuns().size() > 0) {
            p.removeRun(0);
        }
        p.setAlignment(ParagraphAlignment.LEFT);

        if (text == null || text.isEmpty()) return;

        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            // Support **bold** markers within lines
            addFormattedRuns(p, lines[i]);
            if (i < lines.length - 1) {
                XWPFRun br = p.createRun();
                br.setFontFamily("Times New Roman");
                br.setFontSize(9);
                br.addBreak();
            }
        }
    }

    /**
     * Parse **bold** markers in text and add runs with appropriate formatting.
     * E.g. "**п.38 пп.1:** some text" → bold "п.38 пп.1:" + normal " some text"
     */
    private void addFormattedRuns(XWPFParagraph p, String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(line);
        int pos = 0;
        while (m.find()) {
            // Text before bold marker
            if (m.start() > pos) {
                XWPFRun run = p.createRun();
                run.setText(line.substring(pos, m.start()));
                run.setFontFamily("Times New Roman");
                run.setFontSize(9);
            }
            // Bold text
            XWPFRun boldRun = p.createRun();
            boldRun.setText(m.group(1));
            boldRun.setFontFamily("Times New Roman");
            boldRun.setFontSize(9);
            boldRun.setBold(true);
            pos = m.end();
        }
        // Remaining text after last bold marker
        if (pos < line.length()) {
            XWPFRun run = p.createRun();
            run.setText(line.substring(pos));
            run.setFontFamily("Times New Roman");
            run.setFontSize(9);
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return String.format("%02d.%02d.%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        String s = start != null ? formatDate(start) : "...";
        String e = end != null ? formatDate(end) : "...";
        return s + " – " + e;
    }

    /**
     * Military teachers have a non-empty militaryRank.
     */
    private boolean isMilitary(Teacher t) {
        return t.getMilitaryRank() != null && !t.getMilitaryRank().isBlank();
    }

    /**
     * Position seniority rank (lower = higher seniority).
     * начальник=0, заступник/зам=1, професор=2, доцент=3,
     * старший викладач=4, викладач=5, інше=99.
     */
    private int getPositionRank(Teacher t) {
        String pos = teacherPositionService.getEffectivePosition(t);
        if (pos == null || pos.isBlank()) return 99;
        String lower = pos.toLowerCase();
        if (lower.contains("начальник")) return 0;
        if (lower.contains("заступник") || lower.contains("зам.") || lower.contains("зам ")) return 1;
        if (lower.contains("професор")) return 2;
        if (lower.contains("доцент")) return 3;
        if (lower.contains("старший викладач")) return 4;
        if (lower.contains("викладач")) return 5;
        return 99;
    }

    /** Format list with numbering: "1. item1\n2. item2\n..." */
    private String numbered(List<String> items) {
        if (items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(items.get(i));
        }
        return sb.toString();
    }

    // ── Enum labels (Ukrainian) ─────────────────────────────────────

    private String degreeTypeLabel(DegreeType type) {
        return switch (type) {
            case PHD -> "PhD";
            case DSC -> "д.н.";
            case CANDIDATE -> "к.н.";
            case DOCTOR -> "доктор наук";
        };
    }

    private String attestationRoleLabel(AttestationRole role) {
        return switch (role) {
            case OPPONENT -> "офіційний опонент";
            case REVIEWER -> "рецензент";
            case CHAIR -> "голова разової спецради";
            case COUNCIL_MEMBER -> "член постійної спецради";
        };
    }

    private String editorialRoleLabel(EditorialRole role) {
        return switch (role) {
            case THEME_LEADER -> "керівник теми";
            case RESPONSIBLE_EXECUTOR -> "відповідальний виконавець";
            case CHIEF_EDITOR -> "головний редактор";
            case BOARD_MEMBER -> "член редколегії";
            case REVIEWER -> "рецензент";
        };
    }

    private String expertCouncilTypeLabel(ExpertCouncilType type) {
        return switch (type) {
            case MON -> "МОН";
            case NAZYAVO -> "НАЗЯВО";
            case ACCREDITATION -> "акред. комісія";
            case NMR -> "НМР";
            case STATE_SERVICE -> "держ. служба";
        };
    }

    private String internationalProgramLabel(InternationalProgram program) {
        return switch (program) {
            case ERASMUS -> "Erasmus+";
            case HORIZON -> "Horizon Europe";
            case NATO -> "НАТО";
            case BILATERAL -> "двостороння угода";
            case GRANT -> "грант";
            case OTHER -> "інше";
        };
    }

    private String missionTypeLabel(MissionType type) {
        return switch (type) {
            case UN_PEACEKEEPING -> "миротворча операція ООН";
            case NATO_EXERCISE -> "навчання НАТО";
        };
    }

    private String activityTypeLabel(Pp14ActivityType type) {
        return switch (type) {
            case OLYMPIAD -> "Олімпіада";
            case SCIENTIFIC_COMPETITION -> "Конкурс наукових робіт";
            case COMPETITION -> "Конкурс";
            case SCIENTIFIC_GROUP -> "Науковий гурток";
            case SPORTS -> "Спортивні змагання";
            case ARTS -> "Мистецький конкурс";
            case OTHER -> "Інше";
        };
    }

    private String olympiadRoleLabel(OlympiadRole role) {
        return switch (role) {
            case SUPERVISOR -> "керівник";
            case JURY -> "член журі";
            case COMMITTEE -> "член оргкомітету";
            case GROUP_LEADER -> "керівник гуртка";
            case COACH -> "тренер";
            case CURATOR -> "куратор";
        };
    }

    private String pubTypeLabel(PublicationType type) {
        return switch (type) {
            case ARTICLE -> "стаття";
            case PATENT -> "патент";
            case DECLARATIVE_PATENT -> "декл. патент";
            case COPYRIGHT -> "авт. право";
            case TEXTBOOK -> "підручник";
            case STUDY_GUIDE -> "навч. посібник";
            case MONOGRAPH -> "монографія";
            case METHODICAL -> "навч.-метод.";
            case APPROBATION -> "апробація";
            case POPULAR_SCIENTIFIC -> "наук.-популярне";
            case OTHER -> "інше";
        };
    }

    // ── Inner record ──────────────────────────────────────────────────

    public record ColumnMapping(int columnIndex, String headerText, List<String> fieldKeys) {
    }

    /** Aggregated per-teacher data for field resolution */
    private record TeacherData(
            List<Publication> pubs, List<Achievement> achs, List<QualificationImprovement> quals,
            List<CareerRecord> careers, List<LanguageSkill> langs, List<TeacherDiscipline> discs,
            List<ScientificSupervision> pp6, List<AttestationActivity> pp7, List<EditorialActivity> pp8,
            List<ExpertCouncil> pp9, List<InternationalProject> pp10, List<ScientificConsulting> pp11,
            List<ForeignLanguageTeaching> pp13, List<OlympiadGuidance> pp14, List<MilitaryMission> pp17,
            List<ProfessionalAssociation> pp19, List<PracticalExperience> pp20
    ) {}
}
