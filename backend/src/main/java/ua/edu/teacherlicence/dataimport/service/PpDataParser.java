package ua.edu.teacherlicence.dataimport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсить текст секцій пп.5-20 з DOCX і створює структуровані записи ppData.
 * Використовує regex для витягнення структурованих даних зі вільного тексту.
 * При невдалому парсингу зберігає запис з description = повний текст секції.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PpDataParser {

    private final ScientificSupervisionRepository scientificSupervisionRepo;
    private final AttestationActivityRepository attestationActivityRepo;
    private final EditorialActivityRepository editorialActivityRepo;
    private final ExpertCouncilRepository expertCouncilRepo;
    private final InternationalProjectRepository internationalProjectRepo;
    private final ScientificConsultingRepository scientificConsultingRepo;
    private final ForeignLanguageTeachingRepository foreignLanguageTeachingRepo;
    private final OlympiadGuidanceRepository olympiadGuidanceRepo;
    private final MilitaryMissionRepository militaryMissionRepo;
    private final ProfessionalAssociationRepository professionalAssociationRepo;
    private final PracticalExperienceRepository practicalExperienceRepo;
    private final AcademicDegreeRepository academicDegreeRepository;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),  // ISO — першим, щоб 2025-03-11 не плутати з dd.MM
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d.MM.yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yy")
    };

    /**
     * Парсить текст секції пп.X і створює відповідні ppData записи для викладача.
     *
     * @param ppNum     номер підпункту (5-20)
     * @param text      текст секції
     * @param teacher   викладач
     * @return кількість створених записів
     */
    public int parseSectionAndSave(int ppNum, String text, Teacher teacher) {
        if (text == null || text.isBlank()) return 0;

        return switch (ppNum) {
            case 5 -> parsePp5Dissertation(text, teacher);
            case 6 -> parsePp6ScientificSupervision(text, teacher);
            case 7 -> parsePp7Attestation(text, teacher);
            case 8 -> parsePp8Editorial(text, teacher);
            case 9 -> parsePp9ExpertCouncil(text, teacher);
            case 10 -> parsePp10InternationalProject(text, teacher);
            case 11 -> parsePp11ScientificConsulting(text, teacher);
            case 13 -> parsePp13ForeignLanguage(text, teacher);
            case 14 -> parsePp14Olympiad(text, teacher);
            case 15 -> parsePp15Olympiad(text, teacher, OlympiadLevel.SCHOOL);
            case 16 -> parsePp16CombatVeteran(text, teacher);
            case 17 -> parsePp17Peacekeeping(text, teacher);
            case 18 -> parsePp18NatoExercise(text, teacher);
            case 19 -> parsePp19ProfessionalAssociation(text, teacher);
            case 20 -> parsePp20PracticalExperience(text, teacher);
            default -> 0;
        };
    }

    // =====================================================================
    // пп.5 — Захист дисертації (дані зберігаються в Teacher)
    // =====================================================================

    private int parsePp5Dissertation(String text, Teacher teacher) {
        // Витягуємо дані дисертації і доповнюємо primary AcademicDegree (або створюємо новий запис).
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());
        AcademicDegree d = AcademicDegreeRanking.primary(degrees);
        boolean isNew = false;
        if (d == null) {
            d = AcademicDegree.builder().teacher(teacher).build();
            isNew = true;
        }
        boolean changed = false;

        if (d.getDissertationTopic() == null || d.getDissertationTopic().isBlank()) {
            Matcher topicMatcher = Pattern.compile(
                    "(?:тем[аи]|дисертаці[яї])\\s*[:\\-—]?\\s*[«\"\\u201C]?(.+?)(?:[»\"\\u201D]|\\.|;|$)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ).matcher(text);
            if (topicMatcher.find()) {
                String topic = topicMatcher.group(1).trim();
                if (topic.length() > 10) {
                    d.setDissertationTopic(topic);
                    changed = true;
                    log.debug("Parsed pp.5 dissertation topic for {}: '{}'",
                            teacher.getLastName(), truncate(topic, 50));
                }
            }
        }

        // Дата диплома
        if (d.getDiplomaDate() == null) {
            Matcher dateMatcher = Pattern.compile("(\\d{1,2}[./]\\d{2}[./]\\d{2,4})").matcher(text);
            if (dateMatcher.find()) {
                LocalDate date = parseDate(dateMatcher.group(1));
                if (date != null) {
                    d.setDiplomaDate(date);
                    changed = true;
                    log.debug("Parsed pp.5 diploma date for {}: {}", teacher.getLastName(), date);
                }
            }
        }

        // Номер диплома
        if (d.getDiploma() == null || d.getDiploma().isBlank()) {
            Matcher diplomaMatcher = Pattern.compile(
                    "(?:диплом|свідоцтво)\\s*[№\\u2116]?\\s*([A-ZА-ЯЄІЇҐa-zа-яєіїґ0-9\\-/]+)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ).matcher(text);
            if (diplomaMatcher.find()) {
                d.setDiploma(diplomaMatcher.group(0).trim());
                changed = true;
            }
        }

        if (changed && (isNew || d.getId() != null)) {
            academicDegreeRepository.save(d);
        }

        log.info("Parsed pp.5 dissertation data for {} from text ({} chars)",
                teacher.getLastName(), text.length());
        return 0; // Дані у academic_degrees, не окремий ppData запис
    }

    // =====================================================================
    // пп.6 — Наукове керівництво (ScientificSupervision)
    // =====================================================================

    private int parsePp6ScientificSupervision(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                ScientificSupervision ss = ScientificSupervision.builder()
                        .teacher(teacher)
                        .createdBy("import")
                        .build();

                // Шукаємо ім'я здобувача: "Прізвище І.Б." або "керівництво ... Іванов І.І."
                Matcher nameMatcher = Pattern.compile(
                        "([А-ЯЄІЇҐ][а-яєіїґ']+\\s+[А-ЯЄІЇҐ]\\.\\s*[А-ЯЄІЇҐ]\\.)",
                        Pattern.UNICODE_CASE
                ).matcher(entry);
                if (nameMatcher.find()) {
                    ss.setStudentName(nameMatcher.group(1).trim());
                }

                // Тема
                Matcher topicMatcher = Pattern.compile(
                        "(?:тем[аи]|на тему)\\s*[:\\-—]?\\s*[«\"\\u201C]?(.+?)(?:[»\"\\u201D]|\\.|;|$)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (topicMatcher.find()) {
                    ss.setTopic(topicMatcher.group(1).trim());
                }

                // Дата захисту
                Matcher dateMatcher = Pattern.compile("(\\d{1,2}[./]\\d{2}[./]\\d{2,4})").matcher(entry);
                if (dateMatcher.find()) {
                    ss.setDefenseDate(parseDate(dateMatcher.group(1)));
                }

                // Тип ступеня
                String lower = entry.toLowerCase();
                if (lower.contains("доктор") || lower.contains("дsc") || lower.contains("д.н.")) {
                    ss.setDegreeType(DegreeType.DSC);
                } else if (lower.contains("кандидат") || lower.contains("к.н.")) {
                    ss.setDegreeType(DegreeType.CANDIDATE);
                } else if (lower.contains("phd") || lower.contains("ph.d")) {
                    ss.setDegreeType(DegreeType.PHD);
                }

                // Номер диплома
                Matcher diplomaMatcher = Pattern.compile(
                        "(?:диплом|свідоцтво)\\s*[№\\u2116]?\\s*([^,;\\s]+)"
                ).matcher(entry);
                if (diplomaMatcher.find()) {
                    ss.setDiplomaNumber(diplomaMatcher.group(1).trim());
                }

                scientificSupervisionRepo.save(ss);
                count++;
                log.debug("Parsed pp.6: student='{}', degree={}", ss.getStudentName(), ss.getDegreeType());
            } catch (Exception e) {
                log.warn("Failed to parse pp.6 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.6: {} scientific supervision records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.7 — Участь в атестації (AttestationActivity)
    // =====================================================================

    private int parsePp7Attestation(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                AttestationActivity aa = AttestationActivity.builder()
                        .teacher(teacher)
                        .createdBy("import")
                        .build();

                String lower = entry.toLowerCase();
                if (lower.contains("опонент") || lower.contains("опонув")) {
                    aa.setRole(AttestationRole.OPPONENT);
                } else if (lower.contains("рецензент") || lower.contains("рецензув")) {
                    aa.setRole(AttestationRole.REVIEWER);
                } else if (lower.contains("голов")) {
                    aa.setRole(AttestationRole.CHAIR);
                } else if (lower.contains("член") && lower.contains("ради")) {
                    // "Член постійної спецради" — узагальнено для будь-якого "член ради"
                    aa.setRole(AttestationRole.COUNCIL_MEMBER);
                } else if (lower.contains("разов")) {
                    // Legacy: "разова" → найчастіше це рецензент
                    aa.setRole(AttestationRole.REVIEWER);
                }

                // Назва ради
                Matcher councilMatcher = Pattern.compile(
                        "(?:спеціалізован[аоі]|вчен[аоі]|разов[аоі])\\s+(?:вчен[аоі]\\s+)?рад[аиі]\\s+([^,;]+)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (councilMatcher.find()) {
                    aa.setCouncilName(councilMatcher.group(1).trim());
                }

                // Здобувач
                Matcher nameMatcher = Pattern.compile(
                        "([А-ЯЄІЇҐ][а-яєіїґ']+\\s+[А-ЯЄІЇҐ]\\.\\s*[А-ЯЄІЇҐ]\\.)",
                        Pattern.UNICODE_CASE
                ).matcher(entry);
                if (nameMatcher.find()) {
                    aa.setStudentName(nameMatcher.group(1).trim());
                }

                // Дата
                Matcher dateMatcher = Pattern.compile("(\\d{1,2}[./]\\d{2}[./]\\d{2,4})").matcher(entry);
                if (dateMatcher.find()) {
                    aa.setDefenseDate(parseDate(dateMatcher.group(1)));
                }

                attestationActivityRepo.save(aa);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.7 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.7: {} attestation records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.8 — Редакційна діяльність (EditorialActivity)
    // =====================================================================

    private int parsePp8Editorial(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                EditorialActivity ea = EditorialActivity.builder()
                        .teacher(teacher)
                        .description(truncate(entry, 4000))
                        .createdBy("import")
                        .build();

                String lower = entry.toLowerCase();
                if (lower.contains("головний редактор") || lower.contains("головний ред.")) {
                    ea.setRole(EditorialRole.CHIEF_EDITOR);
                } else if (lower.contains("член редакційної") || lower.contains("член редколегії")) {
                    ea.setRole(EditorialRole.BOARD_MEMBER);
                } else if (lower.contains("рецензент") || lower.contains("експерт")) {
                    ea.setRole(EditorialRole.REVIEWER);
                } else if (lower.contains("керівник") && lower.contains("тем")) {
                    ea.setRole(EditorialRole.THEME_LEADER);
                } else if (lower.contains("відповідальний виконавець")) {
                    ea.setRole(EditorialRole.RESPONSIBLE_EXECUTOR);
                }

                // Назва журналу / проекту — текст в лапках або після "журнал", "видання"
                Matcher journalMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (journalMatcher.find()) {
                    ea.setJournalOrProjectName(journalMatcher.group(1).trim());
                }

                // Дати
                parseDateRange(entry, ea);

                editorialActivityRepo.save(ea);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.8 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.8: {} editorial records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.9 — Експертна рада (ExpertCouncil)
    // =====================================================================

    private int parsePp9ExpertCouncil(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                ExpertCouncil ec = ExpertCouncil.builder()
                        .teacher(teacher)
                        .createdBy("import")
                        .build();

                String lower = entry.toLowerCase();

                // Тип ради
                if (lower.contains("мон") || lower.contains("міністерств")) {
                    ec.setType(ExpertCouncilType.MON);
                } else if (lower.contains("назяво") || lower.contains("якості")) {
                    ec.setType(ExpertCouncilType.NAZYAVO);
                } else if (lower.contains("акредитацій")) {
                    ec.setType(ExpertCouncilType.ACCREDITATION);
                } else if (lower.contains("нмр") || lower.contains("науково-методичн")) {
                    ec.setType(ExpertCouncilType.NMR);
                }

                // Назва ради
                Matcher councilMatcher = Pattern.compile(
                        "(?:експертн[аоі]|вчен[аоі])\\s+рад[аиі]\\s+([^,;]+)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (councilMatcher.find()) {
                    ec.setCouncilName(councilMatcher.group(0).trim());
                }

                // Роль
                Matcher roleMatcher = Pattern.compile(
                        "(?:член|голова|заступник|експерт|секретар)\\s*(?:ради|комісії)?",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (roleMatcher.find()) {
                    ec.setRole(roleMatcher.group(0).trim());
                }

                // Номер наказу
                Matcher orderMatcher = Pattern.compile(
                        "(?:наказ|рішення)\\s*[№\\u2116]?\\s*([^,;\\s]+)"
                ).matcher(entry);
                if (orderMatcher.find()) {
                    ec.setOrderNumber(orderMatcher.group(1).trim());
                }

                // Дати
                List<LocalDate> dates = extractAllDates(entry);
                if (dates.size() >= 2) {
                    ec.setDateFrom(dates.get(0));
                    ec.setDateTo(dates.get(1));
                } else if (dates.size() == 1) {
                    ec.setDateFrom(dates.get(0));
                }

                expertCouncilRepo.save(ec);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.9 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.9: {} expert council records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.10 — Міжнародні проекти (InternationalProject)
    // =====================================================================

    private int parsePp10InternationalProject(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                InternationalProject ip = InternationalProject.builder()
                        .teacher(teacher)
                        .description(truncate(entry, 4000))
                        .createdBy("import")
                        .build();

                String lower = entry.toLowerCase();

                // Програма
                if (lower.contains("erasmus") || lower.contains("еразмус")) {
                    ip.setProgram(InternationalProgram.ERASMUS);
                } else if (lower.contains("horizon") || lower.contains("горизонт")) {
                    ip.setProgram(InternationalProgram.HORIZON);
                } else if (lower.contains("nato") || lower.contains("нато")) {
                    ip.setProgram(InternationalProgram.NATO);
                } else if (lower.contains("грант") || lower.contains("grant")) {
                    ip.setProgram(InternationalProgram.GRANT);
                } else if (lower.contains("двосторонн") || lower.contains("bilateral")) {
                    ip.setProgram(InternationalProgram.BILATERAL);
                } else {
                    ip.setProgram(InternationalProgram.OTHER);
                }

                // Назва проекту — в лапках
                Matcher nameMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (nameMatcher.find()) {
                    ip.setProjectName(nameMatcher.group(1).trim());
                } else {
                    // Перші 100 символів як fallback
                    ip.setProjectName(truncate(entry, 200));
                }

                // Роль
                Matcher roleMatcher = Pattern.compile(
                        "(?:керівник|координатор|учасник|виконавець|researcher|participant|leader)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (roleMatcher.find()) {
                    ip.setRole(roleMatcher.group(0).trim());
                }

                // Дати
                List<LocalDate> dates = extractAllDates(entry);
                if (dates.size() >= 2) {
                    ip.setDateFrom(dates.get(0));
                    ip.setDateTo(dates.get(1));
                } else if (dates.size() == 1) {
                    ip.setDateFrom(dates.get(0));
                }

                internationalProjectRepo.save(ip);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.10 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.10: {} international project records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.11 — Наукове консультування (ScientificConsulting)
    // =====================================================================

    private int parsePp11ScientificConsulting(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                ScientificConsulting sc = ScientificConsulting.builder()
                        .teacher(teacher)
                        .createdBy("import")
                        .build();

                // Організація — в лапках або після "підприємство/організація/установа"
                Matcher orgMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (orgMatcher.find()) {
                    sc.setOrganizationName(orgMatcher.group(1).trim());
                }

                // Номер договору
                Matcher contractMatcher = Pattern.compile(
                        "(?:договір|контракт|угод[аи])\\s*[№\\u2116]?\\s*([^,;\\s]+)"
                ).matcher(entry);
                if (contractMatcher.find()) {
                    sc.setContractNumber(contractMatcher.group(1).trim());
                }

                // Дати
                List<LocalDate> dates = extractAllDates(entry);
                if (dates.size() >= 2) {
                    sc.setDateFrom(dates.get(0));
                    sc.setDateTo(dates.get(1));
                } else if (dates.size() == 1) {
                    sc.setDateFrom(dates.get(0));
                }

                // Кількість років
                Matcher yearsMatcher = Pattern.compile("(\\d+)\\s*(?:рок|років|роки)").matcher(entry);
                if (yearsMatcher.find()) {
                    sc.setYearsCount(Integer.parseInt(yearsMatcher.group(1)));
                }

                scientificConsultingRepo.save(sc);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.11 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.11: {} scientific consulting records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.13 — Викладання іноземною мовою (ForeignLanguageTeaching)
    // =====================================================================

    private int parsePp13ForeignLanguage(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                ForeignLanguageTeaching flt = ForeignLanguageTeaching.builder()
                        .teacher(teacher)
                        .createdBy("import")
                        .build();

                // Мова
                String lower = entry.toLowerCase();
                if (lower.contains("англійськ") || lower.contains("english")) {
                    flt.setLanguage("Англійська");
                } else if (lower.contains("німецьк") || lower.contains("german") || lower.contains("deutsch")) {
                    flt.setLanguage("Німецька");
                } else if (lower.contains("французьк") || lower.contains("french") || lower.contains("français")) {
                    flt.setLanguage("Французька");
                }

                // Дисципліна — в лапках
                Matcher discMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (discMatcher.find()) {
                    flt.setDisciplineName(discMatcher.group(1).trim());
                }

                // Години
                Matcher hoursMatcher = Pattern.compile("(\\d+)\\s*(?:годин|год\\.|аудиторн)").matcher(entry);
                if (hoursMatcher.find()) {
                    flt.setHours(Integer.parseInt(hoursMatcher.group(1)));
                }

                // Навчальний рік
                Matcher yearMatcher = Pattern.compile("(20\\d{2})[/\\-–—](20)?\\d{2}\\s*(?:н\\.р\\.|навч)").matcher(entry);
                if (yearMatcher.find()) {
                    flt.setAcademicYear(yearMatcher.group(0).replaceAll("\\s*(?:н\\.р\\.|навч).*", "").trim());
                }

                // Семестр
                Matcher semMatcher = Pattern.compile("(\\d)\\s*(?:семестр|сем\\.)").matcher(entry);
                if (semMatcher.find()) {
                    flt.setSemester(Integer.parseInt(semMatcher.group(1)));
                }

                foreignLanguageTeachingRepo.save(flt);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.13 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.13: {} foreign language teaching records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.14+15 — Олімпіади (OlympiadGuidance)
    // =====================================================================

    private int parsePp14Olympiad(String text, Teacher teacher) {
        return parsePpOlympiad(text, teacher, OlympiadLevel.STUDENT);
    }

    private int parsePp15Olympiad(String text, Teacher teacher, OlympiadLevel level) {
        return parsePpOlympiad(text, teacher, level);
    }

    private int parsePpOlympiad(String text, Teacher teacher, OlympiadLevel level) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                OlympiadGuidance og = OlympiadGuidance.builder()
                        .teacher(teacher)
                        .level(level)
                        .createdBy("import")
                        .build();

                String lower = entry.toLowerCase();

                // Визначити тип діяльності
                og.setActivityType(detectActivityType(lower));

                // Визначити масштаб заходу (міжнародний/всеукраїнський/регіональний)
                og.setCompetitionScope(detectCompetitionScope(lower));

                // Назва олімпіади/конкурсу/гуртка
                Matcher olympMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (olympMatcher.find()) {
                    og.setOlympiadName(olympMatcher.group(1).trim());
                }

                // Ім'я студента / учня
                Matcher nameMatcher = Pattern.compile(
                        "([А-ЯЄІЇҐ][а-яєіїґ']+\\s+[А-ЯЄІЇҐ]\\.\\s*[А-ЯЄІЇҐ]\\.)",
                        Pattern.UNICODE_CASE
                ).matcher(entry);
                if (nameMatcher.find()) {
                    og.setStudentName(nameMatcher.group(1).trim());
                }

                // Результат: "І місце", "1 місце", "призер", "переможець"
                Matcher resultMatcher = Pattern.compile(
                        "(?:(I{1,3}|\\d)\\s*місце|переможець|призер|диплом\\s*(I{1,3}|\\d)\\s*ступен)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (resultMatcher.find()) {
                    og.setResult(resultMatcher.group(0).trim());
                }

                // Рік
                Matcher yearMatcher = Pattern.compile("(20[12]\\d)").matcher(entry);
                if (yearMatcher.find()) {
                    og.setYear(Integer.parseInt(yearMatcher.group(1)));
                }

                // Навчальний рік: "2023-2024", "2023–2024"
                Matcher academicYearMatcher = Pattern.compile(
                        "(20[12]\\d)\\s*[-–]\\s*(20[12]\\d)\\s*навч"
                ).matcher(lower);
                if (academicYearMatcher.find()) {
                    og.setAcademicYear(academicYearMatcher.group(1) + "-" + academicYearMatcher.group(2));
                } else {
                    // Also try pattern: "у YYYY–YYYY навчальному році"
                    Matcher ay2 = Pattern.compile("(20[12]\\d)\\s*[-–]\\s*(20[12]\\d)").matcher(entry);
                    if (ay2.find() && og.getActivityType() == Pp14ActivityType.SCIENTIFIC_GROUP) {
                        og.setAcademicYear(ay2.group(1) + "-" + ay2.group(2));
                    }
                }

                // Кількість учасників: "14 курсантів", "20 студентів", "12 учасників"
                Matcher participantMatcher = Pattern.compile(
                        "(\\d+)\\s*(?:курсант|студент|учасник|слухач|осіб)"
                ).matcher(lower);
                if (participantMatcher.find()) {
                    og.setParticipantCount(Integer.parseInt(participantMatcher.group(1)));
                }

                // Кафедра: "кафедра|кафедри ..."
                Matcher deptMatcher = Pattern.compile(
                        "кафедр[иа]\\s+[«\"\\u201C\\u201E]?([^»\"\\u201D\\u201F.]+)[»\"\\u201D\\u201F]?",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (deptMatcher.find()) {
                    og.setDepartmentName(deptMatcher.group(1).trim());
                }

                // Номер наказу: "Наказ ... № 133", "наказ №234"
                Matcher orderMatcher = Pattern.compile(
                        "наказ\\s+.*?№\\s*(\\d+[/\\-\\w]*)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (orderMatcher.find()) {
                    og.setOrderNumber(orderMatcher.group(1).trim());
                }

                // Дата наказу: "від DD.MM.YYYY"
                Matcher orderDateMatcher = Pattern.compile(
                        "від\\s+(\\d{2})\\.(\\d{2})\\.(\\d{4})"
                ).matcher(entry);
                if (orderDateMatcher.find()) {
                    try {
                        og.setOrderDate(LocalDate.of(
                                Integer.parseInt(orderDateMatcher.group(3)),
                                Integer.parseInt(orderDateMatcher.group(2)),
                                Integer.parseInt(orderDateMatcher.group(1))));
                    } catch (Exception ignore) {
                        // bad date
                    }
                }

                // Роль
                if (lower.contains("керівник гуртка") || lower.contains("керівник наукового гуртка")) {
                    og.setRole(OlympiadRole.GROUP_LEADER);
                } else if (lower.contains("тренер") || lower.contains("coach")) {
                    og.setRole(OlympiadRole.COACH);
                } else if (lower.contains("керівник") || lower.contains("наставник") || lower.contains("supervisor")) {
                    og.setRole(OlympiadRole.SUPERVISOR);
                } else if (lower.contains("журі") || lower.contains("jury")) {
                    og.setRole(OlympiadRole.JURY);
                } else if (lower.contains("оргкомітет") || lower.contains("committee")) {
                    og.setRole(OlympiadRole.COMMITTEE);
                } else if (lower.contains("куратор")) {
                    og.setRole(OlympiadRole.CURATOR);
                } else {
                    og.setRole(OlympiadRole.SUPERVISOR); // default
                }

                // Зберегти оригінальний текст
                og.setDescription(entry.trim());

                olympiadGuidanceRepo.save(og);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.{} entry: {}", level == OlympiadLevel.STUDENT ? 14 : 15, e.getMessage());
            }
        }

        int ppNum = level == OlympiadLevel.STUDENT ? 14 : 15;
        log.info("Parsed pp.{}: {} records for {}", ppNum, count, teacher.getLastName());
        return count;
    }

    /**
     * Визначає тип діяльності за пп.14 на основі ключових слів у тексті.
     */
    private Pp14ActivityType detectActivityType(String lowerText) {
        if (lowerText.contains("гурток") || lowerText.contains("гуртка")) {
            return Pp14ActivityType.SCIENTIFIC_GROUP;
        }
        if (lowerText.contains("спорт") || lowerText.contains("збірн") || lowerText.contains("тренер")) {
            return Pp14ActivityType.SPORTS;
        }
        if (lowerText.contains("мистецьк") || lowerText.contains("творч") || lowerText.contains("виступ")) {
            return Pp14ActivityType.ARTS;
        }
        if (lowerText.contains("конкурс наукових") || lowerText.contains("конкурс студентських")
                || lowerText.contains("ман ") || lowerText.contains("малої академії")) {
            return Pp14ActivityType.SCIENTIFIC_COMPETITION;
        }
        if (lowerText.contains("олімпіад")) {
            return Pp14ActivityType.OLYMPIAD;
        }
        if (lowerText.contains("хакатон") || lowerText.contains("hackathon")
                || lowerText.contains("конкурс")) {
            return Pp14ActivityType.COMPETITION;
        }
        return Pp14ActivityType.OTHER;
    }

    /**
     * Автовизначення масштабу заходу з тексту.
     */
    private CompetitionScope detectCompetitionScope(String lowerText) {
        if (lowerText.contains("міжнарод") || lowerText.contains("international")
                || lowerText.contains("ieee") || lowerText.contains("acm ") || lowerText.contains("nato")) {
            return CompetitionScope.INTERNATIONAL;
        }
        if (lowerText.contains("всеукр") || lowerText.contains("загальнодержавн")) {
            return CompetitionScope.NATIONAL;
        }
        return null; // не вдалося визначити
    }

    // =====================================================================
    // пп.16 — Статус УБД (дані зберігаються в Teacher)
    // =====================================================================

    private int parsePp16CombatVeteran(String text, Teacher teacher) {
        // Дані УБД зазвичай парсяться при обробці основного рядка даних (col 6)
        // Тут оновлюємо якщо поля ще не заповнені
        if (!teacher.isCombatVeteranStatus() && !text.isBlank()) {
            String lower = text.toLowerCase();
            if (lower.contains("убд") || lower.contains("ветеран") || lower.contains("бойових дій")
                    || lower.contains("посвідчення")) {
                teacher.setCombatVeteranStatus(true);

                // Номер посвідчення: "№XXXXXX" або "Посвідчення УБД №XXXXX"
                Matcher docMatcher = Pattern.compile(
                        "[Пп]освідчення\\s+(?:УБД\\s*)?[№\\u2116]?\\s*([^\\s,;]+)"
                ).matcher(text);
                if (docMatcher.find() && (teacher.getCombatVeteranDoc() == null || teacher.getCombatVeteranDoc().isBlank())) {
                    teacher.setCombatVeteranDoc("Посвідчення УБД №" + docMatcher.group(1).trim());
                }

                // Дата: "від DD.MM.YYYY"
                Matcher dateMatcher = Pattern.compile("від\\s+(\\d{1,2}[./]\\d{2}[./]\\d{2,4})").matcher(text);
                if (dateMatcher.find() && teacher.getCombatVeteranDocDate() == null) {
                    teacher.setCombatVeteranDocDate(parseDate(dateMatcher.group(1)));
                }

                // Ким видано
                Matcher issuedMatcher = Pattern.compile(
                        "(?:видан[оеі]|ким\\s+видано)[:\\s]+([^,;\\n]+)"
                ).matcher(text);
                if (issuedMatcher.find() && (teacher.getCombatVeteranDocIssuedBy() == null
                        || teacher.getCombatVeteranDocIssuedBy().isBlank())) {
                    teacher.setCombatVeteranDocIssuedBy(issuedMatcher.group(1).trim());
                }

                log.info("Parsed pp.16 combat veteran data for {}", teacher.getLastName());
            }
        }
        return 0; // Дані зберігаються в Teacher
    }

    // =====================================================================
    // пп.17 — Миротворчі операції ООН (MilitaryMission)
    // =====================================================================

    private int parsePp17Peacekeeping(String text, Teacher teacher) {
        return parseMilitaryMission(text, teacher, MissionType.UN_PEACEKEEPING);
    }

    // =====================================================================
    // пп.18 — Навчання НАТО (MilitaryMission)
    // =====================================================================

    private int parsePp18NatoExercise(String text, Teacher teacher) {
        return parseMilitaryMission(text, teacher, MissionType.NATO_EXERCISE);
    }

    private int parseMilitaryMission(String text, Teacher teacher, MissionType missionType) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                MilitaryMission mm = MilitaryMission.builder()
                        .teacher(teacher)
                        .missionType(missionType)
                        .createdBy("import")
                        .build();

                // Назва місії — в лапках
                Matcher nameMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (nameMatcher.find()) {
                    mm.setMissionName(nameMatcher.group(1).trim());
                } else {
                    mm.setMissionName(truncate(entry, 200));
                }

                // Країна
                Matcher countryMatcher = Pattern.compile(
                        "(?:країна|country|в|у)\\s*[:\\-—]?\\s*([А-ЯЄІЇҐA-Z][а-яєіїґa-z]+(?:\\s+[А-ЯЄІЇҐA-Z][а-яєіїґa-z]+)?)",
                        Pattern.UNICODE_CASE
                ).matcher(entry);
                if (countryMatcher.find()) {
                    mm.setCountry(countryMatcher.group(1).trim());
                }

                // Дати
                List<LocalDate> dates = extractAllDates(entry);
                if (dates.size() >= 2) {
                    mm.setDateFrom(dates.get(0));
                    mm.setDateTo(dates.get(1));
                } else if (dates.size() == 1) {
                    mm.setDateFrom(dates.get(0));
                }

                militaryMissionRepo.save(mm);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.{} entry: {}",
                        missionType == MissionType.UN_PEACEKEEPING ? 17 : 18, e.getMessage());
            }
        }

        int ppNum = missionType == MissionType.UN_PEACEKEEPING ? 17 : 18;
        log.info("Parsed pp.{}: {} military mission records for {}", ppNum, count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.19 — Професійні об'єднання (ProfessionalAssociation)
    // =====================================================================

    private int parsePp19ProfessionalAssociation(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                ProfessionalAssociation pa = ProfessionalAssociation.builder()
                        .teacher(teacher)
                        .createdBy("import")
                        .build();

                // Організація — в лапках
                Matcher orgMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (orgMatcher.find()) {
                    pa.setOrganizationName(orgMatcher.group(1).trim());
                } else {
                    pa.setOrganizationName(truncate(entry, 200));
                }

                // Роль
                Matcher roleMatcher = Pattern.compile(
                        "(?:член|голова|заступник|президент|секретар)\\s*(?:правління|ради|організації)?",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (roleMatcher.find()) {
                    pa.setRole(roleMatcher.group(0).trim());
                }

                // Дати
                List<LocalDate> dates = extractAllDates(entry);
                if (dates.size() >= 2) {
                    pa.setDateFrom(dates.get(0));
                    pa.setDateTo(dates.get(1));
                } else if (dates.size() == 1) {
                    pa.setDateFrom(dates.get(0));
                }

                // Номер сертифіката
                Matcher certMatcher = Pattern.compile(
                        "(?:сертифікат|посвідчення|свідоцтво)\\s*[№\\u2116]?\\s*([^,;\\s]+)"
                ).matcher(entry);
                if (certMatcher.find()) {
                    pa.setCertificateNumber(certMatcher.group(1).trim());
                }

                professionalAssociationRepo.save(pa);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.19 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.19: {} professional association records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // пп.20 — Досвід практичної роботи (PracticalExperience)
    // =====================================================================

    private int parsePp20PracticalExperience(String text, Teacher teacher) {
        List<String> entries = splitByNumbering(text);
        int count = 0;

        for (String entry : entries) {
            try {
                PracticalExperience pe = PracticalExperience.builder()
                        .teacher(teacher)
                        .createdBy("import")
                        .build();

                // Організація — в лапках або після "працював"
                Matcher orgMatcher = Pattern.compile(
                        "[«\"\\u201C\\u201E]([^»\"\\u201D\\u201F]+)[»\"\\u201D\\u201F]"
                ).matcher(entry);
                if (orgMatcher.find()) {
                    pe.setOrganizationName(orgMatcher.group(1).trim());
                }

                // Посада
                Matcher posMatcher = Pattern.compile(
                        "(?:посада|на посаді|обіймав)\\s*[:\\-—]?\\s*([^,;]+)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (posMatcher.find()) {
                    pe.setPosition(posMatcher.group(1).trim());
                }

                // Дати
                List<LocalDate> dates = extractAllDates(entry);
                if (dates.size() >= 2) {
                    pe.setDateFrom(dates.get(0));
                    pe.setDateTo(dates.get(1));
                } else if (dates.size() == 1) {
                    pe.setDateFrom(dates.get(0));
                }

                // Кількість років
                Matcher yearsMatcher = Pattern.compile("(\\d+)\\s*(?:рок|років|роки)").matcher(entry);
                if (yearsMatcher.find()) {
                    pe.setYearsCount(Integer.parseInt(yearsMatcher.group(1)));
                } else if (pe.getDateFrom() != null && pe.getDateTo() != null) {
                    long years = java.time.temporal.ChronoUnit.YEARS.between(pe.getDateFrom(), pe.getDateTo());
                    pe.setYearsCount((int) years);
                }

                // Назва спеціальності
                Matcher specMatcher = Pattern.compile(
                        "(?:спеціальніст[ьі]|фах)\\s*[:\\-—]?\\s*([^,;]+)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ).matcher(entry);
                if (specMatcher.find()) {
                    pe.setSpecialtyName(specMatcher.group(1).trim());
                }

                practicalExperienceRepo.save(pe);
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse pp.20 entry: {}", e.getMessage());
            }
        }

        log.info("Parsed pp.20: {} practical experience records for {}", count, teacher.getLastName());
        return count;
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    /**
     * Розбиває текст на окремі записи за нумерацією ("1.", "2.", ...).
     * Якщо нумерації немає — повертає весь текст як один запис.
     */
    private List<String> splitByNumbering(String text) {
        List<String> entries = new ArrayList<>();
        String[] parts = text.split("(?=(?:^|\\n)\\s*\\d{1,2}\\s*[.)]\\s)");
        for (String part : parts) {
            String cleaned = part.trim()
                    .replaceAll("^\\d{1,2}\\s*[.)]\\s*", "")
                    .trim();
            if (cleaned.length() >= 10) {
                entries.add(cleaned);
            }
        }
        if (entries.isEmpty() && text.trim().length() >= 10) {
            entries.add(text.trim());
        }
        return entries;
    }

    /**
     * Парсить дату з різних форматів.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr.trim(), fmt);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        return null;
    }

    /**
     * Витягує всі дати з тексту.
     */
    private List<LocalDate> extractAllDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        // Спочатку шукаємо ISO формат (yyyy-MM-dd), потім dd.MM.yyyy / dd/MM/yyyy
        Matcher isoM = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(text);
        while (isoM.find()) {
            LocalDate d = parseDate(isoM.group(1));
            if (d != null) dates.add(d);
        }
        if (dates.isEmpty()) {
            Matcher m = Pattern.compile("(\\d{1,2}[./]\\d{2}[./]\\d{2,4})").matcher(text);
            while (m.find()) {
                LocalDate d = parseDate(m.group(1));
                if (d != null) dates.add(d);
            }
        }
        return dates;
    }

    /**
     * Парсить діапазон дат і встановлює dateFrom/dateTo для EditorialActivity.
     */
    private void parseDateRange(String text, EditorialActivity ea) {
        List<LocalDate> dates = extractAllDates(text);
        if (dates.size() >= 2) {
            ea.setDateFrom(dates.get(0));
            ea.setDateTo(dates.get(1));
        } else if (dates.size() == 1) {
            ea.setDateFrom(dates.get(0));
        }

        // Також пробуємо "з YYYY по YYYY"
        if (ea.getDateFrom() == null) {
            Matcher rangeMatcher = Pattern.compile("з\\s+(20\\d{2}).*(?:по|до)\\s+(20\\d{2})").matcher(text);
            if (rangeMatcher.find()) {
                ea.setDateFrom(LocalDate.of(Integer.parseInt(rangeMatcher.group(1)), 1, 1));
                ea.setDateTo(LocalDate.of(Integer.parseInt(rangeMatcher.group(2)), 12, 31));
            }
        }
    }

    /**
     * Обрізає рядок до заданої довжини.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
