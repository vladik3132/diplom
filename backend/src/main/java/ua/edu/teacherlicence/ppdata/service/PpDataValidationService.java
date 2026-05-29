package ua.edu.teacherlicence.ppdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.fakhove.dto.VerificationResult;
import ua.edu.teacherlicence.fakhove.service.FakhovyiJournalService;
import ua.edu.teacherlicence.ppdata.dto.PpDataValidationResponse;
import ua.edu.teacherlicence.ppdata.dto.PpDataValidationResponse.PpDataValidationItem;
import ua.edu.teacherlicence.achievement.service.AchievementComposer;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Валідація даних п.38 за допомогою ШІ.
 * Перевіряє чи введені дані відповідають вимогам відповідних підпунктів.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
public class PpDataValidationService {

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
    private final PpDataValidationResultRepository validationResultRepo;
    private final TeacherRepository teacherRepository;
    private final FakhovyiJournalService fakhovyiJournalService;
    private final AchievementComposer achievementComposer;

    private final EntityManager entityManager;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    private static final int COMPLIANCE_YEARS = 5;

    // ==================== ПОВНА ВАЛІДАЦІЯ ====================

    /**
     * Валідує ВСІ ppData для викладача, зберігає результати в історію.
     */
    @Transactional
    public PpDataValidationResponse validateAll(Long teacherId) {
        List<PpDataEntry> entries = collectEntries(teacherId);

        if (entries.isEmpty()) {
            return PpDataValidationResponse.builder()
                    .sessionId("").totalChecked(0).validCount(0).warningCount(0).errorCount(0)
                    .validatedAt(LocalDateTime.now()).items(List.of()).build();
        }

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        ChatClient chatClient = chatClientBuilder.build();

        List<PpDataValidationItem> items = new ArrayList<>();

        for (PpDataEntry entry : entries) {
            PpDataValidationItem item = validateEntry(chatClient, entry);
            items.add(item);

            // Зберігаємо в БД
            PpDataValidationResult result = PpDataValidationResult.builder()
                    .sessionId(sessionId)
                    .teacher(teacher)
                    .entityType(entry.entityType)
                    .entityId(entry.entityId)
                    .ppNumber(entry.ppNumber)
                    .ppLabel(entry.ppLabel)
                    .entitySummary(entry.summary)
                    .status(item.getStatus())
                    .reasoning(item.getReasoning())
                    .build();
            validationResultRepo.save(result);
        }

        int valid = (int) items.stream().filter(i -> "OK".equals(i.getStatus())).count();
        int warning = (int) items.stream().filter(i -> "WARNING".equals(i.getStatus())).count();
        int error = (int) items.stream().filter(i -> "ERROR".equals(i.getStatus())).count();

        log.info("PpData validation for teacher={}: session={}, total={}, ok={}, warn={}, err={}",
                teacherId, sessionId, items.size(), valid, warning, error);

        // Flush щоб recompose бачив свіжі validation results
        entityManager.flush();

        // Завжди перегенеровуємо досягнення після валідації
        // (OK → повернути в досягнення, ERROR/WARNING → видалити з досягнень)
        if (teacher != null) {
            try {
                achievementComposer.recomposeForTeacher(teacher);
                log.info("Recomposed achievements after validation for {}", teacher.getLastName());
            } catch (Exception e) {
                log.warn("Failed to recompose after validation for {}: {}",
                        teacher.getLastName(), e.getMessage());
            }
        }

        return PpDataValidationResponse.builder()
                .sessionId(sessionId)
                .totalChecked(items.size())
                .validCount(valid)
                .warningCount(warning)
                .errorCount(error)
                .validatedAt(LocalDateTime.now())
                .items(items)
                .build();
    }

    // ==================== ВАЛІДАЦІЯ ОДНОГО ЗАПИСУ ====================

