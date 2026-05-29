package ua.edu.teacherlicence.teacher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Одноразовий сервіс нормалізації поля {@link Teacher#getCombatExperienceDates()}.
 *
 * <p>Викладачі заповнюють поле бойового досвіду у вільній формі: різні роздільники
 * ({@code -}, {@code –}, {@code /}, «по»), різні формати дат, додаткові коментарі тощо.
 * Це псує парсинг у {@link ua.edu.teacherlicence.rating.service.RatingCalculationService}.
 *
 * <p>Цей сервіс прогоняє кожен запис через AI з суворою специфікацією канонічного
 * формату {@code дд.мм.рррр – дд.мм.рррр[, дд.мм.рррр – дд.мм.рррр …]} і зберігає
 * результат. Для AI-disabled оточень або записів, які AI не зміг впевнено розпарсити,
 * застосовується regex-фолбек.
 *
 * <p>Викликається через {@code POST /api/admin/normalize-combat-dates} (ADMIN-only).
 * Безпечно запускати повторно — записи, що вже в канонічній формі, пропускаються.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CombatDatesNormalizationService {

    private final TeacherRepository teacherRepository;
    private final ObjectMapper objectMapper;

    /** AI — Optional: якщо ai.enabled=false, нормалізатор використає лише regex. */
    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    private static final DateTimeFormatter UA = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Канонічна форма: "дд.мм.рррр – дд.мм.рррр[, ...]". Перевіряємо точне співпадіння. */
    private static final Pattern CANONICAL = Pattern.compile(
            "^\\d{2}\\.\\d{2}\\.\\d{4} – \\d{2}\\.\\d{2}\\.\\d{4}"
                    + "(?:, \\d{2}\\.\\d{2}\\.\\d{4} – \\d{2}\\.\\d{2}\\.\\d{4})*$");

    /** Фолбек-regex: будь-які два діапазони dd.MM.yyyy з довільним роздільником. */
    private static final Pattern ANY_RANGE = Pattern.compile(
            "(\\d{2}\\.\\d{2}\\.\\d{4})\\s*(?:[–—\\-/]|по)\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");

    public record NormalizationItem(
            Long teacherId,
            String teacherName,
            String original,
            String normalized,
            String status,   // "OK" | "ALREADY_CANONICAL" | "FALLBACK_REGEX" | "FAILED"
            String reason
    ) {}

    public record NormalizationReport(
            int totalScanned,
            int alreadyCanonical,
            int normalizedByAi,
            int normalizedByRegex,
            int failed,
            List<NormalizationItem> items
    ) {}

    /**
     * Сканує всіх викладачів з непорожнім combatExperienceDates і нормалізує формат.
     *
     * @param dryRun якщо true — не зберігає зміни, лише повертає звіт.
     */
    @Transactional
    public NormalizationReport normalizeAll(boolean dryRun) {
        List<Teacher> teachers = teacherRepository.findAll().stream()
                .filter(t -> t.getCombatExperienceDates() != null
                        && !t.getCombatExperienceDates().isBlank())
                .toList();

        List<NormalizationItem> items = new ArrayList<>();
        int alreadyCanonical = 0, aiOk = 0, regexOk = 0, failed = 0;

        for (Teacher t : teachers) {
            String original = t.getCombatExperienceDates().trim();
            String teacherName = fullName(t);

            // 1. Вже канонічне — пропускаємо.
            if (CANONICAL.matcher(original).matches()) {
                items.add(new NormalizationItem(t.getId(), teacherName, original, original,
                        "ALREADY_CANONICAL", "Запис вже у канонічному форматі"));
                alreadyCanonical++;
                continue;
            }

            // 2. Пробуємо AI.
            String normalized = tryAi(original);
            String status, reason;
            if (normalized != null && CANONICAL.matcher(normalized).matches()) {
                status = "OK";
                reason = "AI-нормалізація";
                aiOk++;
            } else {
                // 3. Фолбек на regex (формуємо канонічний рядок з усіх знайдених діапазонів).
                String fromRegex = tryRegex(original);
                if (fromRegex != null) {
                    normalized = fromRegex;
                    status = "FALLBACK_REGEX";
                    reason = "AI не зміг — успішно по regex";
                    regexOk++;
                } else {
                    status = "FAILED";
                    reason = "Не вдалося розпізнати дати ні AI, ні regex";
                    normalized = original;
                    failed++;
                }
            }

            if (!dryRun && !"FAILED".equals(status) && !normalized.equals(original)) {
                t.setCombatExperienceDates(normalized);
                teacherRepository.save(t);
                log.info("Combat dates normalized for teacher {} ({}): '{}' → '{}'",
                        t.getId(), teacherName, original, normalized);
            }

            items.add(new NormalizationItem(t.getId(), teacherName, original, normalized, status, reason));
        }

        return new NormalizationReport(
                teachers.size(), alreadyCanonical, aiOk, regexOk, failed, items);
    }

    /**
     * Викликає AI з суворою інструкцією на нормалізацію формату.
     * Повертає null якщо AI вимкнено, помилка, або вихід не у канонічній формі.
     */
    private String tryAi(String original) {
        if (chatClientBuilder == null) return null;
        try {
            String prompt = """
                    Ти — парсер дат бойового досвіду військовослужбовця.
                    Перетвори вхідний текст на КАНОНІЧНУ форму:
                      "дд.мм.рррр – дд.мм.рррр" — один діапазон,
                      "дд.мм.рррр – дд.мм.рррр, дд.мм.рррр – дд.мм.рррр" — кілька через кому.
                    Між датами діапазону — ПРОБІЛ, EN DASH (–), ПРОБІЛ. Між діапазонами — кома + пробіл.

                    ПРАВИЛА:
                    1. Якщо у вхідному тексті є дати з днем, місяцем і роком — використовуй їх як є.
                    2. Якщо вказано тільки рік (наприклад "2018") і нема дня/місяця —
                       trapезько НЕ ВИГАДУЙ. Поверни couldNotParse=true.
                    3. Якщо вказано «ООС», «АТО», «по теперішній час», коментарі — ВІДКИДАЙ їх,
                       залиш лише точні діапазони дат.
                    4. Якщо немає жодної повної дати — couldNotParse=true.
                    5. Не змінюй порядок діапазонів — зберігай як у вхідному тексті.
                    6. Усі дати повинні бути валідними (день 01-31, місяць 01-12).

                    Поверни ТІЛЬКИ JSON без жодних коментарів чи markdown:
                      {"normalized": "...", "couldNotParse": false}
                    або
                      {"normalized": "", "couldNotParse": true}

                    ВХІД:
                    """ + original;

            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt().user(prompt).call().content();
            JsonNode root = objectMapper.readTree(extractJson(response));

            if (root.has("couldNotParse") && root.get("couldNotParse").asBoolean(false)) {
                return null;
            }
            String normalized = root.has("normalized") ? root.get("normalized").asText("").trim() : "";
            if (normalized.isBlank()) return null;
            // Додаткова перевірка валідності кожної дати:
            return validateAndCleanup(normalized);
        } catch (Exception e) {
            log.warn("AI normalization failed for '{}': {}", original, e.getMessage());
            return null;
        }
    }

    /**
     * Regex-фолбек: знаходить ВСІ діапазони dd.MM.yyyy з будь-яким роздільником
     * (—, –, -, /, "по") і збирає у канонічну форму.
     * null якщо жодного валідного діапазону не знайдено.
     */
    private String tryRegex(String original) {
        Matcher m = ANY_RANGE.matcher(original);
        List<String> ranges = new ArrayList<>();
        while (m.find()) {
            try {
                LocalDate a = LocalDate.parse(m.group(1), UA);
                LocalDate b = LocalDate.parse(m.group(2), UA);
                // Якщо переплутаний порядок — вирівнюємо.
                if (a.isAfter(b)) {
                    LocalDate tmp = a; a = b; b = tmp;
                }
                ranges.add(a.format(UA) + " – " + b.format(UA));
            } catch (Exception ignored) {}
        }
        if (ranges.isEmpty()) return null;
        return String.join(", ", ranges);
    }

    /**
     * Розбирає рядок-кандидат (з AI) і повертає чистий канонічний рядок,
     * якщо всі дати валідні. Інакше null.
     */
    private String validateAndCleanup(String candidate) {
        // Розпарсимо як набір діапазонів і збиремо назад у канон.
        Matcher m = ANY_RANGE.matcher(candidate);
        List<String> ranges = new ArrayList<>();
        while (m.find()) {
            try {
                LocalDate a = LocalDate.parse(m.group(1), UA);
                LocalDate b = LocalDate.parse(m.group(2), UA);
                if (a.isAfter(b)) { LocalDate tmp = a; a = b; b = tmp; }
                ranges.add(a.format(UA) + " – " + b.format(UA));
            } catch (Exception e) {
                return null;
            }
        }
        return ranges.isEmpty() ? null : String.join(", ", ranges);
    }

    /** Витягуємо перший JSON-об'єкт з тексту відповіді AI. */
    private String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static String fullName(Teacher t) {
        StringBuilder sb = new StringBuilder();
        if (t.getLastName() != null) sb.append(t.getLastName());
        if (t.getFirstName() != null) sb.append(' ').append(t.getFirstName());
        if (t.getPatronymic() != null) sb.append(' ').append(t.getPatronymic());
        return sb.toString().trim();
    }
}
