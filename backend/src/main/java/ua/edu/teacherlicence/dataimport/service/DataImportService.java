package ua.edu.teacherlicence.dataimport.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.repository.AchievementRepository;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.department.service.DepartmentService;
import ua.edu.teacherlicence.discipline.model.Discipline;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.DisciplineRepository;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.fakhove.dto.VerificationResult;
import ua.edu.teacherlicence.fakhove.model.JournalCategory;
import ua.edu.teacherlicence.fakhove.service.FakhovyiJournalService;
import ua.edu.teacherlicence.publication.model.ApprobationSubtype;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.MethodicalSubtype;
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

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataImportService {

    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final AchievementRepository achievementRepository;
    private final DepartmentRepository departmentRepository;
    private final DisciplineRepository disciplineRepository;
    private final TeacherDisciplineRepository teacherDisciplineRepository;
    private final PublicationRepository publicationRepository;
    private final QualificationImprovementRepository qualificationRepository;
    private final CareerRecordRepository careerRecordRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final DepartmentService departmentService;
    private final FakhovyiJournalService fakhovyiJournalService;
    private final PpDataParser ppDataParser;
    private final ua.edu.teacherlicence.achievement.service.AchievementComposer achievementComposer;
    private final DisciplineMatcher disciplineMatcher;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final AcademicTitleRepository academicTitleRepository;

    /**
     * AI-парсер публікацій (опціональний — доступний тільки при ai.enabled=true).
     */
    @Setter(onMethod_ = @Autowired(required = false))
    private PublicationAiParser aiParser;

    /**
     * AI-валідація ppData (опціональна — доступна тільки при ai.enabled=true).
     */
    @Setter(onMethod_ = @Autowired(required = false))
    private ua.edu.teacherlicence.ppdata.service.PpDataValidationService ppDataValidationService;

    /**
     * Regex для секцій п.38 пп.X — підтримує формати:
     * п.38 пп.1.  |  п.38.пп.2.  |  п.38 пп .7  |  п.38 пп. 12
     */
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "п[.]?\\s*38[.]?\\s*пп[.]?\\s*(\\d{1,2})[.)]?",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Імпортує дані викладачів з DOCX таблиці кадрового забезпечення.
     *
     * Реальна структура DOCX:
     * - ROW 0 (11 cells): Заголовок таблиці
     * - ROWs 1..N (≥10 cells): Основні дані кожного викладача (всі підряд)
     * - ROWs N+1.. (1 cell, парами): Мітка-ім'я + детальний текст досягнень
     *   Пара: "Редзюк Є.В." → повний текст пп.1-20
     */
    public ImportResult importFromDocx(InputStream inputStream, Long departmentId) {
        ImportResult result = new ImportResult();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            Department department = departmentId != null
                    ? departmentRepository.findById(departmentId).orElse(null)
                    : null;

            for (XWPFTable table : document.getTables()) {
                List<XWPFTableRow> rows = table.getRows();
                if (rows.isEmpty()) continue;

                // ================================================================
                // PHASE 1: Розділяємо рядки на дані (≥10 комірок) і деталі (1 комірка)
                // ================================================================
                List<XWPFTableRow> dataRows = new ArrayList<>();
                List<XWPFTableRow> detailRows = new ArrayList<>();

                for (int i = 1; i < rows.size(); i++) { // пропускаємо заголовок
                    XWPFTableRow row = rows.get(i);
                    if (row.getTableCells().size() >= 10) {
                        dataRows.add(row);
                    } else {
                        detailRows.add(row);
                    }
                }

                log.info("DOCX structure: {} data rows, {} detail rows", dataRows.size(), detailRows.size());

                // ================================================================
                // PHASE 2: Парсимо та зберігаємо викладачів із рядків даних
                // ================================================================
                Map<String, Teacher> savedTeachers = new LinkedHashMap<>();

                for (int idx = 0; idx < dataRows.size(); idx++) {
                    try {
                        Teacher teacher = parseDataRow(dataRows.get(idx), department, result);
                        if (teacher != null) {
                            savedTeachers.put(teacher.getLastName().toUpperCase(), teacher);
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing data row {}: {}", idx, e.getMessage());
                        result.errors.add("Рядок даних " + idx + ": " + e.getMessage());
                    }
                }

                log.info("Saved {} teachers from data rows", savedTeachers.size());

                // ================================================================
                // PHASE 3: Обробляємо рядки деталей парами (мітка-ім'я + досягнення)
                // ================================================================
                int d = 0;
                while (d < detailRows.size()) {
                    try {
                        String rowText = getRowText(detailRows.get(d));

                        // Шукаємо відповідного викладача за прізвищем у мітці
                        Teacher matched = matchTeacherByNameLabel(rowText, savedTeachers);

                        if (matched != null && d + 1 < detailRows.size()) {
                            // Наступний рядок — детальний текст досягнень
                            String achievementText = getRowText(detailRows.get(d + 1));
                            log.info("Processing achievements for {} ({} chars)",
                                    matched.getLastName(), achievementText.length());

                            processAchievementText(achievementText, matched, result);

                            // Валідуємо ppData + перегенеруємо досягнення
                            if (ppDataValidationService != null) {
                                try {
                                    ppDataValidationService.validateAll(matched.getId());
                                    log.info("Validated ppData and recomposed achievements for {}",
                                            matched.getLastName());
                                } catch (Exception valEx) {
                                    log.warn("PpData validation failed for {}: {}, falling back to recompose",
                                            matched.getLastName(), valEx.getMessage());
                                    try {
                                        achievementComposer.recomposeForTeacher(matched);
                                    } catch (Exception composeEx) {
                                        log.warn("Failed to recompose achievements for {}: {}",
                                                matched.getLastName(), composeEx.getMessage());
                                    }
                                }
                            } else {
                                try {
                                    achievementComposer.recomposeForTeacher(matched);
                                } catch (Exception composeEx) {
                                    log.warn("Failed to recompose achievements for {}: {}",
                                            matched.getLastName(), composeEx.getMessage());
                                }
                            }

                            d += 2;
                        } else if (matched != null) {
                            log.warn("Name label found but no achievement row: {}", rowText);
                            d++;
                        } else {
                            // Може бути об'єднаний рядок (мітка-ім'я відсутня, текст великий)
                            // Спробуємо знайти відповідного викладача за вмістом
                            if (rowText.length() > 200) {
                                log.warn("Large unmatched detail row ({} chars), skipping: {}...",
                                        rowText.length(), rowText.substring(0, Math.min(100, rowText.length())));
                            }
                            d++;
                        }
                    } catch (Exception e) {
                        log.warn("Error processing detail row {}: {}", d, e.getMessage());
                        result.errors.add("Деталі рядок " + d + ": " + e.getMessage());
                        d++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error importing DOCX", e);
            result.errors.add("Загальна помилка: " + e.getMessage());
        }

        log.info("Import complete: {} teachers, {} achievements, {} publications, {} ppData, {} disciplines, {} qualifications, {} career records, {} languages, {} errors",
                result.teachersImported, result.achievementsImported,
                result.publicationsImported, result.ppDataImported, result.disciplinesAssigned,
                result.qualificationsImported, result.careerRecordsImported,
                result.languageSkillsImported, result.errors.size());

        return result;
    }

    // ================================================================
    // PHASE 2: Парсинг рядка даних викладача (11 стовпців)
    // ================================================================

    private Teacher parseDataRow(XWPFTableRow dataRow, Department department, ImportResult result) {
        List<XWPFTableCell> cells = dataRow.getTableCells();
        if (cells.size() < 10) return null;

        // Визначити зсув: якщо перша колонка — номер рядка (№ з/п), зсув = 1
        int offset = 0;
        String firstCell = getCellText(cells.get(0));
        if (firstCell != null && firstCell.trim().matches("^\\d{1,3}\\.?$")) {
            offset = 1;
        }

        // Col 0+offset: ПІБ, рік народження, звання, стаж
        String col0 = getCellText(cells.get(offset));
        Teacher teacher = parseTeacherBasicInfo(col0);

        if (teacher.getLastName() == null || teacher.getLastName().isEmpty()) {
            return null;
        }

        // Col 1: Посада (обрізаємо назву кафедри — зберігаємо тільки чисту посаду)
        if (cells.size() > 1 + offset) {
            teacher.setPosition(cleanPosition(getCellText(cells.get(1 + offset))));
        }

        // Col 3: Освітня кваліфікація
        if (cells.size() > 3 + offset) {
            String eduText = getCellText(cells.get(3 + offset));
            parseEducation(teacher, eduText);
        }

        // Col 4: Наукова кваліфікація — парсимо у holder, зберігаємо після teacherRepository.save
        ScientificParsed sci = null;
        if (cells.size() > 4 + offset) {
            String sciText = getCellText(cells.get(4 + offset));
            sci = parseScientific(sciText);
        }

        // Col 5: Послужний список (career records) — парсимо після save
        // (потрібен збережений teacher з id)

        // Col 6: Досвід бойових дій
        if (cells.size() > 6 + offset) {
            String combatText = getCellText(cells.get(6 + offset));
            if (combatText != null && !combatText.trim().isEmpty()
                    && !combatText.trim().equals("-") && !combatText.trim().equals("—")) {
                teacher.setCombatVeteranStatus(true);
                teacher.setCombatExperienceDates(convertIsoDatesToUkr(combatText.trim()));
                // Номер посвідчення УБД
                Matcher ubdDocMatcher = Pattern.compile("[Пп]освідчення\\s+(?:УБД\\s*)?[№\\u2116]?\\s*([^\\s,;]+)").matcher(combatText);
                if (ubdDocMatcher.find()) {
                    teacher.setCombatVeteranDoc("Посвідчення УБД №" + ubdDocMatcher.group(1).trim());
                }
                // Дата посвідчення: "від DD.MM.YYYY" або "від YYYY-MM-DD"
                Matcher ubdDateMatcher = Pattern.compile("від\\s+(\\d{2}\\.\\d{2}\\.\\d{2,4}|\\d{4}-\\d{2}-\\d{2})").matcher(combatText);
                if (ubdDateMatcher.find()) {
                    teacher.setCombatVeteranDocDate(parseDate(ubdDateMatcher.group(1)));
                }
                // Ким видано: "видано/видане XXXX" або "ким видано: XXXX"
                Matcher ubdIssuedMatcher = Pattern.compile("(?:видан[оеі]|ким\\s+видано)[:\\s]+([^,;\\n]+)").matcher(combatText);
                if (ubdIssuedMatcher.find()) {
                    teacher.setCombatVeteranDocIssuedBy(ubdIssuedMatcher.group(1).trim());
                }
            }
        }

        // Col 9: Публікації + ідентифікатори
        if (cells.size() > 9 + offset) {
            parseIdentifiers(teacher, getCellText(cells.get(9 + offset)));
        }

        // Встановлюємо кафедру
        teacher.setDepartment(department);

        // Визначаємо тип зайнятості
        String positionText = teacher.getPosition();
        if (positionText != null && positionText.toLowerCase().contains("сумісни")) {
            teacher.setEmploymentType("PART_TIME");
        } else {
            teacher.setEmploymentType("MAIN");
        }

        // Зберігаємо
        teacher = teacherRepository.save(teacher);
        result.teachersImported++;
        result.importedTeacherIds.add(teacher.getId());

        // Гарантуємо що для викладача буде штатна позиція (auto-bootstrap якщо немає).
        // Так нові імпорти одразу мають коректний effectivePosition без чекання на міграцію.
        teacherPositionService.ensureStaffPosition(teacher);

        // Зберігаємо science (degree/title) як окремі сутності — після збереження teacher
        if (sci != null) {
            persistScientific(teacher, sci);
        }

        // Авто-лінкування штатних посад по ПІБ
        departmentService.autoLinkStaffPositionsForTeacher(teacher);

        log.info("Saved teacher #{}: {} {} {}",
                result.teachersImported, teacher.getLastName(), teacher.getFirstName(), teacher.getPatronymic());

        // Col 2: Освітні компоненти (дисципліни)
        if (cells.size() > 2 + offset) {
            String disciplinesText = getCellText(cells.get(2 + offset));
            if (disciplinesText != null && !disciplinesText.trim().isEmpty()) {
                int discCount = parseDisciplines(disciplinesText, teacher, department, result.errors);
                result.disciplinesAssigned += discCount;
            }
        }

        // Col 5: Послужний список (career records)
        if (cells.size() > 5 + offset) {
            String careerText = getCellText(cells.get(5 + offset));
            if (careerText != null && !careerText.trim().isEmpty()) {
                int careerCount = parseCareerRecords(careerText, teacher);
                result.careerRecordsImported += careerCount;
            }
        }

        // Col 7: Іноземні мови
        if (cells.size() > 7 + offset) {
            String langText = getCellText(cells.get(7 + offset));
            if (langText != null && !langText.trim().isEmpty()) {
                int langCount = parseLanguageSkills(langText, teacher);
                result.languageSkillsImported += langCount;
            }
        }

        // Col 8: Підвищення кваліфікації
        if (cells.size() > 8 + offset) {
            String qualText = getCellText(cells.get(8 + offset));
            if (qualText != null && !qualText.trim().isEmpty()) {
                int qualCount = parseQualifications(qualText, teacher);
                result.qualificationsImported += qualCount;
            }
        }

        return teacher;
    }

    // ================================================================
    // PHASE 3: Обробка тексту досягнень (деталізація)
    // ================================================================

    /**
     * Обробляє повний текст досягнень одного викладача.
     * Розбиває на секції п.38 пп.X, створює Achievement та Publication.
     */
    private void processAchievementText(String text, Teacher teacher, ImportResult result) {
        // Розбиваємо на секції
        Map<Integer, String> sections = splitBySections(text);
        String preamble = extractPreamble(text);

        // Якщо є текст перед першою секцією (зазвичай Scopus/фахові публікації)
        // і немає явної секції пп.1, створюємо Achievement для пп.1
        if (!preamble.trim().isEmpty()) {
            boolean hasPp1 = sections.containsKey(1);
            if (!hasPp1) {
                AchievementType type = AchievementType.fromNumber(1);
                if (type != null) {
                    Achievement a = new Achievement();
                    a.setTeacher(teacher);
                    a.setAchievementType(type);
                    String desc = preamble.trim();
                    String shortDesc = desc.length() > 147 ? desc.substring(0, 147) + "..." : desc;
                    a.setTitle("пп.1 — " + shortDesc);
                    a.setDescription(desc.length() > 4000 ? desc.substring(0, 4000) : desc);
                    a.setVerified(false);
                    achievementRepository.save(a);
                    result.achievementsImported++;
                    result.createdAchievementIds.add(a.getId());
                }
            }

            // Парсимо публікації з преамбули (pp.1 context)
            int pubCount = parsePublicationEntries(preamble, teacher, null, 1);
            result.publicationsImported += pubCount;
        }

        // Обробляємо кожну секцію п.38 пп.X
        for (Map.Entry<Integer, String> entry : sections.entrySet()) {
            int ppNum = entry.getKey();
            String sectionText = entry.getValue().trim();

            if (sectionText.isEmpty()) continue;

            // Створюємо Achievement
            AchievementType type = AchievementType.fromNumber(ppNum);
            if (type != null) {
                Achievement a = new Achievement();
                a.setTeacher(teacher);
                a.setAchievementType(type);
                String shortDesc = sectionText.length() > 147 ? sectionText.substring(0, 147) + "..." : sectionText;
                a.setTitle("пп." + ppNum + " — " + shortDesc);
                a.setDescription(sectionText.length() > 4000 ? sectionText.substring(0, 4000) : sectionText);
                a.setVerified(false);

                // Спробуємо витягнути URL документа
                Matcher urlMatcher = Pattern.compile("https?://[^\\s,;]+").matcher(sectionText);
                if (urlMatcher.find()) {
                    a.setDocumentUrl(urlMatcher.group());
                }

                achievementRepository.save(a);
                result.achievementsImported++;
                result.createdAchievementIds.add(a.getId());
            }

            // Парсимо публікації з певних секцій (з ppType для зв'язку Publication→Achievement)
            int pubCount = 0;
            switch (ppNum) {
                case 1:  // Наукові публікації Scopus/WoS/фахові
                    pubCount = parsePublicationEntries(sectionText, teacher, null, 1);
                    break;
                case 2:  // Патенти, свідоцтва авторського права
                    pubCount = parsePublicationEntries(sectionText, teacher, null, 2);
                    break;
                case 3:  // Підручники, навчальні посібники
                    pubCount = parsePublicationEntries(sectionText, teacher, PublicationType.TEXTBOOK, 3);
                    break;
                case 4:  // Навчально-методичні праці
                    pubCount = parsePublicationEntries(sectionText, teacher, PublicationType.METHODICAL, 4);
                    break;
                case 12: // Тези доповідей, апробація
                    pubCount = parsePublicationEntries(sectionText, teacher, PublicationType.APPROBATION, 12);
                    break;
                // Секції 5-11, 13-20 — структуровані дані ppData
                default:
                    if (ppNum >= 5 && ppNum <= 20) {
                        try {
                            int ppDataCount = ppDataParser.parseSectionAndSave(ppNum, sectionText, teacher);
                            result.ppDataImported += ppDataCount;
                            log.debug("Parsed {} ppData records from pp.{} for {}",
                                    ppDataCount, ppNum, teacher.getLastName());
                        } catch (Exception e) {
                            log.warn("Failed to parse ppData for pp.{}: {}", ppNum, e.getMessage());
                        }
                    }
                    break;
            }
            result.publicationsImported += pubCount;
        }
    }

    /**
     * Розбиває текст на секції за маркерами п.38 пп.X.
     * Повертає Map: номер підпункту → текст секції.
     */
    private Map<Integer, String> splitBySections(String text) {
        Map<Integer, String> sections = new LinkedHashMap<>();

        Matcher matcher = SECTION_PATTERN.matcher(text);
        List<int[]> matches = new ArrayList<>(); // [start, end, number]

        while (matcher.find()) {
            int num = Integer.parseInt(matcher.group(1));
            if (num >= 1 && num <= 20) {
                matches.add(new int[]{matcher.start(), matcher.end(), num});
            }
        }

        for (int i = 0; i < matches.size(); i++) {
            int num = matches.get(i)[2];
            int contentStart = matches.get(i)[1]; // кінець маркера
            int contentEnd = (i + 1 < matches.size()) ? matches.get(i + 1)[0] : text.length();
            String content = text.substring(contentStart, contentEnd).trim();

            if (!content.isEmpty()) {
                // Якщо вже є секція з таким номером — додаємо текст
                sections.merge(num, content, (old, newText) -> old + "\n" + newText);
            }
        }

        return sections;
    }

    /**
     * Витягує текст перед першим маркером п.38 пп.X (преамбулу).
     */
    private String extractPreamble(String text) {
        Matcher matcher = SECTION_PATTERN.matcher(text);
        if (matcher.find()) {
            return text.substring(0, matcher.start()).trim();
        }
        return ""; // якщо немає секцій — все вже оброблено як секції
    }

    // ================================================================
    // Парсинг публікацій з тексту секції
    // ================================================================

    /**
     * Парсить окремі публікації з тексту секції.
     * Розбиває за нумерацією (1., 2., ...) або за рядками.
     *
     * @param ppNum номер підпункту п.38 (для ppType та sourceSection)
     */
    private int parsePublicationEntries(String text, Teacher teacher, PublicationType defaultType, int ppNum) {
        if (text == null || text.trim().isEmpty()) return 0;

        // Трекаємо тип секції за підзаголовками
        PublicationType currentSectionType = defaultType;

        // Розбиваємо за нумерацією: "1.", "2.", ... на початку рядка
        String[] entries = text.split("(?=(?:^|\\n)\\s*\\d{1,2}\\s*[.)]\\s)");

        // ======== Фаза 1: Збираємо записи та базові поля (type, year, DOI, URL, journal) ========
        List<String> cleanedEntries = new ArrayList<>();
        List<Publication> pubs = new ArrayList<>();

        for (String entry : entries) {
            String cleaned = entry.trim()
                    .replaceAll("^\\d{1,2}\\s*[.)]\\s*", "")  // прибираємо номер
                    .trim();

            // Оновлюємо тип секції за підзаголовками
            if (cleaned.toLowerCase().contains("статті scopus") || cleaned.toLowerCase().contains("scopus:")) {
                currentSectionType = PublicationType.ARTICLE;
            }
            if (cleaned.toLowerCase().contains("фахових виданнях") || cleaned.toLowerCase().contains("фахові видання")) {
                currentSectionType = PublicationType.ARTICLE;
            }

            // Прибираємо підзаголовки-маркери (на початку та в кінці рядка)
            cleaned = cleaned
                    .replaceAll("(?i)^Статті\\s+Scopus\\s*:?\\s*", "")
                    .replaceAll("(?i)^Статті\\s+у\\s+фахових\\s+виданнях\\s*:?\\s*", "")
                    .replaceAll("(?i)^Конспект\\s+лекцій\\s*:?\\s*", "")
                    // Прибираємо підзаголовки-маркери з КІНЦЯ тексту (злиті через \n)
                    .replaceAll("(?i)\\n\\s*Статті\\s+Scopus\\s*:?\\s*$", "")
                    .replaceAll("(?i)\\n\\s*Статті\\s+у\\s+фахових\\s+виданнях\\s*:?\\s*$", "")
                    .replaceAll("(?i)\\n\\s*Конспект\\s+лекцій\\s*:?\\s*$", "")
                    .replaceAll("(?i)\\n\\s*Навчальні\\s+посібники\\s*:?\\s*$", "")
                    .replaceAll("(?i)\\n\\s*Підручники\\s*:?\\s*$", "")
                    .trim();

            if (cleaned.length() < 15) continue;

            // Пропускаємо записи, які НЕ є публікаціями
            if (isNonPublicationEntry(cleaned)) continue;

            Publication pub = new Publication();
            pub.setTeacher(teacher);
            pub.setSourceSection("pp." + ppNum);

            // Визначаємо тип
            PublicationType detectedType = detectPublicationType(cleaned);
            if (detectedType == PublicationType.OTHER && currentSectionType != null) {
                detectedType = currentSectionType;
            }
            pub.setType(detectedType);

            // Визначаємо підтип для METHODICAL
            if (detectedType == PublicationType.METHODICAL) {
                pub.setMethodicalSubtype(detectMethodicalSubtype(cleaned));
            }

            // Визначаємо підтип (рівень видання) для APPROBATION та POPULAR_SCIENTIFIC
            if (detectedType == PublicationType.APPROBATION || detectedType == PublicationType.POPULAR_SCIENTIFIC) {
                pub.setApprobationSubtype(detectApprobationSubtype(cleaned));
            }

            // Визначаємо articleCategory для ARTICLE (Scopus/WoS/фахові з тексту)
            // Для APPROBATION — рівень видання вже визначено через approbationSubtype вище
            if (detectedType == PublicationType.ARTICLE) {
                ArticleCategory textCategory = detectArticleCategoryFromText(cleaned);
                if (textCategory != null) {
                    pub.setArticleCategory(textCategory);
                }
            }

            // Встановлюємо ppType на основі секції
            AchievementType ppType = AchievementType.fromNumber(ppNum);
            if (ppType != null) {
                pub.setPpType(ppType);
            }

            // Рік публікації + повна дата (за замовчуванням YYYY-01-01)
            Matcher yearMatcher = Pattern.compile("(20[12]\\d)").matcher(cleaned);
            if (yearMatcher.find()) {
                int y = Integer.parseInt(yearMatcher.group(1));
                pub.setYear(y);
                pub.setPublicationDate(java.time.LocalDate.of(y, 1, 1));
            }

            // DOI
            Matcher doiMatcher = Pattern.compile("(10\\.\\d{4,}/[^\\s,;]+)").matcher(cleaned);
            if (doiMatcher.find()) {
                pub.setDoi(doiMatcher.group(1));
            }

            // URL
            Matcher urlMatcher = Pattern.compile("(https?://[^\\s,;]+)").matcher(cleaned);
            if (urlMatcher.find()) {
                pub.setUrl(urlMatcher.group(1));
            }

            // Журнал/Видання — кілька стратегій пошуку
            extractJournalName(cleaned, pub);

            cleanedEntries.add(cleaned);
            pubs.add(pub);
        }

        // ======== Фаза 2: AI-парсинг title/authors/pages/volume (з fallback на regex) ========
        if (aiParser != null && !cleanedEntries.isEmpty()) {
            log.info("Using AI parser for {} publication entries", cleanedEntries.size());
            try {
                List<PublicationAiParser.ParsedFields> aiResults = aiParser.parseEntries(cleanedEntries);
                for (int i = 0; i < pubs.size(); i++) {
                    PublicationAiParser.ParsedFields ai = (i < aiResults.size()) ? aiResults.get(i) : null;
                    if (ai != null && ai.title() != null && !ai.title().isBlank()) {
                        // AI спрацював — використовуємо AI результат
                        pubs.get(i).setTitle(ai.title());
                        if (ai.authors() != null) pubs.get(i).setAuthors(ai.authors());
                        if (ai.pages() != null) pubs.get(i).setPages(ai.pages());
                        if (ai.volume() != null) pubs.get(i).setVolume(ai.volume());
                    } else {
                        // AI не спрацював для цього запису — fallback на regex
                        log.debug("AI returned null for entry {}, falling back to regex", i);
                        decomposePublicationEntry(cleanedEntries.get(i), pubs.get(i));
                    }
                }
            } catch (Exception e) {
                log.warn("AI parser failed completely, falling back to regex for all entries: {}", e.getMessage());
                for (int i = 0; i < pubs.size(); i++) {
                    decomposePublicationEntry(cleanedEntries.get(i), pubs.get(i));
                }
            }
        } else {
            // AI недоступний — regex для всіх
            for (int i = 0; i < pubs.size(); i++) {
                decomposePublicationEntry(cleanedEntries.get(i), pubs.get(i));
            }
        }

        // ======== Фаза 2.5: Дедуплікація ========
        // Видаляємо дублікати в рамках поточного батчу (normalize(title) + year + normalize(journal))
        List<Publication> deduplicated = deduplicatePublications(pubs, teacher);
        if (deduplicated.size() < pubs.size()) {
            log.info("Deduplication: {} → {} publications (removed {} duplicates)",
                    pubs.size(), deduplicated.size(), pubs.size() - deduplicated.size());
        }

        // ======== Фаза 3: Верифікація фахових видань + збереження ========
        int count = 0;
        for (Publication pub : deduplicated) {
            try {
                // Верифікація ARTICLE через довідник фахових/Scopus видань
                if (pub.getType() == PublicationType.ARTICLE && pub.getArticleCategory() == null) {
                    verifyAndSetArticleCategory(pub);
                }
                // Перевірка актуальності (5 років від точної дати)
                java.time.LocalDate effective = pub.effectiveDate();
                if (effective != null) {
                    java.time.LocalDate cutoff = java.time.LocalDate.now().minusYears(5);
                    if (effective.isBefore(cutoff)) {
                        pub.setStatus(ua.edu.teacherlicence.publication.model.PublicationStatus.OUTDATED);
                        log.info("Publication '{}' (date={}) is OUTDATED", pub.getTitle(), effective);
                    }
                }
                publicationRepository.save(pub);
                count++;
            } catch (Exception e) {
                log.warn("Failed to save publication: {}", e.getMessage());
            }
        }

        return count;
    }

    /**
     * Витягує назву журналу/видання з тексту публікації.
     * Використовує кілька стратегій пошуку (regex).
     */
    private void extractJournalName(String cleaned, Publication pub) {
        String journalName = null;
        // Лямбда для перевірки хибних спрацювань (ISBN, кількість сторінок)
        java.util.function.Predicate<String> isFalsePositive = jn ->
                jn.contains("ISBN") || Pattern.compile("[—–]\\s*\\d+\\s*с\\.").matcher(jn).find();

        // 1. Між "//" (не URL!) та рік або "—" або "; м."
        Matcher journalMatcher = Pattern.compile(
                "(?<!:)//\\s*(.+?)(?:,\\s*\\d{4}|(?:\\.\\s*)?[—–]|\\d{4}\\s*\\.|;\\s*м\\.)")
                .matcher(cleaned);
        if (journalMatcher.find()) {
            String jCandidate = journalMatcher.group(1).trim()
                    .replaceAll("[.,;:]+$", "").trim();
            if (jCandidate.length() > 200) {
                int cutAt = -1;
                Matcher cutMatcher = Pattern.compile("[»\"\\u201D]\\s*\\(").matcher(jCandidate);
                if (cutMatcher.find()) {
                    cutAt = cutMatcher.start() + 1;
                }
                if (cutAt > 20) {
                    jCandidate = jCandidate.substring(0, cutAt).trim();
                }
            }
            if (jCandidate.length() >= 3 && jCandidate.length() <= 200 && !isFalsePositive.test(jCandidate)) {
                journalName = jCandidate;
            }
        }

        // 2. Посібник/Підручник — витягуємо назву в лапках (перед publisher щоб мав пріоритет)
        if (journalName == null) {
            Matcher textbookMatcher = Pattern.compile(
                    "(?:навчальний\\s+)?(?:посібник|підручник)\\s+[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(cleaned);
            if (textbookMatcher.find()) {
                journalName = "Посібник \u00AB" + textbookMatcher.group(1).trim() + "\u00BB";
            }
        }

        // 3. Книги/посібники: видавець після "– К.:" або "К:" або "– Київ:"
        if (journalName == null) {
            Matcher publisherMatcher = Pattern.compile(
                    "(?:[—–-]\\s*)?[КK]\\.?\\s*(?:иїв)?\\s*[.:]+\\s*(.+?)(?:,\\s*\\d{4}|\\d{4}\\s*[.,])")
                    .matcher(cleaned);
            if (publisherMatcher.find()) {
                String pubCandidate = publisherMatcher.group(1).trim()
                        .replaceAll("[.,;:]+$", "").trim();
                if (!isFalsePositive.test(pubCandidate)) {
                    journalName = pubCandidate;
                }
            }
        }

        // 4. Англомовні статті APA: "Title. JournalName, Vol(Issue), Pages."
        if (journalName == null) {
            String[] apaKeywords = {"Journal of", "Technologies,", "Review,", "Letters,",
                    "Science,", "Proceedings,", "Conference,"};
            for (String kw : apaKeywords) {
                int kwIdx = cleaned.indexOf(kw);
                if (kwIdx < 0) continue;
                int beforeStart = Math.max(0, kwIdx - 200);
                String before = cleaned.substring(beforeStart, kwIdx);
                int sentenceBound = -1;
                for (int i = before.length() - 2; i >= 0; i--) {
                    char c = before.charAt(i);
                    char next = before.charAt(i + 1);
                    if (c == '.' && (next == ' ' || next == '\n' || next == '\t'
                            || next == '\u00A0' || next == '\r')) {
                        sentenceBound = i;
                        break;
                    }
                }
                int start = sentenceBound >= 0 ? sentenceBound + 2 : 0;
                String after = cleaned.substring(kwIdx);
                Matcher endMatcher = Pattern.compile(",\\s*\\d").matcher(after);
                int end;
                if (endMatcher.find()) {
                    end = kwIdx + endMatcher.start();
                } else {
                    continue;
                }
                int absStart = beforeStart + start;
                String candidate = cleaned.substring(absStart, end).trim();
                if (candidate.length() >= 10 && candidate.length() <= 200
                        && Character.isUpperCase(candidate.charAt(0))) {
                    journalName = candidate;
                    break;
                }
            }
        }

        // 5. "Науковий збірник", "Збірник матеріалів/тез" з назвою в лапках
        if (journalName == null) {
            Matcher zbirnykMatcher = Pattern.compile(
                    "([Нн]ауковий\\s+збірник|[Зз]бірник\\s+(?:матеріалів|тез)(?:\\s+доповідей)?)\\s+[\\u201E\\u201C\\u201D\\u00AB\"«\u201F](.+?)[\\u201D\\u201C\\u00BB\"»\u201E]",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(cleaned);
            if (zbirnykMatcher.find()) {
                journalName = zbirnykMatcher.group(1) + " \u00AB" + zbirnykMatcher.group(2) + "\u00BB";
            }
        }

        // 6. Конференція з назвою в лапках — захоплюємо префікс + «назву»
        if (journalName == null) {
            Matcher confMatcher = Pattern.compile(
                    "((?:[А-ЯІЇЄҐа-яіїєґA-Za-z0-9'ʼ\\-]+\\s+){0,8}" +
                    "(?:конференц[а-яіїєґА-ЯІЇЄҐ]*|conference))" +
                    "\\s+[\"«\\u201C\\u201E]([^\"»\\u201D\\u201F]+)[\"»\\u201D\\u201F]",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(cleaned);
            if (confMatcher.find()) {
                journalName = confMatcher.group(1).trim() + " \u00AB" + confMatcher.group(2).trim() + "\u00BB";
            }
        }

        // 7. CEUR Workshop Proceedings
        if (journalName == null && cleaned.contains("CEUR")) {
            Matcher ceurMatcher = Pattern.compile("(CEUR\\s+Work[Ss]hop\\s+Proceedings)").matcher(cleaned);
            if (ceurMatcher.find()) {
                journalName = ceurMatcher.group(1);
            }
        }

        // 8. Конференція як фоллбек: "N-а/ІІІ Міжнародна ... конференція" без лапок
        if (journalName == null) {
            Matcher confFallbackMatcher = Pattern.compile(
                    "((?:\\d+-[аяеє]\\s+|[IVXLCDMІ]+\\s+)?міжнародн[а-яіїєґА-ЯІЇЄҐ]*\\s+науков\\S+\\s+конференц[а-яіїєґА-ЯІЇЄҐ]*)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(cleaned);
            if (confFallbackMatcher.find()) {
                journalName = confFallbackMatcher.group(1).trim();
            }
        }

        // 9. Матеріали конференції: "Матеріали ... конференції"
        if (journalName == null) {
            Matcher materialsMatcher = Pattern.compile(
                    "(матеріали\\s+.{5,80}?конференц[а-яіїєґА-ЯІЇЄҐ]*)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(cleaned);
            if (materialsMatcher.find()) {
                journalName = materialsMatcher.group(1).trim()
                        .replaceAll("[.,;:]+$", "").trim();
            }
        }

        // 10. Фоллбек: організація (Інститут ..., Військовий інститут ...)
        if (journalName == null) {
            Matcher orgMatcher = Pattern.compile(
                    "((?:Інститут|Військовий\\s+інститут|Київський\\s+національний\\s+університет)\\s+[а-яіїєґА-ЯІЇЄҐ'ʼ\\s]+(?:України|Крут|Шевченка))")
                    .matcher(cleaned);
            if (orgMatcher.find()) {
                journalName = orgMatcher.group(1).trim()
                        .replaceAll("[.,;:]+$", "").trim();
            }
        }

        if (journalName != null && journalName.length() >= 3 && journalName.length() <= 300) {
            // Видаляємо ISBN номер з початку назви журналу
            journalName = journalName.replaceAll("^\\d{3}-\\d[\\d-]+\\s+", "").trim();
            pub.setJournalName(journalName);
        }
    }

    /**
     * Розкладає повний текст публікації на окремі поля: назва, співавтори, сторінки, том.
     * DOI, URL, рік та журнал витягуються окремо в parsePublicationEntries().
     */
    private void decomposePublicationEntry(String cleaned, Publication pub) {
        String remaining = cleaned;

        // 1. Сторінки: С. 10-20, С.36-42, pp. 10-20, P. 10-20
        Matcher pageRangeMatcher = Pattern.compile(
                "[СсCcPp](?:p)?\\.\\s*(\\d+\\s*[-–]\\s*\\d+)").matcher(remaining);
        if (pageRangeMatcher.find()) {
            pub.setPages(pageRangeMatcher.group(1).replaceAll("\\s+", ""));
        } else {
            // Загальна кількість сторінок книги: – 244 с.
            Matcher totalPagesMatcher = Pattern.compile("[—–-]\\s*(\\d{2,4})\\s*с\\.").matcher(remaining);
            if (totalPagesMatcher.find()) {
                pub.setPages(totalPagesMatcher.group(1) + " с.");
            }
        }

        // 2. Том/Випуск: Т.5, Том 5, Vol.5, Vol-3909, Вип. 4
        Matcher volMatcher = Pattern.compile(
                "(?:[ТтTt](?:ом)?|[Vv]ol|[Вв]ип)\\.?\\s*[-]?\\s*(\\d+(?:\\s*\\([^)]*\\))?)")
                .matcher(remaining);
        if (volMatcher.find()) {
            pub.setVolume(volMatcher.group(1).trim());
        }

        // 3. Автори
        // 3a. Формат: Прізвище І.Б., Прізвище2 І.Б. (ініціали з крапками на початку)
        Matcher authorMatcher = Pattern.compile(
                "^((?:[A-ZА-ЯІЇЄҐ][a-zA-Zа-яіїєґА-ЯІЇЄҐ'ʼ\\-]{1,30}\\s+" +
                "[A-ZА-ЯІЇЄҐ]\\.(?:\\s?[A-ZА-ЯІЇЄҐ]\\.)?(?:\\s*,\\s*)?)+)")
                .matcher(remaining);
        if (authorMatcher.find()) {
            String authors = authorMatcher.group(1).trim().replaceAll("[,;\\s]+$", "").trim();
            if (authors.length() >= 4) {
                pub.setAuthors(authors);
                remaining = remaining.substring(authorMatcher.end()).trim();
            }
        }
        // 3b. Автори у квадратних дужках: / [Ім'я Прізвище, Ім'я2 Прізвище2]
        if (pub.getAuthors() == null) {
            Matcher bracketAuthorMatcher = Pattern.compile(
                    "/\\s*\\[([^\\]]{5,})\\]").matcher(remaining);
            if (bracketAuthorMatcher.find()) {
                pub.setAuthors(bracketAuthorMatcher.group(1).trim());
            }
        }

        // 4. Назва публікації
        String title = null;

        // 4a. Якщо є // → назва = текст до // (основний роздільник у українських цитуваннях)
        int slashSlashIdx = remaining.indexOf("//");
        if (slashSlashIdx > 5) {
            String before = remaining.substring(0, slashSlashIdx).trim();
            if (!before.endsWith("http:") && !before.endsWith("https:")) {
                title = before.replaceAll("[.,;:]+$", "").trim();
            }
        }

        // 4b. Посібник/підручник/монографія: назва в лапках «...»
        if (title == null) {
            String lower = remaining.toLowerCase();
            if (lower.contains("посібник") || lower.contains("підручник") || lower.contains("монографія")) {
                Matcher quoteTitleMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]{5,})[»\"\\u201D\\u201F]")
                        .matcher(remaining);
                if (quoteTitleMatcher.find()) {
                    title = quoteTitleMatcher.group(1).trim();
                }
            }
        }

        // 4c. Англомовний APA: назва закінчується перед ". " + велика латинська буква
        if (title == null) {
            Matcher apaTitleMatcher = Pattern.compile(
                    "^(.{15,}?)\\.\\s+(?=[A-Z][a-z])").matcher(remaining);
            if (apaTitleMatcher.find()) {
                String candidate = apaTitleMatcher.group(1).trim();
                if (candidate.length() >= 10 && candidate.length() <= 400) {
                    title = candidate;
                }
            }
        }

        // 4d. Фоллбек: перше речення (до ". " + велика буква/цифра або " — ")
        if (title == null) {
            Matcher sentenceMatcher = Pattern.compile(
                    "^(.{15,}?)(?:\\.\\s+[A-ZА-ЯІЇЄҐ\\d]|\\s+[—–]\\s+)").matcher(remaining);
            if (sentenceMatcher.find()) {
                title = sentenceMatcher.group(1).trim();
            }
        }

        // 4e. Останній фоллбек: весь remaining
        if (title == null || title.isEmpty()) {
            title = remaining;
        }

        // Очищуємо назву від DOI, URL, метаданих, дат конференцій, тез
        title = title
                .replaceAll("\\s*(?:DOI|doi)\\s*:?\\s*10\\.\\d{4,}/\\S+", "")
                .replaceAll("\\s*https?://\\S+", "")
                // Видаляємо "тези доповідей ..." та все після нього
                .replaceAll("(?i)\\s*[,.]?\\s*тези\\s+доповідей.*$", "")
                // Видаляємо "/ [автори]" (автори у квадратних дужках)
                .replaceAll("\\s*/\\s*\\[([^\\]]+)\\]", "")
                .trim()
                .replaceAll("[.,;:!]+$", "")
                .trim();

        if (title.length() > 500) title = title.substring(0, 500);
        if (title.isEmpty()) title = cleaned.substring(0, Math.min(cleaned.length(), 200));

        pub.setTitle(title);
    }

    /**
     * Перевіряє, чи є запис НЕ публікацією (робочі програми, ОПП тощо).
     */
    private boolean isNonPublicationEntry(String text) {
        String lower = text.toLowerCase();
        return (lower.startsWith("розробник робочих програм")
                || lower.startsWith("робочі програми навчальних")
                || lower.startsWith("освітньо-професійна програма")
                || (lower.contains("рівень вищої освіти") && text.length() < 200)
                || (lower.startsWith("спеціальність") && text.length() < 150)
                || (lower.startsWith("галузь знань") && text.length() < 100)
                || (lower.startsWith("кваліфікація") && text.length() < 150));
    }

    // ================================================================
    // Парсинг іноземних мов (Col 7)
    // ================================================================

    /**
     * Парсить стовпець "Відомості про рівень володіння іноземними мовами" (Col 7).
     *
     * Формати:
     * - "Англійська мова СМР 1+222 ВІТІ № 000452 від 26.06.2024"
     * - "Англійська для початківців. Elementary level (A1-A2). Дата видачі сертифікату 22.03.24 р. https://..."
     */
    private int parseLanguageSkills(String text, Teacher teacher) {
        if (text == null || text.trim().isEmpty()) return 0;
        int count = 0;

        // Розбиваємо на рядки — кожен може бути окремою мовою
        String[] lines = text.split("\\n");

        // Об'єднуємо рядки, що відносяться до однієї мови
        // (рядок з назвою мови + продовження)
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Новий запис починається з назви мови
            if (startsWithLanguageName(trimmed) && current.length() > 0) {
                entries.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(" ");
            current.append(trimmed);
        }
        if (current.length() > 0) {
            entries.add(current.toString().trim());
        }

        for (String entry : entries) {
            if (entry.length() < 5) continue;

            try {
                LanguageSkill skill = parseOneLanguageSkill(entry);
                if (skill != null) {
                    skill.setTeacher(teacher);
                    languageSkillRepository.save(skill);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Failed to save language skill: {}", e.getMessage());
            }
        }

        return count;
    }

    /**
     * Перевіряє, чи рядок починається з назви мови.
     */
    private boolean startsWithLanguageName(String text) {
        String lower = text.toLowerCase();
        return lower.startsWith("англійськ") || lower.startsWith("німецьк")
                || lower.startsWith("французьк") || lower.startsWith("іспанськ")
                || lower.startsWith("польськ") || lower.startsWith("турецьк")
                || lower.startsWith("китайськ") || lower.startsWith("арабськ")
                || lower.startsWith("english") || lower.startsWith("german")
                || lower.startsWith("french") || lower.startsWith("spanish");
    }

    /**
     * Парсить один запис іноземної мови.
     */
    private LanguageSkill parseOneLanguageSkill(String text) {
        LanguageSkill skill = new LanguageSkill();

        // Визначаємо мову
        String lower = text.toLowerCase();
        if (lower.contains("англійськ") || lower.contains("english")) {
            skill.setLanguage("Англійська");
        } else if (lower.contains("німецьк") || lower.contains("german")) {
            skill.setLanguage("Німецька");
        } else if (lower.contains("французьк") || lower.contains("french")) {
            skill.setLanguage("Французька");
        } else if (lower.contains("іспанськ") || lower.contains("spanish")) {
            skill.setLanguage("Іспанська");
        } else if (lower.contains("польськ") || lower.contains("polish")) {
            skill.setLanguage("Польська");
        } else {
            // Перше слово як назва мови
            String firstWord = text.split("\\s+")[0];
            skill.setLanguage(firstWord);
        }

        // Визначаємо рівень
        // СМР (стандартизований мовний рівень): "СМР 1+222", "СМР 2+222"
        Pattern smrPattern = Pattern.compile("СМР\\s*\\d[+]\\d+");
        Matcher smrMatcher = smrPattern.matcher(text);
        if (smrMatcher.find()) {
            skill.setLevel(smrMatcher.group());
        }

        // CEFR рівень: A1, A2, B1, B2, C1, C2
        if (skill.getLevel() == null) {
            Pattern cefrPattern = Pattern.compile("\\(([A-C][12](?:\\s*-\\s*[A-C][12])?)\\)");
            Matcher cefrMatcher = cefrPattern.matcher(text);
            if (cefrMatcher.find()) {
                skill.setLevel(cefrMatcher.group(1));
            }
        }

        // Текстовий рівень: Elementary, Intermediate, Advanced, etc.
        if (skill.getLevel() == null) {
            Pattern levelPattern = Pattern.compile("(?i)(Elementary|Pre-?Intermediate|Intermediate|Upper-?Intermediate|Advanced|Beginner|початків)",
                    Pattern.CASE_INSENSITIVE);
            Matcher levelMatcher = levelPattern.matcher(text);
            if (levelMatcher.find()) {
                skill.setLevel(levelMatcher.group());
            }
        }

        // Номер сертифіката: "№MIL-ENG-2024-001", "серт. №1234", "сертифікат №..."
        Pattern certNumPattern = Pattern.compile("(?:№|серт\\.?\\s*№?|сертифікат\\s*№?)\\s*([A-Za-Zа-яА-ЯіІїЇєЄґҐ0-9_/\\-]+)");
        Matcher certNumMatcher = certNumPattern.matcher(text);
        if (certNumMatcher.find()) {
            skill.setCertificateNumber(certNumMatcher.group(1).trim());
        }

        // Дата сертифіката: "від DD.MM.YYYY", "дата видачі DD.MM.YYYY", або просто DD.MM.YYYY
        Pattern certDatePattern = Pattern.compile("(?:від|видач[іа]|дат[аи])\\s*(\\d{2})[./](\\d{2})[./](\\d{4})");
        Matcher certDateMatcher = certDatePattern.matcher(text);
        if (certDateMatcher.find()) {
            try {
                skill.setCertificateDate(LocalDate.of(
                        Integer.parseInt(certDateMatcher.group(3)),
                        Integer.parseInt(certDateMatcher.group(2)),
                        Integer.parseInt(certDateMatcher.group(1))));
            } catch (Exception ignored) {}
        }

        // Організація: "видан(ий/а) <org>" або "організація: <org>"
        Pattern certOrgPattern = Pattern.compile("(?:видан[іий]+\\s+|організац[іи][яю]\\s*[:\\-]\\s*)([^,;\\n]{5,80})");
        Matcher certOrgMatcher = certOrgPattern.matcher(text);
        if (certOrgMatcher.find()) {
            skill.setCertificateOrganization(certOrgMatcher.group(1).trim());
        }

        // Решта — деталі сертифіката
        skill.setCertificateDetails(text.length() > 2000 ? text.substring(0, 2000) : text);

        return skill;
    }

    // ================================================================
    // Парсинг послужного списку (Col 5)
    // ================================================================

    /**
     * Парсить стовпець "Послужний список" (Col 5).
     * Кожен рядок — окремий запис: "Посада організації  DD.MM.YY – DD.MM.YY (тривалість)"
     *
     * Формати дат:
     * - "02.2011–02.2013"        (MM.YYYY)
     * - "30.09.93 – 14.02.95"   (DD.MM.YY)
     * - "28.08.18 - 22.11.19"   (DD.MM.YY)
     * - "10.2025 -  по теперішній час"
     * - "— 02.2011–02.2013"     (з тире на початку)
     */
    private int parseCareerRecords(String text, Teacher teacher) {
        if (text == null || text.trim().isEmpty()) return 0;
        int count = 0;

        // Розбиваємо на рядки
        String[] lines = text.split("\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 10) continue;

            try {
                CareerRecord record = parseOneCareerRecord(trimmed);
                if (record != null) {
                    record.setTeacher(teacher);
                    careerRecordRepository.save(record);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Failed to parse career record: {}", e.getMessage());
            }
        }

        return count;
    }

    /**
     * Парсить один рядок послужного списку.
     * Виділяє посаду, організацію, дати початку/кінця.
     */
    private CareerRecord parseOneCareerRecord(String text) {
        if (text == null || text.trim().length() < 10) return null;

        CareerRecord record = new CareerRecord();

        // Шукаємо дати в кінці рядка
        // Патерн 1: DD.MM.YYYY – DD.MM.YYYY або DD.MM.YY – DD.MM.YY
        // Патерн 2: MM.YYYY – MM.YYYY або MM.YYYY–MM.YYYY
        // Патерн 3: DD.MM.YYYY - по теперішній час

        String positionText = text;
        String durationSuffix = null;

        // Спочатку спробуємо знайти "(X р. XX м.)" або "(XXр. XXм)" тривалість і прибрати
        Pattern durationPattern = Pattern.compile("\\(\\s*\\d+\\s*р\\.?\\s*(?:\\d+\\s*м\\.?)?\\s*(?:\\d+\\s*д\\.?)?\\s*\\)\\s*$");
        Matcher durationMatcher = durationPattern.matcher(positionText);
        if (durationMatcher.find()) {
            durationSuffix = durationMatcher.group();
            positionText = positionText.substring(0, durationMatcher.start()).trim();
        }

        // Патерн дат: DD.MM.YYYY або DD.MM.YY або MM.YYYY
        String dateToken = "(?:\\d{2}\\.\\d{2}\\.\\d{2,4}|\\d{2}\\.\\d{4})";
        String endToken = "(?:" + dateToken + "|по\\s+теперішній\\s+час)";

        Pattern dateRangePattern = Pattern.compile(
                "\\s*[—–-]?\\s*(" + dateToken + ")\\s*[—–-]\\s*(" + endToken + ")\\s*$"
        );

        Matcher dateRangeMatcher = dateRangePattern.matcher(positionText);
        if (dateRangeMatcher.find()) {
            String startStr = dateRangeMatcher.group(1);
            String endStr = dateRangeMatcher.group(2);

            record.setStartDate(parseCareerDate(startStr));
            if (!endStr.contains("теперішній")) {
                record.setEndDate(parseCareerDate(endStr));
            }
            // Текст до дат — посада + організація
            positionText = positionText.substring(0, dateRangeMatcher.start()).trim();
            // Прибираємо кінцеве тире
            positionText = positionText.replaceAll("\\s*[—–-]\\s*$", "").trim();
        }

        if (positionText.length() < 3) return null;

        // Спробуємо розділити посаду і організацію
        // Організація зазвичай починається з великої літери після позначки посади
        // Типові маркери організацій: "Військового інституту", "Київського", тощо
        // Складно розділити надійно, тому зберігаємо повний текст як position
        // і намагаємося вичленити організацію за ключовими словами

        String organization = extractOrganization(positionText);
        String position = positionText;

        // Якщо знайшли організацію, витягуємо чисту посаду
        if (organization != null) {
            int orgIdx = positionText.indexOf(organization);
            if (orgIdx > 0) {
                position = positionText.substring(0, orgIdx).trim();
                // Прибираємо кінцеві прийменники
                position = position.replaceAll("\\s+$", "").trim();
            }
        }

        record.setPosition(position.length() > 500 ? position.substring(0, 500) : position);
        record.setOrganization(organization != null && organization.length() > 500
                ? organization.substring(0, 500) : organization);

        // Якщо є тривалість — додаємо в notes
        if (durationSuffix != null) {
            record.setNotes(durationSuffix);
        }

        return record;
    }

    /**
     * Намагається виділити організацію з тексту послужного списку.
     * Шукає знайомі патерни: "інституту", "університету", "академії" тощо.
     */
    private String extractOrganization(String text) {
        if (text == null || text.isEmpty()) return null;

        // 1. Шукаємо повну назву організації з ключовими словами
        // Включаємо всі типи лапок: " « » \u201C \u201D
        String quotes = "\"«»\u201C\u201D\u00AB\u00BB";
        Pattern orgPattern = Pattern.compile(
                "((?:[А-ЯІЇЄҐ][а-яіїєґ'ʼ]+\\s+)?" +
                "(?:Військов|Київськ|Національн|Державн|Навчальн|Науков|Центр)" +
                "[а-яіїєґ'ʼА-ЯІЇЄҐ\\s" + quotes + "()]+?" +
                "(?:інститут[уі]?|університет[уі]?|академії?|центр[уі]?|відділ[уі]?|лаборатор[іи]ї?|управлінн[яю]?)" +
                "[а-яіїєґ'ʼА-ЯІЇЄҐ\\s" + quotes + "()іменіім\\.]*)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher orgMatcher = orgPattern.matcher(text);
        if (orgMatcher.find()) {
            String org = orgMatcher.group().trim();
            // Підтягуємо абревіатуру після повної назви (напр. "... НТУУ "КПІ"")
            int endIdx = orgMatcher.end();
            if (endIdx < text.length()) {
                Pattern suffixPattern = Pattern.compile(
                        "^\\s+(?:[А-ЯІЇЄҐ]{2,6}(?:\\s+[«\"\u201C]?[А-ЯІЇЄҐ]{2,6}[»\"\u201D]?)?(?:\\s+[А-ЯІЇЄҐ]{2,6})?)");
                Matcher suffixMatcher = suffixPattern.matcher(text.substring(endIdx));
                if (suffixMatcher.find()) {
                    org += suffixMatcher.group();
                }
            }
            return org.trim();
        }

        // 2. Шукаємо абревіатуру організації в кінці тексту
        // Типові: ВІТІ, НТУУ "КПІ", ВІТІ ДУТ, ВІТІ НТУУ "КПІ", НЦЗІ
        Pattern abbrPattern = Pattern.compile(
                "\\s([А-ЯІЇЄҐ]{2,6}(?:\\s+[А-ЯІЇЄҐ]{2,6})*(?:\\s+[«\"\u201C]?[А-ЯІЇЄҐ]{2,6}[»\"\u201D]?)?)\\s*$"
        );
        Matcher abbrMatcher = abbrPattern.matcher(text);
        if (abbrMatcher.find()) {
            String abbr = abbrMatcher.group(1).trim();
            // Перевіряємо що це не просто технічне скорочення
            if (!abbr.matches("АСУ|ППО|НДР")) {
                return abbr;
            }
        }

        return null;
    }

    /**
     * Парсить дату послужного списку.
     * Формати: "DD.MM.YYYY", "DD.MM.YY", "MM.YYYY", "YYYY-MM-DD"
     */
    private LocalDate parseCareerDate(String dateStr) {
        if (dateStr == null) return null;
        dateStr = dateStr.trim();
        try {
            // ISO формат: YYYY-MM-DD
            if (dateStr.contains("-") && dateStr.length() >= 8) {
                String[] parts = dateStr.split("-");
                if (parts.length == 3 && parts[0].length() == 4) {
                    return LocalDate.of(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                }
            }
            String[] parts = dateStr.split("\\.");
            if (parts.length == 3) {
                // DD.MM.YYYY або DD.MM.YY
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (year < 100) year += (year > 50 ? 1900 : 2000);
                return LocalDate.of(year, month, day);
            } else if (parts.length == 2) {
                // MM.YYYY
                int month = Integer.parseInt(parts[0]);
                int year = Integer.parseInt(parts[1]);
                return LocalDate.of(year, month, 1);
            }
        } catch (Exception e) {
            log.trace("Cannot parse career date: {}", dateStr);
        }
        return null;
    }

    // ================================================================
    // Парсинг підвищення кваліфікації (Col 8)
    // ================================================================

    /**
     * Парсить стовпець "Підвищення кваліфікації" (Col 8).
     * Формати: нумерований список (1., 2., ...) з під-пунктами (а), б), ...).
     */
    private int parseQualifications(String text, Teacher teacher) {
        if (text == null || text.trim().isEmpty()) return 0;
        int count = 0;

        // Розбиваємо за нумерацією: 1., 2., 3., ...
        // Дозволяємо 0+ пробілів після крапки (2.Certificate — без пробілу)
        // Після крапки+пробілів має бути буква (щоб не розбити дати 24.10.2025)
        String[] entries = text.split("(?=(?:^|\\n)\\s*\\d{1,2}\\s*[.)]\\s*(?=[A-ZА-ЯІЇЄҐa-zа-яіїєґ]))");

        for (String entry : entries) {
            String cleaned = entry.trim().replaceAll("^\\d{1,2}\\s*[.)]\\s*", "").trim();
            if (cleaned.length() < 10) continue;

            // Перевіряємо чи є під-пункти (а), б), в), ...)
            if (hasSubItems(cleaned)) {
                // Перший рядок — організація, далі під-пункти
                String[] lines = cleaned.split("\\n", 2);
                String organization = lines[0].trim();
                String subItemsText = lines.length > 1 ? lines[1] : "";

                // Розбиваємо за під-пунктами: а), б), в), ...
                String[] subItems = subItemsText.split("(?=[а-яіїєґ]\\)\\s)");
                for (String sub : subItems) {
                    String subCleaned = sub.trim().replaceAll("^[а-яіїєґ]\\)\\s*", "").trim();
                    if (subCleaned.length() < 10) continue;

                    QualificationImprovement qi = parseOneQualification(subCleaned, organization);
                    if (qi != null) {
                        qi.setTeacher(teacher);
                        try {
                            qualificationRepository.save(qi);
                            count++;
                        } catch (Exception e) {
                            log.warn("Failed to save qualification: {}", e.getMessage());
                        }
                    }
                }
            } else {
                // Одиничний запис
                QualificationImprovement qi = parseOneQualification(cleaned, null);
                if (qi != null) {
                    qi.setTeacher(teacher);
                    try {
                        qualificationRepository.save(qi);
                        count++;
                    } catch (Exception e) {
                        log.warn("Failed to save qualification: {}", e.getMessage());
                    }
                }
            }
        }

        return count;
    }

    /**
     * Перевіряє, чи містить запис під-пункти виду а), б), в).
     */
    private boolean hasSubItems(String text) {
        return Pattern.compile("[а-яіїєґ]\\)\\s+(?:Вид документа|Тема|Електронний сертифікат)", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
    }

    /**
     * Парсить один запис підвищення кваліфікації.
     * Підтримує формати:
     * - Підпункти з "Тема:" (parentOrg задано)
     * - "Сертифікат за проходження курсу '...'" (Prometheus)
     * - "Certificate of Completion ORG. Course" (NATO)
     * - Організація на першому рядку, курс на наступних (збірники)
     */
    private QualificationImprovement parseOneQualification(String text, String parentOrg) {
        QualificationImprovement qi = new QualificationImprovement();
        String workText = text;

        // === Організація (крок 1: визначаємо до парсингу назви) ===
        if (parentOrg != null && !parentOrg.isEmpty()) {
            qi.setOrganization(parentOrg);
        } else {
            // Для single entries — перший рядок може бути організацією
            String firstLine = text.split("\\n")[0].trim();
            String flLower = firstLine.toLowerCase();
            boolean isOrgLine = flLower.contains("інститут") || flLower.contains("університет")
                    || flLower.contains("центр") || flLower.contains("академі")
                    || flLower.contains("школа") || flLower.contains("department")
                    || flLower.contains("academy") || flLower.contains("school")
                    || flLower.contains("university") || flLower.contains("malopolska");

            if (isOrgLine && text.contains("\n")) {
                qi.setOrganization(firstLine);
                // Решта тексту — для парсингу назви
                workText = text.substring(text.indexOf('\n') + 1).trim();
            }
        }

        // === Крок 1b: Якщо org містить назву курсу в лапках — витягти title з org ===
        String title = null;
        if (qi.getOrganization() != null && parentOrg == null) {
            Matcher orgQuoteMatcher = Pattern.compile(
                    "[\"«\\u201C\\u201E]([^\"»\\u201D\\u201F]{5,})[\"»\\u201D\\u201F]"
            ).matcher(qi.getOrganization());
            if (orgQuoteMatcher.find()) {
                title = orgQuoteMatcher.group(1).trim();
                // Очищуємо org: прибираємо лапки з назвою курсу
                String cleanedOrg = qi.getOrganization().substring(0, orgQuoteMatcher.start()).trim()
                        .replaceAll("[,;:\\s]+$", "");
                // Прибираємо "Certificate of (The)" з початку
                cleanedOrg = cleanedOrg.replaceAll("^Certificate\\s+of\\s+(?:The\\s+)?", "");
                // Прибираємо "for participating in..." / "for ..." з кінця
                cleanedOrg = cleanedOrg.replaceAll("\\s+for\\s+(?:participating|completion|taking)\\b.*$", "");
                cleanedOrg = cleanedOrg.replaceAll("[,;:\\s]+$", "").trim();
                if (!cleanedOrg.isEmpty()) {
                    qi.setOrganization(cleanedOrg);
                }
            }
        }

        // === Назва (крок 2) ===

        // 2a. "Тема:" — найвищий пріоритет
        Matcher themeMatcher = Pattern.compile("Тема:\\s*(.+?)(?:\\n|$)").matcher(workText);
        if (themeMatcher.find()) {
            title = themeMatcher.group(1).trim();
        }

        // 2b. Курс в лапках: за проходження курсу "Назва курсу"
        if (title == null) {
            Matcher courseMatcher = Pattern.compile(
                    "курсу?\\s*[\"«\\u201C\\u201E]([^\"»\\u201D\\u201F]+)[\"»\\u201D\\u201F]",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(workText);
            if (courseMatcher.find()) {
                title = courseMatcher.group(1).trim();
            }
        }

        // 2c. Підвищення кваліфікації з назвою в лапках: «STEM-ОСВІТА: ...»
        if (title == null) {
            Matcher qualNameMatcher = Pattern.compile(
                    "[«\\u201C\\u201E]([^»\\u201D\\u201F]{5,})[»\\u201D\\u201F]").matcher(workText);
            if (qualNameMatcher.find()) {
                title = qualNameMatcher.group(1).trim();
            }
        }

        // 2d. Certificate of Completion ORG. COURSE_DETAILS
        if (title == null) {
            Matcher certMatcher = Pattern.compile(
                    "Certificate\\s+of\\s+Completion\\s+(.+?)\\.\\s+(.+?)(?:\\.|$)").matcher(workText);
            if (certMatcher.find()) {
                String org = certMatcher.group(1).trim();
                String courseDetails = certMatcher.group(2).trim();
                if (qi.getOrganization() == null) {
                    qi.setOrganization(org);
                }
                title = courseDetails;
            }
        }

        // 2e. Certificate of [Type] [Org]. [Details]
        if (title == null && workText.startsWith("Certificate")) {
            Matcher certGenMatcher = Pattern.compile(
                    "Certificate\\s+of\\s+(?:The\\s+)?(.+?)(?:\\.|$)").matcher(workText);
            if (certGenMatcher.find()) {
                title = certGenMatcher.group(1).trim();
            }
        }

        // 2f. Fallback: перший рядок з очищенням
        if (title == null) {
            String fl = workText.split("\\n")[0]
                    .replaceAll("^Вид документа:\\s*", "")
                    .replaceAll("^(?:Електронний\\s+)?[Сс]ертифікат\\s*(№\\s*[^\\n,]+)?[,.]?\\s*", "")
                    .replaceAll("^(?:за\\s+проходження|за\\s+результатами)\\s+", "")
                    .trim();
            if (fl.length() > 3) {
                title = fl;
            }
        }

        // 2g. Додаткове очищення title
        if (title != null) {
            title = title
                    .replaceAll("\\s*Дата\\s+видачі.*", "")                       // "Дата видачі..."
                    .replaceAll("\\s*\\d{2}\\.\\d{2}\\.\\d{2,4}\\s*[рp]?\\.?\\s*$", "")  // дата в кінці
                    .replaceAll("\\s*\\(обсяг\\s+.*", "")                          // "(обсяг ... годин)"
                    .replaceAll("\\s*\\(загальне\\s+навантаження.*", "")            // "(загальне навантаження...)"
                    .replaceAll("\\s*\\(кількість\\s+годин.*", "")                 // "(кількість годин...)"
                    .replaceAll("\\s*https?://\\S+", "")                           // URL
                    .replaceAll("\\s*№\\s*\\d+\\.?\\s*$", "")                      // № 575.
                    .replaceAll("[.,;:\\s]+$", "")                                  // trailing punctuation
                    .trim();

            if (title.length() > 500) title = title.substring(0, 500);
        }

        qi.setTitle(title);
        if (qi.getTitle() == null || qi.getTitle().length() < 3) return null;

        // === Організація (крок 3: Certificate org — незалежно від title extraction) ===
        if (qi.getOrganization() == null) {
            Matcher certOrgMatcher = Pattern.compile(
                    "Certificate\\s+of\\s+Completion\\s+(.+?)\\.\\s+").matcher(text);
            if (certOrgMatcher.find()) {
                qi.setOrganization(certOrgMatcher.group(1).trim());
            }
        }

        // === Дати: "з DD.MM.YYYY до DD.MM.YYYY" ===
        Matcher dateRangeMatcher = Pattern.compile(
                "з\\s+(\\d{2}\\.\\d{2}\\.\\d{2,4})\\s+до\\s+(\\d{2}\\.\\d{2}\\.\\d{2,4})").matcher(text);
        if (dateRangeMatcher.find()) {
            qi.setStartDate(parseDate(dateRangeMatcher.group(1)));
            qi.setEndDate(parseDate(dateRangeMatcher.group(2)));
        }

        // Дати: "DD.MM.YY – DD.MM.YY"
        if (qi.getStartDate() == null) {
            Matcher dashMatcher = Pattern.compile(
                    "(\\d{2}\\.\\d{2}\\.\\d{2,4})\\s*[–—-]\\s*(\\d{2}\\.\\d{2}\\.\\d{2,4})").matcher(text);
            if (dashMatcher.find()) {
                qi.setStartDate(parseDate(dashMatcher.group(1)));
                qi.setEndDate(parseDate(dashMatcher.group(2)));
            }
        }

        // Дати: "DD місяця - DD місяця YYYY року" (10 жовтня - 20 листопада 2022 року)
        if (qi.getStartDate() == null) {
            Matcher textDateMatcher = Pattern.compile(
                    "(\\d{1,2})\\s+(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня)" +
                    "\\s*[–—-]?\\s*(?:по\\s+)?(\\d{1,2})\\s+(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня)" +
                    "\\s+(\\d{4})\\s*року",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            if (textDateMatcher.find()) {
                qi.setStartDate(parseTextDate(textDateMatcher.group(1), textDateMatcher.group(2), textDateMatcher.group(5)));
                qi.setEndDate(parseTextDate(textDateMatcher.group(3), textDateMatcher.group(4), textDateMatcher.group(5)));
            }
        }

        // Дата видачі (як endDate якщо не знайшли діапазон)
        if (qi.getEndDate() == null) {
            Matcher issueMatcher = Pattern.compile("Дата видачі[^\\d]*(\\d{2}\\.\\d{2}\\.\\d{2,4})").matcher(text);
            if (issueMatcher.find()) {
                qi.setEndDate(parseDate(issueMatcher.group(1)));
            }
        }

        // Одинична дата (як endDate): DD.MM.YY в кінці рядка
        if (qi.getEndDate() == null) {
            Matcher singleDateMatcher = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{2,4})\\s*$").matcher(text.split("\\n")[0]);
            if (singleDateMatcher.find()) {
                qi.setEndDate(parseDate(singleDateMatcher.group(1)));
            }
        }

        // === Години: "(120 годин" або "(загальне навантаження 6 годин" ===
        Matcher hoursMatcher = Pattern.compile("(\\d+)\\s*(?:академічних\\s+)?годин").matcher(text);
        if (hoursMatcher.find()) {
            qi.setHours(Integer.parseInt(hoursMatcher.group(1)));
        }

        // === Кредити: "4 кредити" або "0,33 кредиту" ===
        Matcher creditsMatcher = Pattern.compile("(\\d+[,.]?\\d*)\\s*кредит").matcher(text);
        if (creditsMatcher.find()) {
            qi.setCredits(Double.parseDouble(creditsMatcher.group(1).replace(",", ".")));
        }

        // === Номер сертифіката ===
        // Використовуємо [ -] замість [\s-] щоб не захоплювати через \n
        Matcher certNumMatcher = Pattern.compile("[Сс]ертифікат\\s*№\\s*([^\\s,.;\\n]+(?:[ -][^\\s,.;\\n]+)*)").matcher(text);
        if (certNumMatcher.find()) {
            qi.setCertificateNumber(certNumMatcher.group(1).trim());
        }

        // === Дата видачі сертифіката ===
        Matcher certDateMatcher = Pattern.compile("[Дд]ата\\s+видачі[^\\d]*(\\d{2}\\.\\d{2}\\.\\d{2,4})").matcher(text);
        if (certDateMatcher.find()) {
            qi.setCertificateDate(parseDate(certDateMatcher.group(1)));
        } else if (qi.getEndDate() != null && qi.getCertificateNumber() != null) {
            // Якщо є endDate та certificateNumber — дата видачі ≈ endDate
            qi.setCertificateDate(qi.getEndDate());
        }

        // === URL ===
        Matcher urlMatcher = Pattern.compile("(https?://[^\\s,;\\n]+)").matcher(text);
        if (urlMatcher.find()) {
            qi.setCertificateUrl(urlMatcher.group(1));
        }

        return qi;
    }

    /**
     * Парсить текстову дату: "10", "жовтня", "2022" → LocalDate.
     */
    private LocalDate parseTextDate(String day, String monthName, String year) {
        try {
            Map<String, Integer> months = Map.ofEntries(
                    Map.entry("січня", 1), Map.entry("лютого", 2), Map.entry("березня", 3),
                    Map.entry("квітня", 4), Map.entry("травня", 5), Map.entry("червня", 6),
                    Map.entry("липня", 7), Map.entry("серпня", 8), Map.entry("вересня", 9),
                    Map.entry("жовтня", 10), Map.entry("листопада", 11), Map.entry("грудня", 12));
            Integer month = months.get(monthName.toLowerCase());
            if (month == null) return null;
            return LocalDate.of(Integer.parseInt(year), month, Integer.parseInt(day));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Парсить дату у форматах DD.MM.YYYY, DD.MM.YY, або YYYY-MM-DD (ISO).
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        dateStr = dateStr.trim();
        try {
            // ISO формат: YYYY-MM-DD (4 цифри на початку)
            if (dateStr.contains("-") && dateStr.length() >= 8) {
                String[] parts = dateStr.split("-");
                if (parts.length == 3 && parts[0].length() == 4) {
                    int year = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    int day = Integer.parseInt(parts[2]);
                    return LocalDate.of(year, month, day);
                }
            }
            // Стандартний формат: DD.MM.YYYY або DD.MM.YY
            String[] parts = dateStr.split("[./]");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (year < 100) year += 2000;
                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    // ================================================================
    // Допоміжні методи: зіставлення імен, парсинг базової інформації
    // ================================================================

    /**
     * Зіставляє мітку імені ("Редзюк Є.В.") із збереженими викладачами.
     */
    private Teacher matchTeacherByNameLabel(String label, Map<String, Teacher> savedTeachers) {
        if (label == null || label.trim().isEmpty()) return null;
        String trimmed = label.trim();

        // Мітки імен зазвичай короткі (< 60 символів)
        if (trimmed.length() > 100) return null;

        // Витягуємо перше слово (прізвище) з конвертацією латиниці
        String[] parts = trimmed.split("[\\s,]+");
        if (parts.length == 0) return null;

        String lastName = latinToCyrillic(parts[0]
                .replaceAll("[^А-ЯІЇЄҐа-яіїєґA-Za-z'ʼ]", ""))
                .toUpperCase();

        if (lastName.isEmpty()) return null;

        // Пряме зіставлення
        if (savedTeachers.containsKey(lastName)) {
            return savedTeachers.get(lastName);
        }

        // Часткове зіставлення (якщо одне прізвище є початком іншого)
        for (Map.Entry<String, Teacher> entry : savedTeachers.entrySet()) {
            if (entry.getKey().startsWith(lastName) || lastName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private Teacher parseTeacherBasicInfo(String text) {
        Teacher teacher = new Teacher();
        if (text == null || text.isEmpty()) return teacher;

        // Формат: "ПРІЗВИЩЕ Ім'я По батькові, рік, звання, стаж років"
        String[] lines = text.split("\n");
        if (lines.length > 0) {
            String firstLine = lines[0].trim();

            // Витягуємо ПІБ (підтримка латинських та кириличних символів)
            Pattern namePattern = Pattern.compile("^([А-ЯІЇЄҐA-Z'ʼ]+)\\s+([А-ЯІЇЄҐа-яіїєґA-Za-z'ʼ]+)\\s*([А-ЯІЇЄҐа-яіїєґA-Za-z'ʼ]+)?");
            Matcher nameMatcher = namePattern.matcher(firstLine);
            if (nameMatcher.find()) {
                teacher.setLastName(latinToCyrillic(nameMatcher.group(1)));
                teacher.setFirstName(latinToCyrillic(nameMatcher.group(2)));
                if (nameMatcher.group(3) != null) {
                    teacher.setPatronymic(latinToCyrillic(nameMatcher.group(3)));
                }
            }

            // Дата народження: спершу шукаємо повну дату dd.mm.yyyy / dd-mm-yyyy / dd/mm/yyyy,
            // якщо немає — fallback на рік (тоді ставимо 1 січня цього року як placeholder).
            Pattern fullDatePattern = Pattern.compile(
                    "(\\b[0-3]?\\d)[.\\-/](\\b[01]?\\d)[.\\-/](19[5-9]\\d|20[0-2]\\d)");
            Matcher fullDateMatcher = fullDatePattern.matcher(firstLine);
            if (fullDateMatcher.find()) {
                try {
                    int day = Integer.parseInt(fullDateMatcher.group(1));
                    int month = Integer.parseInt(fullDateMatcher.group(2));
                    int year = Integer.parseInt(fullDateMatcher.group(3));
                    teacher.setDateOfBirth(java.time.LocalDate.of(year, month, day));
                } catch (Exception ignored) {
                    // некоректна дата — пропускаємо
                }
            } else {
                Pattern yearPattern = Pattern.compile("(19[5-9]\\d|20[0-2]\\d)");
                Matcher yearMatcher = yearPattern.matcher(firstLine);
                if (yearMatcher.find()) {
                    int year = Integer.parseInt(yearMatcher.group(1));
                    teacher.setDateOfBirth(java.time.LocalDate.of(year, 1, 1));
                }
            }

            // Стаж (конвертуємо роки у дату початку)
            Pattern expPattern = Pattern.compile("(\\d+)\\s*рок");
            Matcher expMatcher = expPattern.matcher(text);
            if (expMatcher.find()) {
                int years = Integer.parseInt(expMatcher.group(1));
                teacher.setExperienceStartDate(java.time.LocalDate.now().minusYears(years).withMonth(1).withDayOfMonth(1));
            }

            // Військове звання
            Pattern rankPattern = Pattern.compile(
                    "(генерал[- ]?(?:лейтенант|майор|полковник)?|полковник|підполковник|майор|капітан|старший лейтенант|лейтенант)",
                    Pattern.CASE_INSENSITIVE);
            Matcher rankMatcher = rankPattern.matcher(text);
            if (rankMatcher.find()) {
                teacher.setMilitaryRank(rankMatcher.group(1));
            }
        }

        return teacher;
    }

    private void parseEducation(Teacher teacher, String text) {
        if (text == null || text.isEmpty()) return;

        teacher.setUniversity(text.length() > 500 ? text.substring(0, 500) : text);

        // Спеціальність
        Pattern specPattern = Pattern.compile("(?:Спеціальність|спеціальність)[:\\s]*[\"«]?([^\"»\\n]+)[\"»]?");
        Matcher specMatcher = specPattern.matcher(text);
        if (specMatcher.find()) {
            teacher.setUniversitySpeciality(specMatcher.group(1).trim());
        }

        // Диплом
        Pattern diplomaPattern = Pattern.compile("(?:Диплом|диплом)[^\\n]*(?:серія|№)[^\\n]+");
        Matcher diplomaMatcher = diplomaPattern.matcher(text);
        if (diplomaMatcher.find()) {
            teacher.setUniversityDiploma(diplomaMatcher.group().trim());
        }

        // Рік закінчення ЗВО (шукаємо "закінчив у YYYY" або просто 4-значний рік)
        Matcher gradYearMatcher = Pattern.compile("(?:закінчи[вла]|випуск(?:ник)?|рік закінчення)[^\\d]*(\\d{4})").matcher(text);
        if (gradYearMatcher.find()) {
            try { teacher.setUniversityGraduationYear(Integer.parseInt(gradYearMatcher.group(1))); } catch (Exception ignored) {}
        } else {
            // Fallback: шукаємо 4-значний рік поруч з ЗВО (перший рік у рядку між 1950 та 2030)
            Matcher yearFallback = Pattern.compile("(\\d{4})\\s*(?:р\\.?)?").matcher(text);
            while (yearFallback.find()) {
                int y = Integer.parseInt(yearFallback.group(1));
                if (y >= 1950 && y <= 2030) {
                    teacher.setUniversityGraduationYear(y);
                    break;
                }
            }
        }

        // Дата диплому ЗВО: "від DD.MM.YYYY"
        Matcher diplomaDateMatcher = Pattern.compile("від\\s+(\\d{2}\\.\\d{2}\\.\\d{2,4})").matcher(text);
        if (diplomaDateMatcher.find()) {
            teacher.setUniversityDiplomaDate(parseDate(diplomaDateMatcher.group(1)));
        }
    }

    // Маппінг скорочень наук до повних назв
    private static final Map<String, String> SCIENCE_ABBREV = Map.ofEntries(
            Map.entry("т", "технічних"), Map.entry("тех", "технічних"),
            Map.entry("в", "військових"), Map.entry("війс", "військових"),
            Map.entry("ф", "фізико-математичних"), Map.entry("ф-м", "фізико-математичних"),
            Map.entry("е", "економічних"), Map.entry("ек", "економічних"),
            Map.entry("п", "педагогічних"), Map.entry("пед", "педагогічних"),
            Map.entry("ю", "юридичних"), Map.entry("юр", "юридичних"),
            Map.entry("і", "історичних"), Map.entry("іст", "історичних"),
            Map.entry("б", "біологічних"), Map.entry("біол", "біологічних"),
            Map.entry("х", "хімічних"), Map.entry("хім", "хімічних"),
            Map.entry("м", "медичних"), Map.entry("мед", "медичних"),
            Map.entry("с-г", "сільськогосподарських"),
            Map.entry("псих", "психологічних"),
            Map.entry("філол", "філологічних"), Map.entry("філос", "філософських"),
            Map.entry("пол", "політичних"), Map.entry("соц", "соціологічних"),
            Map.entry("геол", "геологічних"), Map.entry("геогр", "географічних"),
            Map.entry("арх", "архітектурних"), Map.entry("фарм", "фармацевтичних")
    );

    private String expandDegreeAbbreviation(String abbrev) {
        // к.т.н., д.в.н., канд.техн.наук, д-р.техн.наук
        String normalized = abbrev.toLowerCase().replaceAll("\\s+", "");
        boolean isDoctor = normalized.startsWith("д");
        // Витягнути літеру/скорочення галузі
        Pattern abbrPattern = Pattern.compile("[кд]\\.?([-а-яіїєґ]{1,5})\\.?н\\.?");
        Matcher m = abbrPattern.matcher(normalized);
        if (m.find()) {
            String sciCode = m.group(1).replaceAll("[.-]", "");
            String fullScience = SCIENCE_ABBREV.getOrDefault(sciCode, null);
            if (fullScience != null) {
                return (isDoctor ? "Доктор " : "Кандидат ") + fullScience + " наук";
            }
            return isDoctor ? "Доктор наук" : "Кандидат наук";
        }
        return null;
    }

    static class ScientificParsed {
        String degreeName;
        String dissertationTopic;
        String dissertationSpeciality;
        String degreeDiploma;
        LocalDate degreeDiplomaDate;
        String titleName;
        String titleAttestat;
        LocalDate titleAttestatDate;

        boolean hasDegree() {
            return degreeName != null || degreeDiploma != null || dissertationTopic != null
                    || dissertationSpeciality != null || degreeDiplomaDate != null;
        }

        boolean hasTitle() {
            return titleName != null || titleAttestat != null || titleAttestatDate != null;
        }
    }

    private void persistScientific(Teacher teacher, ScientificParsed sci) {
        if (sci == null) return;
        if (sci.hasDegree()) {
            AcademicDegree d = AcademicDegree.builder()
                    .teacher(teacher)
                    .degree(sci.degreeName)
                    .speciality(sci.dissertationSpeciality)
                    .dissertationTopic(sci.dissertationTopic)
                    .diploma(sci.degreeDiploma)
                    .diplomaDate(sci.degreeDiplomaDate)
                    .build();
            academicDegreeRepository.save(d);
        }
        if (sci.hasTitle()) {
            AcademicTitle t = AcademicTitle.builder()
                    .teacher(teacher)
                    .titleName(sci.titleName)
                    .attestat(sci.titleAttestat)
                    .attestatDate(sci.titleAttestatDate)
                    .build();
            academicTitleRepository.save(t);
        }
    }

    private ScientificParsed parseScientific(String text) {
        if (text == null || text.isEmpty()) return null;
        ScientificParsed sci = new ScientificParsed();

        log.debug("parseScientific input: {}", text.substring(0, Math.min(text.length(), 300)));

        // Ступінь — зберігаємо оригінальний текст (напр. "Кандидат технічних наук", "Доктор військових наук")
        // НЕ використовуємо CASE_INSENSITIVE — він не працює для кирилиці без UNICODE_CASE
        Pattern degreeFullPattern = Pattern.compile(
                "((?:[Дд]октор|[Кк]андидат)\\s+[а-яіїєґА-ЯІЇЄҐ'ʼ]+(?:[\\-\\s][а-яіїєґА-ЯІЇЄҐ'ʼ]+)*\\s+наук)");
        Matcher degreeFullMatcher = degreeFullPattern.matcher(text);
        if (degreeFullMatcher.find()) {
            String degree = degreeFullMatcher.group(1).trim();
            // Нормалізація: "Кандидат технічних наук" — перше слово з великої літери, решта маленькі
            int spaceIdx = degree.indexOf(' ');
            if (spaceIdx > 0) {
                degree = degree.substring(0, 1).toUpperCase() + degree.substring(1, spaceIdx).toLowerCase()
                        + degree.substring(spaceIdx).toLowerCase();
            }
            sci.degreeName = degree;
        } else {
            // Спробувати скорочення: к.т.н., д.в.н., к.е.н., д-р т.н., канд. техн. наук
            Pattern abbrPattern = Pattern.compile(
                    "([кКdДд]\\.\\s?[а-яіїєґА-ЯІЇЄҐ]{1,5}\\.\\s?[нН]\\.?)");
            Matcher abbrMatcher = abbrPattern.matcher(text);
            if (abbrMatcher.find()) {
                String expanded = expandDegreeAbbreviation(abbrMatcher.group(1));
                if (expanded != null) {
                    sci.degreeName = expanded;
                }
            } else if (text.contains("д-р") || text.contains("Д-р")) {
                sci.degreeName = "Доктор наук";
            } else if (text.contains("канд.") || text.contains("Канд.")) {
                sci.degreeName = "Кандидат наук";
            } else if (text.contains("PhD") || text.contains("Ph.D")) {
                sci.degreeName = "PhD";
            }
        }

        // Вчене звання — визначаємо за наявності Атестата, коду серії атестату, або явного "вчене звання"
        // НЕ плутати з посадою "професор кафедри" або "доцент кафедри"!
        boolean hasAttestat = text.contains("Атестат") || text.contains("атестат");
        Pattern titlePattern = Pattern.compile(
                "(?:[Вв]чене\\s+звання|[Зз]вання)\\s*[:\\-\u2013\u2014]?\\s*([Пп]рофесор|[Дд]оцент|[Сс]таршій\\s+[Дд]ослідник)");
        Matcher titleMatcher = titlePattern.matcher(text);
        if (titleMatcher.find()) {
            String title = titleMatcher.group(1).trim();
            title = title.substring(0, 1).toUpperCase() + title.substring(1).toLowerCase();
            sci.titleName = title;
        } else if (hasAttestat) {
            // Визначити звання з коду серії атестату:
            // ДЦ = Доцент, ПР = Професор, СД = Старший дослідник
            Pattern attestatCodePattern = Pattern.compile("[Аа]тестат\\s+\\d{2}([А-ЯІЇЄҐа-яіїєґ]{2})");
            Matcher attestatCodeMatcher = attestatCodePattern.matcher(text);
            if (attestatCodeMatcher.find()) {
                String code = attestatCodeMatcher.group(1).toUpperCase();
                switch (code) {
                    case "ДЦ" -> sci.titleName = "Доцент";
                    case "ПР" -> sci.titleName = "Професор";
                    case "СД" -> sci.titleName = "Старший дослідник";
                    default -> log.debug("Unknown attestat code: {}", code);
                }
            } else {
                // Fallback: шукаємо звання поруч з атестатом
                if (text.contains("старший дослідник") || text.contains("Старший дослідник")) {
                    sci.titleName = "Старший дослідник";
                }
            }
        }

        // Наукова спеціальність (напр. "20.02.14 – Озброєння і військова техніка")
        Pattern specPattern = Pattern.compile(
                "(\\d{2}\\.\\d{2}\\.\\d{2})\\s*[\u2013\u2014-]\\s*([^,;\\n]+)");
        Matcher specMatcher = specPattern.matcher(text);
        if (specMatcher.find()) {
            sci.dissertationSpeciality = specMatcher.group(1) + " – " + specMatcher.group(2).trim();
        }

        // Тема дисертації — зупиняємося на крапці, "Диплом", або кінці рядка
        Pattern topicPattern = Pattern.compile(
                "(?:[Тт]ема(?:\\s+дисертації)?|[Дд]исертаці[яї])[:\\s]+[\u00AB\u00BB\u201C\u201D\"]?([^\u00AB\u00BB\u201C\u201D\"\\n.]+(?:\\.[^\\n.]*(?=[Дд]иплом))?)");
        Matcher topicMatcher = topicPattern.matcher(text);
        if (topicMatcher.find()) {
            String topic = topicMatcher.group(1).trim();
            // Відсікаємо артефакти в кінці (крапка, пробіл, "Диплом")
            topic = topic.replaceAll("[.\\s]+$", "").trim();
            sci.dissertationTopic = topic;
        } else {
            // "на тему:" variant
            Pattern topicAlt = Pattern.compile(
                    "на\\s+тему[:\\s]+[\u00AB\u00BB\u201C\u201D\"]?([^\u00AB\u00BB\u201C\u201D\"\\n.]+)");
            Matcher topicAltMatcher = topicAlt.matcher(text);
            if (topicAltMatcher.find()) {
                sci.dissertationTopic = topicAltMatcher.group(1).trim();
            }
        }

        // Диплом ступеня — гнучкіший патерн (може бути "Диплом: ДК" або "Диплом ДК")
        // Дата може бути на наступному рядку після "від"
        Pattern diplomaPattern = Pattern.compile(
                "[Дд]иплом[:\\s]+[А-ЯA-ZІЇЄҐа-яіїєґ]{1,3}\\s*[\u2116№]?\\s*\\S+\\s+від[\\s\\n]+[\\d.]+(?:\\s*(?:року|р)\\.?)?");
        Matcher diplomaMatcher = diplomaPattern.matcher(text);
        if (diplomaMatcher.find()) {
            sci.degreeDiploma = diplomaMatcher.group().trim();
        }

        // Дата диплому ступеня: "від DD.MM.YYYY" після "Диплом ДК/ДД"
        Matcher degreeDateMatcher = Pattern.compile(
                "[Дд]иплом[:\\s]+[А-ЯA-ZІЇЄҐа-яіїєґ]{1,3}\\s*[\u2116№]?\\s*\\S+\\s+від[\\s\\n]+(\\d{2}\\.\\d{2}\\.\\d{2,4})")
                .matcher(text);
        if (degreeDateMatcher.find()) {
            sci.degreeDiplomaDate = parseDate(degreeDateMatcher.group(1));
        }

        // Атестат звання
        Pattern attestatPattern = Pattern.compile("[Аа]тестат\\s+[^\\n]+");
        Matcher attestatMatcher = attestatPattern.matcher(text);
        if (attestatMatcher.find()) {
            sci.titleAttestat = attestatMatcher.group().trim();
        }

        // Дата атестату звання: "від DD.MM.YYYY" після "Атестат"
        Matcher attestatDateMatcher = Pattern.compile(
                "[Аа]тестат[^\\n]+від[\\s\\n]+(\\d{2}\\.\\d{2}\\.\\d{2,4})")
                .matcher(text);
        if (attestatDateMatcher.find()) {
            sci.titleAttestatDate = parseDate(attestatDateMatcher.group(1));
        }

        return sci;
    }

    private void parseIdentifiers(Teacher teacher, String text) {
        if (text == null || text.isEmpty()) return;

        // ORCID (може бути як ID так і URL)
        Pattern orcidPattern = Pattern.compile("(?:ORCID|Orcid)[:\\s]*(?:ID:\\s*)?(?:https?://orcid\\.org/)?([\\d-]+X?)", Pattern.CASE_INSENSITIVE);
        Matcher orcidMatcher = orcidPattern.matcher(text);
        if (orcidMatcher.find()) {
            teacher.setOrcidId(orcidMatcher.group(1));
        }

        // Email
        Pattern emailPattern = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
        Matcher emailMatcher = emailPattern.matcher(text);
        if (emailMatcher.find()) {
            teacher.setEmail(emailMatcher.group());
        }

        // Google Scholar
        Pattern scholarPattern = Pattern.compile("(https?://scholar\\.google[^\\s,]+)");
        Matcher scholarMatcher = scholarPattern.matcher(text);
        if (scholarMatcher.find()) {
            teacher.setGoogleScholarUrl(scholarMatcher.group(1));
        }

        // Scopus (URL: ...authorId=59133020400 або ID: "Scopus ID: 59133020400")
        Pattern scopusUrlPattern = Pattern.compile("scopus\\.com[^\\s]*authorId=(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher scopusUrlMatcher = scopusUrlPattern.matcher(text);
        if (scopusUrlMatcher.find()) {
            teacher.setScopusId(scopusUrlMatcher.group(1));
        } else {
            Pattern scopusPattern = Pattern.compile("Scopus[^:]*(?:Identifier|ID)?[:\\s]*([\\d]+)", Pattern.CASE_INSENSITIVE);
            Matcher scopusMatcher = scopusPattern.matcher(text);
            if (scopusMatcher.find()) {
                teacher.setScopusId(scopusMatcher.group(1));
            }
        }

        // WoS (URL: .../record/C-1998-2019 або ID: "WoS: C-1998-2019")
        Pattern wosUrlPattern = Pattern.compile("webofscience\\.com[^\\s]*/record/([A-Z]-[\\d-]+)", Pattern.CASE_INSENSITIVE);
        Matcher wosUrlMatcher = wosUrlPattern.matcher(text);
        if (wosUrlMatcher.find()) {
            teacher.setWosId(wosUrlMatcher.group(1));
        } else {
            Pattern wosPattern = Pattern.compile("(?:Web of Science|WoS|ResearcherID)[:\\s]*([A-Z]-[\\d-]+)", Pattern.CASE_INSENSITIVE);
            Matcher wosMatcher = wosPattern.matcher(text);
            if (wosMatcher.find()) {
                teacher.setWosId(wosMatcher.group(1));
            }
        }
    }

    /**
     * Парсить стовпець "Освітні компоненти" — список дисциплін.
     * Підв'язує до існуючих дисциплін через нечіткий пошук. Нові НЕ створює.
     */
    private int parseDisciplines(String text, Teacher teacher, Department department,
                                 List<String> errors) {
        int count = 0;
        List<Discipline> allDisciplines = disciplineRepository.findAll();

        // Розбиваємо по переносу рядка, крапці з комою, або нумерації
        String[] parts = text.split("[;\\n]|\\d+\\s*[\\.\\)]\\s*");
        for (String part : parts) {
            String name = part.trim()
                    .replaceAll("^[\\-–—•,]+", "")
                    .replaceAll("[,;]+$", "")
                    .replaceAll("\\s*\\([A-ZА-ЯІЇЄҐ]\\d+\\)\\s*$", "")  // прибираємо (F3), (F6) тощо
                    .replaceAll("\\s*\\(ад'юнкт\\)\\s*$", "")           // прибираємо (ад'юнкт)
                    .trim();
            if (name.length() < 3) continue;

            // Шукаємо існуючу дисципліну (нечіткий пошук)
            Discipline disc = disciplineMatcher.findBestMatch(name, allDisciplines);

            if (disc == null) {
                errors.add("Дисципліна не знайдена: \"" + name + "\" (викладач: "
                        + teacher.getLastName() + " " + teacher.getFirstName() + ")");
                log.warn("Discipline not found for '{}', teacher: {} {}",
                        name, teacher.getLastName(), teacher.getFirstName());
                continue;
            }

            log.info("Matched discipline '{}' -> '{}' (id={})", name, disc.getName(), disc.getId());

            TeacherDiscipline td = new TeacherDiscipline();
            td.setTeacher(teacher);
            td.setDiscipline(disc);
            teacherDisciplineRepository.save(td);
            count++;
        }
        return count;
    }

    /**
     * Визначає тип публікації за ключовими словами.
     */
    private PublicationType detectPublicationType(String text) {
        String lower = text.toLowerCase();
        // Scopus/WoS/фахові -> ARTICLE
        if (lower.contains("scopus")) return PublicationType.ARTICLE;
        if (lower.contains("web of science") || lower.contains("wos")) return PublicationType.ARTICLE;
        if (lower.contains("фахов") || lower.contains("вісник") || lower.contains("збірник наукових")
                || lower.contains("наукові праці") || lower.contains("науковий збірник")) return PublicationType.ARTICLE;
        // Апробації
        if (lower.contains("тези") || lower.contains("тез доповідей")
                || lower.contains("конференц") || lower.contains("proceedings")
                || lower.contains("conference") || lower.contains("семінар")
                || lower.contains("апробац")) return PublicationType.APPROBATION;
        // Патенти
        if (lower.contains("патент на винахід")) return PublicationType.PATENT;
        if (lower.contains("деклараційн") && lower.contains("патент")
                || lower.contains("патент на корисну модель")) return PublicationType.DECLARATIVE_PATENT;
        // Авторське право
        if (lower.contains("свідоцтво") && lower.contains("авторськ")
                || lower.contains("авторське право")) return PublicationType.COPYRIGHT;
        // Методичні
        if (lower.contains("практикум") || lower.contains("конспект лекцій") || lower.contains("конспекти лекцій")
                || lower.contains("методичн") || lower.contains("метод.") || lower.contains("метод ")
                || lower.contains("робоча програма") || lower.contains("робочі програми")
                || lower.contains("рпнд") || lower.contains("силабус")
                || lower.contains("електронний курс") || lower.contains("електронні курси")
                || lower.contains("е-курс") || lower.contains("е курс") || lower.contains("екурс")
                || lower.contains("дистанційн") || lower.contains("для самостійної роботи"))
            return PublicationType.METHODICAL;
        if (lower.contains("підручник")) return PublicationType.TEXTBOOK;
        if (lower.contains("посібник")) return PublicationType.STUDY_GUIDE;
        if (lower.contains("монографія") || lower.contains("monograph")) return PublicationType.MONOGRAPH;
        // Науково-популярне
        if (lower.contains("науково-популярн") || lower.contains("науково-експертн"))
            return PublicationType.POPULAR_SCIENTIFIC;
        return PublicationType.OTHER;
    }

    /**
     * Визначає підтип методичної праці за ключовими словами.
     * Практикум=10, Навч.-метод. вказівки=3, Електронний курс=3, Конспект лекцій=2.
     */
    private MethodicalSubtype detectMethodicalSubtype(String text) {
        String lower = text.toLowerCase();
        // Практикум (10 балів)
        if (lower.contains("практикум")) return MethodicalSubtype.PRACTICUM;
        // Е-курс (3 бали) — всі варіації написання
        if (lower.contains("електронний курс") || lower.contains("електронні курси")
                || lower.contains("е-курс") || lower.contains("е курс") || lower.contains("екурс")
                || lower.contains("дистанційн") || lower.contains("on-line курс")
                || lower.contains("online курс") || lower.contains("онлайн курс")
                || lower.contains("онлайн-курс") || lower.contains("moodle"))
            return MethodicalSubtype.E_COURSE;
        // Конспект лекцій (2 бали)
        if (lower.contains("конспект лекцій") || lower.contains("конспект лекції")
                || lower.contains("конспекти лекцій") || lower.contains("курс лекцій")
                || lower.contains("курс лекції") || lower.contains("тексти лекцій"))
            return MethodicalSubtype.LECTURE_NOTES;
        // РПНД, робоча програма, силабус — окремий підтип (не рейтингується)
        if (lower.contains("робоча програма") || lower.contains("робочі програми")
                || lower.contains("рпнд") || lower.contains("силабус") || lower.contains("навчальна програма"))
            return MethodicalSubtype.WORK_PROGRAM;
        // Методичні вказівки / рекомендації (3 бали)
        if (lower.contains("методичн") && (lower.contains("вказів") || lower.contains("рекоменд")
                || lower.contains("забезпечення") || lower.contains("розробк")))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (lower.contains("метод.") && (lower.contains("вказів") || lower.contains("рекоменд")))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        if (lower.contains("для самостійної роботи") || lower.contains("для самост. роботи")
                || lower.contains("завдання для практичн") || lower.contains("завдання для лаборатор"))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        // "методичн" без конкретики — вказівки
        if (lower.contains("методичн") || lower.contains("метод."))
            return MethodicalSubtype.METHODICAL_GUIDELINES;
        // За замовчуванням — конспект лекцій
        return MethodicalSubtype.LECTURE_NOTES;
    }

    /**
     * Визначає рівень видання для апробаційних/науково-популярних публікацій (пп.12).
     * Scopus/WoS=5, Міжнародний=3, Вітчизняний=2.
     */
    private ApprobationSubtype detectApprobationSubtype(String text) {
        String lower = text.toLowerCase();
        // Scopus / Web of Science — однозначно
        if (lower.contains("scopus") || lower.contains("web of science") || lower.contains("wos")
                || lower.contains("ceur"))
            return ApprobationSubtype.SCOPUS_WOS;
        // Справді міжнародне видавництво (НЕ "Міжнародна конференція")
        if (lower.contains("ieee") || lower.contains("springer") || lower.contains("elsevier")
                || lower.contains("wiley") || lower.contains("acm ") || lower.contains("mdpi")
                || lower.contains("taylor & francis") || lower.contains("de gruyter")
                || lower.contains("cambridge university") || lower.contains("oxford university")
                || lower.contains("nato "))
            return ApprobationSubtype.INTERNATIONAL;
        // За замовчуванням — вітчизняний
        return ApprobationSubtype.DOMESTIC;
    }

    /**
     * Дедуплікація публікацій: видаляє дублікати за ключем normalize(title)+year+normalize(journal).
     * Також перевіряє існуючі публікації цього викладача в БД.
     * При дублікаті — залишає запис з більш повною інформацією (більше полів заповнено).
     */
    private List<Publication> deduplicatePublications(List<Publication> pubs, Teacher teacher) {
        // Завантажуємо існуючі публікації з БД для перевірки крос-секційних дублів
        List<Publication> existingPubs = publicationRepository.findByTeacherId(teacher.getId());
        Set<String> existingKeys = new HashSet<>();
        for (Publication ep : existingPubs) {
            existingKeys.add(makeDeduplicationKey(ep));
        }

        // Дедуплікація в рамках батчу + фільтр проти існуючих
        Map<String, Publication> uniqueMap = new LinkedHashMap<>();
        for (Publication pub : pubs) {
            String key = makeDeduplicationKey(pub);
            if (key.isBlank()) {
                // Не вдалося створити ключ — зберігаємо як є
                uniqueMap.putIfAbsent("__no_key_" + uniqueMap.size(), pub);
                continue;
            }

            // Перевіряємо чи вже є в БД
            if (existingKeys.contains(key)) {
                log.debug("Skipping duplicate (exists in DB): '{}'", pub.getTitle());
                continue;
            }

            // Перевіряємо чи вже є в батчі — залишаємо повніший запис
            if (uniqueMap.containsKey(key)) {
                Publication existing = uniqueMap.get(key);
                if (countFilledFields(pub) > countFilledFields(existing)) {
                    // Новий запис повніший — замінюємо, мержимо sourceSection
                    if (existing.getSourceSection() != null && pub.getSourceSection() != null
                            && !existing.getSourceSection().equals(pub.getSourceSection())) {
                        pub.setSourceSection(existing.getSourceSection() + "," + pub.getSourceSection());
                    }
                    uniqueMap.put(key, pub);
                } else {
                    // Мержимо sourceSection в існуючий
                    if (existing.getSourceSection() != null && pub.getSourceSection() != null
                            && !existing.getSourceSection().equals(pub.getSourceSection())
                            && !existing.getSourceSection().contains(pub.getSourceSection())) {
                        existing.setSourceSection(existing.getSourceSection() + "," + pub.getSourceSection());
                    }
                }
                log.debug("Merged duplicate: '{}' from sections {}", pub.getTitle(),
                        uniqueMap.get(key).getSourceSection());
            } else {
                uniqueMap.put(key, pub);
            }
        }

        return new ArrayList<>(uniqueMap.values());
    }

    /**
     * Створює ключ дедуплікації: normalize(title) + "|" + year + "|" + normalize(journal).
     */
    private String makeDeduplicationKey(Publication pub) {
        String title = normalizeForDedup(pub.getTitle());
        if (title.isBlank()) return "";
        String year = pub.getYear() != null ? String.valueOf(pub.getYear()) : "";
        String journal = normalizeForDedup(pub.getJournalName());
        return title + "|" + year + "|" + journal;
    }

    /**
     * Нормалізація рядка для дедуплікації: lowercase, видалення пунктуації, зайвих пробілів.
     */
    private String normalizeForDedup(String s) {
        if (s == null || s.isBlank()) return "";
        return s.toLowerCase()
                .replaceAll("[«»\"\\u201C\\u201D\\u201E\\u201F.,;:!?()\\[\\]{}]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Рахує кількість заповнених полів публікації (для вибору більш повного запису при дедуплікації).
     */
    private int countFilledFields(Publication pub) {
        int count = 0;
        if (pub.getTitle() != null && !pub.getTitle().isBlank()) count++;
        if (pub.getJournalName() != null && !pub.getJournalName().isBlank()) count++;
        if (pub.getYear() != null) count++;
        if (pub.getDoi() != null && !pub.getDoi().isBlank()) count++;
        if (pub.getUrl() != null && !pub.getUrl().isBlank()) count++;
        if (pub.getAuthors() != null && !pub.getAuthors().isBlank()) count++;
        if (pub.getVolume() != null && !pub.getVolume().isBlank()) count++;
        if (pub.getPages() != null && !pub.getPages().isBlank()) count++;
        if (pub.getIsbn() != null && !pub.getIsbn().isBlank()) count++;
        if (pub.getIssn() != null && !pub.getIssn().isBlank()) count++;
        if (pub.getArticleCategory() != null) count++;
        if (pub.getPublisher() != null && !pub.getPublisher().isBlank()) count++;
        return count;
    }

    /**
     * Визначає ArticleCategory з тексту публікації (Scopus/WoS маркери).
     * Для фахових видань (без Scopus/WoS маркера) повертає null —
     * категорія буде визначена через довідник фахових видань.
     */
    private ArticleCategory detectArticleCategoryFromText(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("scopus")) return ArticleCategory.SCOPUS;
        if (lower.contains("web of science") || lower.contains("wos")) return ArticleCategory.WOS;
        return null; // буде визначено через FakhovyiJournalService
    }

    /**
     * Верифікує публікацію типу ARTICLE через довідник фахових/Scopus видань.
     * Якщо журнал знайдений у довіднику — встановлює articleCategory.
     */
    private void verifyAndSetArticleCategory(Publication pub) {
        try {
            String journalName = pub.getJournalName();
            String issn = pub.getIssn();
            if (journalName == null && issn == null) return;

            VerificationResult vr = fakhovyiJournalService.verifyJournal(journalName, issn);

            if (vr.isScopus()) {
                pub.setArticleCategory(ArticleCategory.SCOPUS);
                log.debug("Publication '{}' verified as Scopus (matched: {})",
                        pub.getTitle(), vr.matchedScopusName());
            } else if (vr.isFakhove()) {
                JournalCategory cat = vr.category();
                if (cat == JournalCategory.CATEGORY_A) {
                    pub.setArticleCategory(ArticleCategory.CATEGORY_A);
                } else if (cat == JournalCategory.CATEGORY_B) {
                    pub.setArticleCategory(ArticleCategory.CATEGORY_B);
                }
                log.debug("Publication '{}' verified as fakhove {} (matched: {})",
                        pub.getTitle(), cat, vr.matchedFakhoveName());
            }
        } catch (Exception e) {
            log.warn("Failed to verify publication '{}': {}", pub.getTitle(), e.getMessage());
        }
    }

    // ================================================================
    // Утиліти: витягування тексту з комірок
    // ================================================================

    private String getCellText(XWPFTableCell cell) {
        if (cell == null) return "";
        StringBuilder sb = new StringBuilder();
        appendCellText(cell, sb);
        return sb.toString().trim();
    }

    /**
     * Рекурсивно витягує текст з комірки, включаючи всі рівні вкладених таблиць
     * та SDT (Structured Document Tags / Content Controls).
     * Деякі користувачі вставляють дані як таблиці або контент-контроли всередину комірки.
     */
    private void appendCellText(XWPFTableCell cell, StringBuilder sb) {
        // Ітеруємо ВСІ body elements — це покриває параграфи, таблиці та SDT
        java.util.List<org.apache.poi.xwpf.usermodel.IBodyElement> bodyElements = cell.getBodyElements();
        for (org.apache.poi.xwpf.usermodel.IBodyElement elem : bodyElements) {
            if (elem instanceof XWPFParagraph p) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(text);
                }
            } else if (elem instanceof XWPFTable nestedTable) {
                // Рекурсія: вкладені таблиці будь-якої глибини
                for (XWPFTableRow nestedRow : nestedTable.getRows()) {
                    for (XWPFTableCell nestedCell : nestedRow.getTableCells()) {
                        appendCellText(nestedCell, sb);
                    }
                }
            } else if (elem instanceof XWPFSDT sdt) {
                // Content Controls (SDT) — часто використовуються при copy-paste з інших таблиць
                String sdtText = sdt.getContent().getText();
                if (sdtText != null && !sdtText.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(sdtText);
                }
            }
        }
    }

    private String getRowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            String cellText = getCellText(cell);
            if (!cellText.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(cellText);
            }
        }
        return sb.toString().trim();
    }

    public static class ImportResult {
        public int teachersImported = 0;
        public int achievementsImported = 0;
        public int disciplinesAssigned = 0;
        public int publicationsImported = 0;
        public int qualificationsImported = 0;
        public int careerRecordsImported = 0;
        public int languageSkillsImported = 0;
        public int ppDataImported = 0;
        public List<Long> createdAchievementIds = new ArrayList<>();
        public Set<Long> importedTeacherIds = new LinkedHashSet<>();
        public List<String> errors = new ArrayList<>();
    }

    /**
     * Конвертує всі ISO-дати (yyyy-MM-dd) в тексті на dd.MM.yyyy.
     * "2020-06-30–2020-11-20" → "30.06.2020–20.11.2020"
     */
    private String convertIsoDatesToUkr(String text) {
        if (text == null) return null;
        return java.util.regex.Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})")
                .matcher(text)
                .replaceAll("$3.$2.$1");
    }

    /** @deprecated делегує в {@link ua.edu.teacherlicence.teacher.util.PositionCleaner#clean}. */
    @Deprecated
    private String cleanPosition(String position) {
        return ua.edu.teacherlicence.teacher.util.PositionCleaner.clean(position);
    }

    // ================================================================
    // Транслітерація латинських символів у кириличні
    // ================================================================

    private static final java.util.Map<Character, Character> LATIN_TO_CYR;
    static {
        LATIN_TO_CYR = new java.util.HashMap<>();
        // Uppercase
        LATIN_TO_CYR.put('A', 'А'); LATIN_TO_CYR.put('B', 'Б'); LATIN_TO_CYR.put('V', 'В');
        LATIN_TO_CYR.put('H', 'Г'); LATIN_TO_CYR.put('G', 'Г'); LATIN_TO_CYR.put('D', 'Д');
        LATIN_TO_CYR.put('E', 'Е'); LATIN_TO_CYR.put('Z', 'З'); LATIN_TO_CYR.put('Y', 'И');
        LATIN_TO_CYR.put('I', 'І'); LATIN_TO_CYR.put('K', 'К'); LATIN_TO_CYR.put('L', 'Л');
        LATIN_TO_CYR.put('M', 'М'); LATIN_TO_CYR.put('N', 'Н'); LATIN_TO_CYR.put('O', 'О');
        LATIN_TO_CYR.put('P', 'П'); LATIN_TO_CYR.put('R', 'Р'); LATIN_TO_CYR.put('S', 'С');
        LATIN_TO_CYR.put('T', 'Т'); LATIN_TO_CYR.put('U', 'У'); LATIN_TO_CYR.put('F', 'Ф');
        LATIN_TO_CYR.put('C', 'С'); LATIN_TO_CYR.put('W', 'В'); LATIN_TO_CYR.put('X', 'Х');
        // Lowercase
        LATIN_TO_CYR.put('a', 'а'); LATIN_TO_CYR.put('b', 'б'); LATIN_TO_CYR.put('v', 'в');
        LATIN_TO_CYR.put('h', 'г'); LATIN_TO_CYR.put('g', 'г'); LATIN_TO_CYR.put('d', 'д');
        LATIN_TO_CYR.put('e', 'е'); LATIN_TO_CYR.put('z', 'з'); LATIN_TO_CYR.put('y', 'и');
        LATIN_TO_CYR.put('i', 'і'); LATIN_TO_CYR.put('k', 'к'); LATIN_TO_CYR.put('l', 'л');
        LATIN_TO_CYR.put('m', 'м'); LATIN_TO_CYR.put('n', 'н'); LATIN_TO_CYR.put('o', 'о');
        LATIN_TO_CYR.put('p', 'п'); LATIN_TO_CYR.put('r', 'р'); LATIN_TO_CYR.put('s', 'с');
        LATIN_TO_CYR.put('t', 'т'); LATIN_TO_CYR.put('u', 'у'); LATIN_TO_CYR.put('f', 'ф');
        LATIN_TO_CYR.put('c', 'с'); LATIN_TO_CYR.put('w', 'в'); LATIN_TO_CYR.put('x', 'х');
    }

    private static String latinToCyrillic(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            sb.append(LATIN_TO_CYR.getOrDefault(c, c));
        }
        return sb.toString();
    }
}