    /**
     * Валідує один запис ppData (для автозапуску після create/update).
     * Зберігає результат в БД.
     */
    @Transactional
    public PpDataValidationItem validateSingleEntry(Long teacherId, String entityType, Long entityId) {
        List<PpDataEntry> allEntries = collectEntries(teacherId);
        PpDataEntry target = allEntries.stream()
                .filter(e -> e.entityType.equals(entityType) && e.entityId.equals(entityId))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return PpDataValidationItem.builder()
                    .entityType(entityType).entityId(entityId)
                    .status("WARNING").reasoning("Запис не знайдено").build();
        }

        ChatClient chatClient = chatClientBuilder.build();
        PpDataValidationItem item = validateEntry(chatClient, target);

        // Зберігаємо
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        String sessionId = "auto-" + UUID.randomUUID().toString().substring(0, 6);
        PpDataValidationResult result = PpDataValidationResult.builder()
                .sessionId(sessionId)
                .teacher(teacher)
                .entityType(target.entityType)
                .entityId(target.entityId)
                .ppNumber(target.ppNumber)
                .ppLabel(target.ppLabel)
                .entitySummary(target.summary)
                .status(item.getStatus())
                .reasoning(item.getReasoning())
                .build();
        validationResultRepo.save(result);

        log.info("PpData auto-validation: teacher={}, пп.{}, entity={}, id={}, status={}",
                teacherId, target.ppNumber, entityType, entityId, item.getStatus());

        // Flush щоб recompose бачив свіжі validation results
        entityManager.flush();

        // Завжди перегенеровуємо досягнення після валідації:
        // - ERROR/WARNING → видалити з досягнень
        // - OK → повернути в досягнення (якщо раніше був видалений)
        if (teacher != null) {
            try {
                achievementComposer.recomposeForTeacher(teacher);
                log.info("Recomposed achievements after single validation for {}", teacher.getLastName());
            } catch (Exception e) {
                log.warn("Failed to recompose after single validation: {}", e.getMessage());
            }
        }

