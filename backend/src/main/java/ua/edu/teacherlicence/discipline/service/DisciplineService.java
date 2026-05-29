package ua.edu.teacherlicence.discipline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.compliance.events.ComplianceEvents;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.discipline.model.Discipline;
import ua.edu.teacherlicence.discipline.model.DisciplineDocument;
import ua.edu.teacherlicence.discipline.model.DocumentStatus;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.DisciplineDocumentRepository;
import ua.edu.teacherlicence.discipline.repository.DisciplineRepository;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisciplineService {

    private final DisciplineRepository disciplineRepository;
    private final TeacherDisciplineRepository teacherDisciplineRepository;
    private final DisciplineDocumentRepository documentRepository;
    private final DepartmentRepository departmentRepository;
    private final EducationalProgramRepository educationalProgramRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public List<Discipline> findAllDisciplines() {
        return disciplineRepository.findAll();
    }

    public Discipline findDisciplineById(Long id) {
        return disciplineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Дисципліну не знайдено: " + id));
    }

    public List<Discipline> findDisciplinesByDepartment(Long departmentId) {
        return disciplineRepository.findByDepartmentId(departmentId);
    }

    public List<Discipline> findDisciplinesByProgram(Long programId) {
        return disciplineRepository.findByEducationalProgramId(programId);
    }

    @Transactional
    public Discipline createDiscipline(Discipline discipline) {
        Discipline saved = disciplineRepository.save(discipline);
        events.publishEvent(new ComplianceEvents.DisciplineChanged(saved.getId()));
        return saved;
    }

    @Transactional
    public Discipline updateDiscipline(Long id, Discipline updated) {
        Discipline existing = findDisciplineById(id);
        existing.setName(updated.getName());
        existing.setCode(updated.getCode());
        existing.setDepartment(updated.getDepartment());
        existing.setEducationalProgram(updated.getEducationalProgram());
        existing.setCredits(updated.getCredits());
        existing.setTotalHours(updated.getTotalHours());
        existing.setAuditoryHours(updated.getAuditoryHours());
        existing.setHoursLecture(updated.getHoursLecture());
        existing.setHoursGroup(updated.getHoursGroup());
        existing.setHoursPractical(updated.getHoursPractical());
        existing.setHoursLab(updated.getHoursLab());
        existing.setHoursSelfStudy(updated.getHoursSelfStudy());
        existing.setExamSemesters(updated.getExamSemesters());
        existing.setCreditSemesters(updated.getCreditSemesters());
        existing.setHoursBySemester(updated.getHoursBySemester());
        existing.setCreditsBySemester(updated.getCreditsBySemester());
        existing.setControlTypes(updated.getControlTypes());
        Discipline saved = disciplineRepository.save(existing);
        events.publishEvent(new ComplianceEvents.DisciplineChanged(saved.getId()));
        return saved;
    }

    @Transactional
    public void deleteDiscipline(Long id) {
        // Cascade: remove teacher assignments and documents first
        teacherDisciplineRepository.deleteByDisciplineId(id);
        documentRepository.deleteByDisciplineId(id);
        disciplineRepository.deleteById(id);
        events.publishEvent(new ComplianceEvents.DisciplineDeleted(id));
    }

    // ── Excel Import (НПБ) ──

    /**
     * Імпорт дисциплін з Excel-файлу НПБ.
     * Формат файлу: лист "Всі ОК" (або перший лист).
     * Рядки 1-10 — заголовки (пропускаються), дані починаються з рядка 11.
     * Колонки (0-based):
     *  0  — shortCode ОПП (F3 КН, F5 КЗІ (121501) тощо)
     *  1  — код компоненти (ОК 6, ВК 1.3)
     *  2  — назва дисципліни
     *  3  — номер кафедри
     *  4  — семестри іспитів
     *  5  — семестри заліків
     *  6  — МКР (модульні контрольні роботи) — семестри
     *  7  — курсові роботи/проекти — семестри
     *  8  — ДКР, РГР, РР, ГР — семестри
     *  9  — реферати — семестри
     * 10  — кредити ЄКТС
     * 11  — загальний обсяг годин
     * 12  — аудиторних годин (всього)
     * 13  — лекцій
     * 14  — групових / семінарів
     * 15  — практичних / лабораторних
     * 16  — самостійна підготовка
     * 17-24 — аудиторні години по семестрах (1-8)
     * 25-32 — кредити ЄКТС по семестрах (1-8)
     *
     * @param is InputStream Excel-файлу (.xlsx)
     * @return кількість імпортованих дисциплін
     */
    @Transactional
    public int importFromExcel(InputStream is, Integer enrollmentYear, String educationLevel, String educationForm) throws IOException {
        // Build lookup maps — filter OPP by enrollment year, education level, and education form
        Map<String, EducationalProgram> oppByShortCode = new HashMap<>();
        educationalProgramRepository.findAll().stream()
                .filter(opp -> enrollmentYear.equals(opp.getEnrollmentYear()))
                .filter(opp -> educationLevel == null || educationLevel.isBlank()
                        || (opp.getEducationLevel() != null && opp.getEducationLevel().toLowerCase().contains(educationLevel.toLowerCase())))
                .filter(opp -> educationForm == null || educationForm.isBlank()
                        || (opp.getEducationForm() != null && opp.getEducationForm().toLowerCase().contains(educationForm.toLowerCase())))
                .forEach(opp -> oppByShortCode.put(normalizeCode(opp.getShortCode()), opp));

        log.info("Found {} OPPs for year={}, level='{}', form='{}': {}", oppByShortCode.size(), enrollmentYear,
                educationLevel, educationForm, oppByShortCode.keySet());

        Map<String, Department> deptByNumber = new HashMap<>();
        departmentRepository.findAll().forEach(d ->
                deptByNumber.put(d.getNumber().trim(), d));

        List<Discipline> imported = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(is)) {
            // Try to find sheet "Всі ОК", otherwise use first sheet
            Sheet sheet = wb.getSheet("Всі ОК");
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }

            // Determine layout: магістр/ДФ have fewer control columns
            boolean isMasterOrPhd = educationLevel != null
                    && (educationLevel.contains("магістр") || educationLevel.contains("доктор"));

            // Data starts at row 11 (index 10), rows 1-10 are headers
            for (int i = 10; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String oppCode = getStringValue(row.getCell(0));
                String componentCode = getStringValue(row.getCell(1));
                String disciplineName = getStringValue(row.getCell(2));

                // Skip empty rows
                if (disciplineName == null || disciplineName.isBlank()) continue;

                String deptNumber = getStringValue(row.getCell(3));
                String examSem = getStringValue(row.getCell(4));
                String creditSem = getStringValue(row.getCell(5));

                String courseworkSem;
                String mkrSem = null;
                String dkrSem = null;
                String referatSem = null;
                Double credits;
                Integer totalHours, auditoryHours, hoursLecture, hoursGroup, hoursPractical, hoursSelfStudy;
                Map<String, Integer> hoursBySem = new LinkedHashMap<>();
                Map<String, Double> creditsBySem = new LinkedHashMap<>();

                if (isMasterOrPhd) {
                    // Магістр/ДФ: col 6=курсові, 7=кредити, 8=годин, 9=аудит, 10=лекц, 11=груп, 12=практ, 13=самост
                    courseworkSem = getStringValue(row.getCell(6));
                    credits = getNumericValue(row.getCell(7));
                    totalHours = getIntValue(row.getCell(8));
                    auditoryHours = getIntValue(row.getCell(9));
                    hoursLecture = getIntValue(row.getCell(10));
                    hoursGroup = getIntValue(row.getCell(11));
                    hoursPractical = getIntValue(row.getCell(12));
                    hoursSelfStudy = getIntValue(row.getCell(13));
                    // No semester distribution for магістр/ДФ
                } else {
                    // Бакалавр: col 6=МКР, 7=курсові, 8=ДКР, 9=реферати, 10=кредити, ...
                    mkrSem = getStringValue(row.getCell(6));
                    courseworkSem = getStringValue(row.getCell(7));
                    dkrSem = getStringValue(row.getCell(8));
                    referatSem = getStringValue(row.getCell(9));
                    credits = getNumericValue(row.getCell(10));
                    totalHours = getIntValue(row.getCell(11));
                    auditoryHours = getIntValue(row.getCell(12));
                    hoursLecture = getIntValue(row.getCell(13));
                    hoursGroup = getIntValue(row.getCell(14));
                    hoursPractical = getIntValue(row.getCell(15));
                    hoursSelfStudy = getIntValue(row.getCell(16));

                    // Hours by semester (columns 17-24)
                    for (int s = 0; s < 8; s++) {
                        Integer val = getIntValue(row.getCell(17 + s));
                        if (val != null && val > 0) {
                            hoursBySem.put(String.valueOf(s + 1), val);
                        }
                    }
                    // Credits by semester (columns 25-32)
                    for (int s = 0; s < 8; s++) {
                        Double val = getNumericValue(row.getCell(25 + s));
                        if (val != null && val > 0) {
                            creditsBySem.put(String.valueOf(s + 1), val);
                        }
                    }
                }

                // Build control types list
                List<String> controlTypesList = new ArrayList<>();
                if (examSem != null && !examSem.isBlank()) controlTypesList.add("іспит: " + examSem);
                if (creditSem != null && !creditSem.isBlank()) controlTypesList.add("залік: " + creditSem);
                if (mkrSem != null && !mkrSem.isBlank()) controlTypesList.add("МКР: " + mkrSem);
                if (courseworkSem != null && !courseworkSem.isBlank()) controlTypesList.add("курсова: " + courseworkSem);
                if (dkrSem != null && !dkrSem.isBlank()) controlTypesList.add("ДКР/РГР: " + dkrSem);
                if (referatSem != null && !referatSem.isBlank()) controlTypesList.add("реферат: " + referatSem);

                // Resolve OPP
                EducationalProgram opp = null;
                if (oppCode != null && !oppCode.isBlank()) {
                    opp = oppByShortCode.get(normalizeCode(oppCode));
                    if (opp == null) {
                        log.warn("Row {}: OPP not found by shortCode '{}', skipping", i + 1, oppCode);
                        continue;
                    }
                }

                // Resolve department
                Department dept = null;
                if (deptNumber != null && !deptNumber.isBlank()) {
                    dept = deptByNumber.get(deptNumber.trim());
                    if (dept == null) {
                        log.warn("Row {}: Department not found by number '{}', using OPP department", i + 1, deptNumber);
                        if (opp != null) dept = opp.getDepartment();
                    }
                }

                Discipline disc = Discipline.builder()
                        .name(disciplineName.trim())
                        .code(componentCode != null ? componentCode.trim() : null)
                        .educationalProgram(opp)
                        .department(dept)
                        .credits(credits)
                        .totalHours(totalHours)
                        .auditoryHours(auditoryHours)
                        .hoursLecture(hoursLecture)
                        .hoursGroup(hoursGroup)
                        .hoursPractical(hoursPractical)
                        .hoursLab(null) // в Excel практичні+лабораторні об'єднані в col 15
                        .hoursSelfStudy(hoursSelfStudy)
                        .examSemesters(examSem)
                        .creditSemesters(creditSem)
                        .hoursBySemester(toJson(hoursBySem))
                        .creditsBySemester(toJson(creditsBySem))
                        .controlTypes(toJson(controlTypesList))
                        .build();

                imported.add(disc);
            }
        }

        if (!imported.isEmpty()) {
            disciplineRepository.saveAll(imported);
        }

        log.info("Imported {} disciplines from Excel", imported.size());
        return imported.size();
    }

    /**
     * Нормалізує shortCode: стискає пробіли, замінює кириличну К↔латинську K тощо.
     */
    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.trim()
                .replaceAll("\\s+", " ")           // multiple spaces → single
                .replace("К", "K")                   // Ukrainian К → Latin K
                .replace("к", "k");                   // Ukrainian к → Latin k
    }

    private String getStringValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == Math.floor(val)) {
                return String.valueOf((int) val);
            }
            return String.valueOf(val);
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getStringCellValue();
            } catch (Exception e) {
                try {
                    double val = cell.getNumericCellValue();
                    if (val == Math.floor(val)) return String.valueOf((int) val);
                    return String.valueOf(val);
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        return null;
    }

    private Double getNumericValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                String s = cell.getStringCellValue().trim().replace(",", ".");
                return s.isEmpty() ? null : Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntValue(Cell cell) {
        Double val = getNumericValue(cell);
        return val != null ? (int) Math.round(val) : null;
    }

    private String toJson(Object obj) {
        try {
            if (obj instanceof Map<?,?> map && map.isEmpty()) return null;
            if (obj instanceof List<?> list && list.isEmpty()) return null;
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // ── Teacher Discipline ──

    public List<TeacherDiscipline> findTeacherDisciplines(Long teacherId) {
        return teacherDisciplineRepository.findByTeacherId(teacherId);
    }

    public List<TeacherDiscipline> findTeachersByDiscipline(Long disciplineId) {
        return teacherDisciplineRepository.findByDisciplineId(disciplineId);
    }

    public TeacherDiscipline findTeacherDisciplineById(Long id) {
        return teacherDisciplineRepository.findById(id).orElse(null);
    }

    @Transactional
    public TeacherDiscipline assignTeacherDiscipline(TeacherDiscipline td) {
        TeacherDiscipline saved = teacherDisciplineRepository.save(td);
        if (saved.getTeacher() != null && saved.getDiscipline() != null) {
            events.publishEvent(new ComplianceEvents.TeacherDisciplineAssigned(
                    saved.getTeacher().getId(), saved.getDiscipline().getId()));
        }
        return saved;
    }

    @Transactional
    public void removeTeacherDiscipline(Long id) {
        TeacherDiscipline td = teacherDisciplineRepository.findById(id).orElse(null);
        Long tid = td != null && td.getTeacher() != null ? td.getTeacher().getId() : null;
        Long did = td != null && td.getDiscipline() != null ? td.getDiscipline().getId() : null;
        teacherDisciplineRepository.deleteById(id);
        if (tid != null && did != null) {
            events.publishEvent(new ComplianceEvents.TeacherDisciplineRemoved(tid, did));
        }
    }

    // ── Documents ──

    public List<DisciplineDocument> findDocumentsByDiscipline(Long disciplineId) {
        return documentRepository.findByDisciplineId(disciplineId);
    }

    public List<DisciplineDocument> findDocumentsByTeacher(Long teacherId) {
        return documentRepository.findByTeacherId(teacherId);
    }

    public List<DisciplineDocument> findDocumentsByStatus(DocumentStatus status) {
        return documentRepository.findByStatus(status);
    }

    @Transactional
    public DisciplineDocument createDocument(DisciplineDocument doc) {
        return documentRepository.save(doc);
    }

    @Transactional
    public DisciplineDocument updateDocument(Long id, DisciplineDocument updated) {
        DisciplineDocument existing = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Документ не знайдено: " + id));
        existing.setType(updated.getType());
        existing.setStatus(updated.getStatus());
        existing.setDeadline(updated.getDeadline());
        existing.setFileUrl(updated.getFileUrl());
        existing.setNotes(updated.getNotes());
        return documentRepository.save(existing);
    }

    @Transactional
    public void deleteDocument(Long id) {
        documentRepository.deleteById(id);
    }
}