        return item;
    }

    // ==================== ІСТОРІЯ ====================

    /**
     * Список сесій валідації для викладача.
     */
    public List<Map<String, Object>> getValidationHistory(Long teacherId) {
        List<Object[]> raw = validationResultRepo.findSessionsByTeacherId(teacherId);
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (Object[] row : raw) {
            String sid = (String) row[0];
            LocalDateTime at = (LocalDateTime) row[1];
            List<PpDataValidationResult> results = validationResultRepo.findBySessionId(sid);
            int ok = (int) results.stream().filter(r -> "OK".equals(r.getStatus())).count();
            int warn = (int) results.stream().filter(r -> "WARNING".equals(r.getStatus())).count();
            int err = (int) results.stream().filter(r -> "ERROR".equals(r.getStatus())).count();
            sessions.add(Map.of(
                    "sessionId", sid,
                    "validatedAt", at.toString(),
                    "totalChecked", results.size(),
                    "validCount", ok,
                    "warningCount", warn,
                    "errorCount", err
            ));
        }
        return sessions;
    }

    /**
     * Завантажити результати конкретної сесії.
     */
    public PpDataValidationResponse getSessionResults(String sessionId) {
        List<PpDataValidationResult> results = validationResultRepo.findBySessionIdOrderByPpNumber(sessionId);
        if (results.isEmpty()) {
            return PpDataValidationResponse.builder()
                    .sessionId(sessionId).totalChecked(0).validCount(0).warningCount(0).errorCount(0)
                    .items(List.of()).build();
        }

        List<PpDataValidationItem> items = results.stream().map(r -> PpDataValidationItem.builder()
                .entityType(r.getEntityType())
                .ppNumber(r.getPpNumber())
                .ppLabel(r.getPpLabel())
                .entityId(r.getEntityId())
                .entitySummary(r.getEntitySummary())
                .status(r.getStatus())
                .reasoning(r.getReasoning())
                .build()).toList();

        int ok = (int) items.stream().filter(i -> "OK".equals(i.getStatus())).count();
        int warn = (int) items.stream().filter(i -> "WARNING".equals(i.getStatus())).count();
        int err = (int) items.stream().filter(i -> "ERROR".equals(i.getStatus())).count();

        return PpDataValidationResponse.builder()
                .sessionId(sessionId)
                .totalChecked(items.size())
                .validCount(ok)
                .warningCount(warn)
                .errorCount(err)
                .validatedAt(results.get(0).getValidatedAt())
                .items(items)
                .build();
    }

    // ==================== ОСТАННІ СТАТУСИ ====================

    /**
     * Повертає останній статус валідації для кожного запису.
     * Ключ: "entityType:entityId", значення: { status, reasoning }
     */
    public Map<String, Map<String, String>> getLatestStatuses(Long teacherId) {
        List<PpDataValidationResult> results = validationResultRepo.findByTeacherIdOrderByValidatedAtDesc(teacherId);
        Map<String, Map<String, String>> statuses = new LinkedHashMap<>();
        for (PpDataValidationResult r : results) {
            String key = r.getEntityType() + ":" + r.getEntityId();
            statuses.putIfAbsent(key, Map.of(
                    "status", r.getStatus(),
                    "reasoning", r.getReasoning() != null ? r.getReasoning() : ""
            ));
        }
        return statuses;
    }

    // ==================== ВАЛІДАЦІЯ ОДНОГО ENTRY ШІ ====================

    private PpDataValidationItem validateEntry(ChatClient chatClient, PpDataEntry entry) {
        try {
            String prompt = buildPrompt(entry);
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("AI ppData validation [пп.{}]: {}", entry.ppNumber, response);

            // Очищуємо відповідь ШІ: видаляємо контрольні символи, ескейпимо newlines
            String json = sanitizeForJson(response);
            Map<String, Object> parsed = objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() {});

            String status = parsed.getOrDefault("status", "WARNING").toString().toUpperCase();
            String reasoning = parsed.getOrDefault("reasoning", "").toString();

            if (!"OK".equals(status) && !"WARNING".equals(status) && !"ERROR".equals(status)) {
                status = "WARNING";
            }

            return PpDataValidationItem.builder()
                    .entityType(entry.entityType)
                    .ppNumber(entry.ppNumber)
                    .ppLabel(entry.ppLabel)
                    .entityId(entry.entityId)
                    .entitySummary(entry.summary)
                    .status(status)
                    .reasoning(reasoning)
                    .build();

        } catch (Exception e) {
            log.warn("AI ppData validation failed for entry [пп.{}, id={}]: {}",
                    entry.ppNumber, entry.entityId, e.getMessage());
            return PpDataValidationItem.builder()
                    .entityType(entry.entityType)
                    .ppNumber(entry.ppNumber)
                    .ppLabel(entry.ppLabel)
                    .entityId(entry.entityId)
                    .entitySummary(entry.summary)
                    .status("WARNING")
                    .reasoning("Не вдалося перевірити через ШІ: " + e.getMessage())
                    .build();
        }
    }

    // ── Промпт ──

    private String buildPrompt(PpDataEntry entry) {
        int cutoffYear = LocalDate.now().getYear() - COMPLIANCE_YEARS;
        LocalDate today = LocalDate.now();
        return """
            Ви — експерт з ліцензійних вимог для ВНЗ України (пункт 38 Ліцензійних умов).
            Перевірте чи введений запис відповідає вимогам підпункту.

            ПОТОЧНА ДАТА: %s (використовуйте для оцінки актуальності дат).

            ВИМОГИ ПІДПУНКТІВ:
            пп.6: Наукове керівництво здобувачем, який захистив дисертацію. Потрібно: ПІБ здобувача, тема, дата захисту, тип ступеня.
            пп.7: Участь в атестації наукових кадрів. Допустимі ролі: OPPONENT (офіційний опонент дисертації), REVIEWER (рецензент дисертації — саме рецензія на дисертаційне дослідження, НЕ на наукове видання!), CHAIR (голова разової спецради), COUNCIL_MEMBER (член постійної спецради). Потрібно: роль + назва ради + ПІБ здобувача та дата захисту (для OPPONENT/REVIEWER/CHAIR) АБО період членства dateFrom/dateTo (для COUNCIL_MEMBER). ВАЖЛИВО: "рецензент дисертації" — це валідна роль пп.7, її НЕ треба переводити в пп.8. Не плутайте з рецензентом наукового журналу (то пп.8).
            пп.8: Редакційно-видавнича діяльність: керівник наукової теми, головний редактор, член редколегії фахового видання, рецензент НАУКОВОГО ВИДАННЯ (журналу/збірника). Потрібно: роль, назва журналу/проекту. ВАЖЛИВО: видання має бути фаховим (є в Переліку МОН) або Scopus/WoS. В даних вказано поле "Статус видання" - якщо там написано "НЕ ЗНАЙДЕНО", статус = ERROR. ПРИМІТКА: рецензент ДИСЕРТАЦІЇ — це пп.7 (атестація), а НЕ пп.8.
            пп.9: Участь в експертній раді (МОН, НАЗЯВО, акредитаційна). Потрібно: назва ради, тип, роль.
            пп.10: Участь у міжнародному проекті (Erasmus+, Horizon, NATO, грант). Потрібно: назва проекту, програма, роль.
            пп.11: Наукове консультування установ (>=3 роки). Потрібно: організація, договір, термін.
            пп.13: Проведення >=50 аудиторних годин занять іноземною мовою. Потрібно: дисципліна, мова, кількість годин.
            пп.14: Діяльність зі здобувачами СТУДЕНТСЬКОГО рівня. Включає РІЗНІ типи:
              - OLYMPIAD (олімпіади): потрібно назва, рік, результат (призове місце). ПІБ учасника бажано, але не обов'язково.
              - SCIENTIFIC_COMPETITION (конкурси наукових робіт, МАН): потрібно назва, рік, результат.
              - COMPETITION (конкурс, хакатон тощо): потрібно назва, рік, результат.
              - SCIENTIFIC_GROUP (науковий гурток): потрібно назва, навчальний рік, кафедра, кількість учасників, номер та дата наказу. НЕ потрібен результат/призове місце!
              - SPORTS (спортивні змагання): потрібно назва, рік, результат.
              - ARTS (мистецькі конкурси): потрібно назва, рік, результат.
            пп.15: Діяльність зі здобувачами ШКІЛЬНОГО рівня (олімпіади школярів, МАН). Потрібно: назва, рік, результат.
            пп.17-18: Участь у миротворчих операціях ООН (пп.17) або навчаннях НАТО (пп.18). Потрібно: тип місії, назва, країна, дати.
            пп.19: Членство у професійному/громадському об'єднанні. Потрібно: організація, роль. ВАЖЛИВО: для членства перевіряється лише ПОТОЧНА активність, а НЕ давність дати вступу! Якщо дата вступу давніша за 5 років — це НОРМАЛЬНО, головне що членство діє. Якщо дата закінчення відсутня або пуста — це ДОБРЕ (членство безстрокове). Якщо дата закінчення вказана І вона РАНІШЕ за поточну дату %s — це ERROR (членство закінчилось!). НЕ ставте зауваження щодо давності дати вступу!
            пп.20: >=5 років досвіду практичної роботи за спеціальністю (не педагогічної). Потрібно: організація, посада, термін.

            ВАЖЛИВІ ПРАВИЛА:
            1. Чи заповнені обов'язкові поля для цього підпункту
            2. Чи дати актуальні (не старіші %d року = %d років від поточної дати %s)
            3. Чи зміст запису дійсно відповідає вимогам підпункту
            4. Чи немає очевидних помилок або нелогічностей
            5. Для пп.8: якщо в полі "Статус видання" вказано "НЕ ЗНАЙДЕНО" - це ERROR. Якщо SCOPUS або ФАХОВЕ - це добре.
            6. Для пп.14-15: хакатони (hackathon) є ПОВНОЦІННИМИ науковими конкурсами/змаганнями і відповідають вимогам підпункту. НЕ ставте зауваження щодо формату хакатону.
            7. Для пп.14-15: назва команди є достатнім ідентифікатором учасника. НЕ вимагайте обов'язково ПІБ окремого учасника, якщо вказана команда або результат.
            8. TIDE NATO Hackathon, NATO Innovation Challenge та подібні заходи під егідою НАТО/ЄС/міжнародних організацій є ВИЗНАНИМИ міжнародними конкурсами.
            10. КРИТИЧНО ВАЖЛИВО для SCIENTIFIC_GROUP: це керівництво науковим гуртком, НЕ олімпіада. Має ІНШІ обов'язкові поля: навчальний рік, кафедра, кількість учасників, номер наказу. Поле "назва змагання" і "результат" для нього НЕ потрібні! Якщо навч.рік + кафедра + к-сть учасників + наказ заповнені → ЗАВЖДИ ставте статус OK.
            11. Тип діяльності SCIENTIFIC_GROUP ПОВНІСТЮ відповідає пп.14 (НЕ пп.15!). Це законна частина пп.14 згідно Ліцензійних умов. НЕ ставте ERROR через відсутність "назви олімпіади" або "результату" для гуртків — їм це НЕ потрібно.
            9. НЕ ставте зауваження щодо дат, які є в межах поточної дати. Дата %s або раніше — це НЕ майбутня дата.

            ФОРМАТ ВІДПОВІДІ - ТІЛЬКИ JSON:
            {"status": "OK", "reasoning": "пояснення українською"}
            або {"status": "WARNING", "reasoning": "пояснення"}
            або {"status": "ERROR", "reasoning": "пояснення"}

            OK = все гаразд, запис відповідає вимогам підпункту
            WARNING = є незначні зауваження (рекомендації щодо покращення)
            ERROR = запис НЕ відповідає вимогам підпункту або є критичні помилки

            ПІДПУНКТ: пп.%d (%s)
            ДАНІ ЗАПИСУ:
            %s
            """.formatted(today, today, cutoffYear, COMPLIANCE_YEARS, today, today, entry.ppNumber, entry.ppLabel, entry.details);
    }

    // ── Збір даних ──

    record PpDataEntry(String entityType, int ppNumber, String ppLabel,
                       Long entityId, String summary, String details) {}

    private List<PpDataEntry> collectEntries(Long teacherId) {
        List<PpDataEntry> entries = new ArrayList<>();

        for (var e : scientificSupervisionRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("scientific-supervision", 6, "Наукове керівництво",
                    e.getId(), safe(e.getStudentName()) + " - " + safe(e.getTopic()),
                    formatSupervision(e)));
        }
        for (var e : attestationActivityRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("attestation-activity", 7, "Атестація",
                    e.getId(), roleLabel(e.getRole()) + ", " + safe(e.getCouncilName()),
                    formatAttestation(e)));
        }
        for (var e : editorialActivityRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("editorial-activity", 8, "Редакційна діяльність",
                    e.getId(), roleLabel(e.getRole()) + " - " + safe(e.getJournalOrProjectName()),
                    formatEditorial(e)));
        }
        for (var e : expertCouncilRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("expert-council", 9, "Експертна рада",
                    e.getId(), safe(e.getCouncilName()),
                    formatExpertCouncil(e)));
        }
        for (var e : internationalProjectRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("international-project", 10, "Міжнародний проект",
                    e.getId(), safe(e.getProjectName()),
                    formatInternationalProject(e)));
        }
        for (var e : scientificConsultingRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("scientific-consulting", 11, "Наукове консультування",
                    e.getId(), safe(e.getOrganizationName()),
                    formatConsulting(e)));
        }
        for (var e : foreignLanguageTeachingRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("foreign-language-teaching", 13, "Іноземна мова",
                    e.getId(), safe(e.getDisciplineName()) + " (" + safe(e.getLanguage()) + ")",
                    formatForeignLanguage(e)));
        }
        for (var e : olympiadGuidanceRepo.findByTeacherId(teacherId)) {
            int pp;
            String ppLabel;
            var at = e.getActivityType();
            if (at == Pp14ActivityType.SCIENTIFIC_GROUP) {
                pp = 14;
                ppLabel = "Науковий гурток";
            } else if (e.getLevel() == OlympiadLevel.SCHOOL) {
                pp = 15;
                ppLabel = "Олімпіади (шк.)";
            } else {
                pp = 14;
                ppLabel = "Олімпіади/конкурси (студ.)";
            }
            entries.add(new PpDataEntry("olympiad-guidance", pp, ppLabel,
                    e.getId(), safe(e.getOlympiadName()),
                    formatOlympiad(e)));
        }
        for (var e : militaryMissionRepo.findByTeacherId(teacherId)) {
            int pp = e.getMissionType() == MissionType.NATO_EXERCISE ? 18 : 17;
            entries.add(new PpDataEntry("military-mission", pp,
                    pp == 17 ? "Миротворча операція" : "Навчання НАТО",
                    e.getId(), safe(e.getMissionName()),
                    formatMission(e)));
        }
        for (var e : professionalAssociationRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("professional-association", 19, "Професійне об'єднання",
                    e.getId(), safe(e.getOrganizationName()),
                    formatAssociation(e)));
        }
        for (var e : practicalExperienceRepo.findByTeacherId(teacherId)) {
            entries.add(new PpDataEntry("practical-experience", 20, "Практичний досвід",
                    e.getId(), safe(e.getOrganizationName()) + " - " + safe(e.getPosition()),
                    formatExperience(e)));
        }

        return entries;
    }

    // ── Форматування записів ──

    private String formatSupervision(ScientificSupervision e) {
        return "ПІБ здобувача: " + safe(e.getStudentName())
                + "\nТема дисертації: " + safe(e.getTopic())
                + "\nДата захисту: " + dateStr(e.getDefenseDate())
                + "\nТип ступеня: " + (e.getDegreeType() != null ? e.getDegreeType().name() : "не вказано")
                + "\nN диплому: " + safe(e.getDiplomaNumber());
    }

    private String formatAttestation(AttestationActivity e) {
        StringBuilder sb = new StringBuilder()
                .append("Роль: ").append(e.getRole() != null ? e.getRole().name() : "не вказано")
                .append("\nНазва ради: ").append(safe(e.getCouncilName()))
                .append("\nПІБ здобувача: ").append(safe(e.getStudentName()))
                .append("\nДата захисту: ").append(dateStr(e.getDefenseDate()));
        if (e.getDateFrom() != null || e.getDateTo() != null) {
            sb.append("\nПеріод членства: ")
                    .append(dateStr(e.getDateFrom())).append(" — ").append(dateStr(e.getDateTo()));
        }
        return sb.toString();
    }

    private String formatEditorial(EditorialActivity e) {
        String journalInfo = safe(e.getJournalOrProjectName());
        // Перевірка видання в реєстрі фахових/Scopus
        String journalStatus = verifyJournalName(e.getJournalOrProjectName());

        return "Роль: " + (e.getRole() != null ? e.getRole().name() : "не вказано")
                + "\nЖурнал/проект: " + journalInfo
                + "\nСтатус видання: " + journalStatus
                + "\nДати: " + dateStr(e.getDateFrom()) + " - " + dateStr(e.getDateTo())
                + "\nОпис: " + safe(e.getDescription());
    }

    private String formatExpertCouncil(ExpertCouncil e) {
        return "Назва ради: " + safe(e.getCouncilName())
                + "\nТип: " + (e.getType() != null ? e.getType().name() : "не вказано")
                + "\nРоль: " + safe(e.getRole())
                + "\nДати: " + dateStr(e.getDateFrom()) + " - " + dateStr(e.getDateTo())
                + "\nN наказу: " + safe(e.getOrderNumber());
    }

    private String formatInternationalProject(InternationalProject e) {
        return "Назва проекту: " + safe(e.getProjectName())
                + "\nПрограма: " + (e.getProgram() != null ? e.getProgram().name() : "не вказано")
                + "\nРоль: " + safe(e.getRole())
                + "\nДати: " + dateStr(e.getDateFrom()) + " - " + dateStr(e.getDateTo());
    }

    private String formatConsulting(ScientificConsulting e) {
        return "Організація: " + safe(e.getOrganizationName())
                + "\nN договору: " + safe(e.getContractNumber())
                + "\nДати: " + dateStr(e.getDateFrom()) + " - " + dateStr(e.getDateTo())
                + "\nРоків: " + (e.getYearsCount() != null ? e.getYearsCount().toString() : "не вказано");
    }

    private String formatForeignLanguage(ForeignLanguageTeaching e) {
        return "Дисципліна: " + safe(e.getDisciplineName())
                + "\nМова: " + safe(e.getLanguage())
                + "\nГодин: " + (e.getHours() != null ? e.getHours().toString() : "не вказано")
                + "\nНавч. рік: " + safe(e.getAcademicYear())
                + "\nСеместр: " + (e.getSemester() != null ? e.getSemester().toString() : "не вказано");
    }

    private String formatOlympiad(OlympiadGuidance e) {
        StringBuilder sb = new StringBuilder();
        var at = e.getActivityType();
        sb.append("Тип діяльності: ").append(at != null ? at.name() : "не вказано");
        sb.append("\nРоль: ").append(e.getRole() != null ? e.getRole().name() : "не вказано");

        if (at == Pp14ActivityType.SCIENTIFIC_GROUP) {
            // Для гуртків — свій набір полів (НЕ олімпіадний)
            String groupName = safe(e.getOlympiadName());
            if ("не вказано".equals(groupName)) {
                // Генеруємо назву з опису або кафедри
                groupName = "Науковий гурток " + safe(e.getDepartmentName());
            }
            sb.append("\nНазва гуртка/товариства: ").append(groupName);
            sb.append("\nНавч. рік: ").append(safe(e.getAcademicYear()));
            sb.append("\nКафедра: ").append(safe(e.getDepartmentName()));
            sb.append("\nКількість учасників: ").append(e.getParticipantCount() != null ? e.getParticipantCount().toString() : "не вказано");
            sb.append("\nN наказу: ").append(safe(e.getOrderNumber()));
            sb.append("\nДата наказу: ").append(dateStr(e.getOrderDate()));
        } else {
            // Для олімпіад/конкурсів — олімпіадний набір полів
            sb.append("\nРівень: ").append(e.getLevel() != null ? e.getLevel().name() : "не вказано");
            sb.append("\nНазва змагання: ").append(safe(e.getOlympiadName()));
            sb.append("\nПІБ учасника: ").append(safe(e.getStudentName()));
            sb.append("\nРезультат: ").append(safe(e.getResult()));
            sb.append("\nРік: ").append(e.getYear() != null ? e.getYear().toString() : "не вказано");
        }

        sb.append("\nОпис: ").append(safe(e.getDescription()));
        return sb.toString();
    }

    private String formatMission(MilitaryMission e) {
        return "Тип місії: " + (e.getMissionType() != null ? e.getMissionType().name() : "не вказано")
                + "\nНазва: " + safe(e.getMissionName())
                + "\nКраїна: " + safe(e.getCountry())
                + "\nДати: " + dateStr(e.getDateFrom()) + " - " + dateStr(e.getDateTo());
    }

    private String formatAssociation(ProfessionalAssociation e) {
        String membershipStatus;
        if (e.getDateTo() != null && e.getDateTo().isBefore(LocalDate.now())) {
            membershipStatus = "ЗАКІНЧИЛОСЬ (дата закінчення " + e.getDateTo() + " раніше поточної дати " + LocalDate.now() + ") — це ERROR!";
        } else if (e.getDateTo() == null) {
            membershipStatus = "ДІЮЧЕ (безстрокове)";
        } else {
            membershipStatus = "ДІЮЧЕ (до " + e.getDateTo() + ")";
        }
        return "Організація: " + safe(e.getOrganizationName())
                + "\nРоль: " + safe(e.getRole())
                + "\nДата вступу: " + dateStr(e.getDateFrom())
                + "\nДата закінчення: " + dateStr(e.getDateTo())
                + "\nСтатус членства: " + membershipStatus
                + "\nN сертифіката: " + safe(e.getCertificateNumber());
    }

    private String formatExperience(PracticalExperience e) {
        return "Організація: " + safe(e.getOrganizationName())
                + "\nПосада: " + safe(e.getPosition())
                + "\nДати: " + dateStr(e.getDateFrom()) + " - " + dateStr(e.getDateTo())
                + "\nРоків: " + (e.getYearsCount() != null ? e.getYearsCount().toString() : "не вказано")
                + "\nСпеціальність: " + safe(e.getSpecialtyName());
    }

    // ── Утиліти ──

    /**
     * Перевіряє назву видання у реєстрі фахових видань та Scopus.
     * Повертає текстовий опис для включення в промпт ШІ.
     */
    private String verifyJournalName(String journalName) {
        if (journalName == null || journalName.isBlank()) {
            return "назва видання не вказана";
        }
        try {
            VerificationResult vr = fakhovyiJournalService.verifyJournal(journalName, null);
            StringBuilder sb = new StringBuilder();
            if (vr.isScopus()) {
                sb.append("SCOPUS (знайдено: ").append(vr.matchedScopusName()).append(")");
            }
            if (vr.isFakhove()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append("ФАХОВЕ видання");
                if (vr.category() != null) sb.append(" (").append(vr.category()).append(")");
                sb.append(" (знайдено: ").append(vr.matchedFakhoveName()).append(")");
            }
            if (sb.isEmpty()) {
                sb.append("НЕ ЗНАЙДЕНО в реєстрі фахових видань та Scopus");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Journal verification failed for '{}': {}", journalName, e.getMessage());
            return "не вдалося перевірити";
        }
    }

    private String safe(String s) {
        return s != null && !s.isBlank() ? s : "не вказано";
    }

    private String dateStr(Object date) {
        return date != null ? date.toString() : "?";
    }

    private String roleLabel(Enum<?> role) {
        return role != null ? role.name() : "?";
    }

    /**
     * Очищує відповідь ШІ для коректного парсингу як JSON.
     * Проблема: ШІ часто повертає JSON з неескейпованими newlines всередині string values,
     * що дає "Illegal unquoted character (CTRL-CHAR, code 10)".
     * Рішення: після extractJson замінюємо реальні newlines всередині рядків на пробіли.
     */
    private String sanitizeForJson(String text) {
        if (text == null) return "";
        // Крок 1: видаляємо контрольні символи (крім \n \r \t)
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= 32 || c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            }
        }
        String cleaned = sb.toString();

        // Крок 2: замінюємо literal newlines всередині JSON string values.
        // JSON стандарт забороняє \n всередині рядків без ескейпу.
        // Простий підхід: між { та } замінюємо newlines між лапками на пробіл.
        // Більш надійний: просто замінюємо всі \r\n та \n на пробіл всередині json.
        String jsonPart = extractJson(cleaned);
        // Всередині JSON рядка newline = помилка, тому замінюємо на пробіл
        jsonPart = jsonPart.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        // Також прибираємо табуляції
        jsonPart = jsonPart.replace("\t", " ");
        return jsonPart;
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) return "{}";
        String trimmed = text.trim();

        Matcher codeMatcher = Pattern.compile(
                "```(?:json)?\\s*([\\[{].*?[\\]}])\\s*```", Pattern.DOTALL).matcher(trimmed);
        if (codeMatcher.find()) {
            return codeMatcher.group(1);
        }

        int objStart = trimmed.indexOf('{');
        int objEnd = trimmed.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return trimmed.substring(objStart, objEnd + 1);
        }

        return "{}";
    }
}
