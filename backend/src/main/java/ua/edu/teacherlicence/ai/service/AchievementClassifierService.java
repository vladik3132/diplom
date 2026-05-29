package ua.edu.teacherlicence.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.ai.dto.ClassificationResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
public class AchievementClassifierService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String CLASSIFICATION_PROMPT = """
            Ви — експерт з ліцензійних вимог для ВНЗ України.
            Проаналізуйте опис досягнення та визначте, до якого підпункту
            пункту 38 воно належить (1-20). Поверніть ТІЛЬКИ JSON:
            {"type": число, "confidence": 0.0-1.0, "reasoning": "коротке пояснення"}

            Підпункти п.38: 1-публікації(≥5), 2-патенти, 3-підручники, 4-методичні(≥3),
            5-дисертація, 6-керівництво, 7-атестація, 8-редколегія, 9-експертна рада,
            10-міжнародні, 11-консультування(≥3р), 12-апробаційні(≥5), 13-іноземна мова(≥50год),
            14-олімпіади студ, 15-олімпіади школ, 16-УБД, 17-ООН, 18-НАТО, 19-об'єднання, 20-досвід(≥5р)

            Опис:
            """;

    /**
     * Будує детальний промпт для перевірки одного досягнення з динамічним cutoff-роком.
     */
    private String buildFulfillmentPrompt() {
        int cutoffYear = java.time.LocalDate.now().getYear() - 5;
        return """
            Ви — експерт з ліцензійних вимог для ВНЗ України (пункт 38).
            Проаналізуйте опис досягнення та визначте чи воно відповідає вказаному підпункту.

            ВАЖЛИВО: досягнення мають бути НЕ СТАРШІ за 5 років (з %d року і новіші).

            КРИТЕРІЇ ПО ПІДПУНКТАХ:
            пп.1: ≥5 наукових публікацій у Scopus, WoS або фахових виданнях. Тези конференцій НЕ рахуються.
            пп.2: 1 патент на винахід АБО ≥5 деклараційних патентів/свідоцтв авторського права. Рахуйте КОЖНЕ свідоцтво окремо.
            пп.3: Виданий підручник (≥5 авт. арк.) або навчальний посібник (≥5 авт. арк.) або монографія.
              ЗАРАХОВУЄТЬСЯ: підручник, навчальний посібник, монографія.
              НЕ ЗАРАХОВУЄТЬСЯ (це пп.4!): практикум, конспект лекцій, методичний посібник, РПНД, робоча програма, посібник для самостійної роботи.
            пп.4: ≥3 навчально-методичних праці.
              ЗАРАХОВУЄТЬСЯ: практикум, конспект лекцій, методичні вказівки/рекомендації, РПНД, робочі програми, електронні курси, посібник для самостійної роботи.
              НЕ ЗАРАХОВУЄТЬСЯ (це пп.3!): підручник, навчальний посібник, монографія.
            пп.5: Захист дисертації (PhD, кандидатська, докторська).
            пп.6: Наукове керівництво захищеним здобувачем.
            пп.7: Участь в атестації наукових кадрів. ЗАРАХОВУЄТЬСЯ:
              - офіційний опонент дисертації (OPPONENT);
              - рецензент дисертації (REVIEWER) — саме рецензія на дисертаційне дослідження, НЕ на наукове видання;
              - голова разової спеціалізованої вченої ради (CHAIR);
              - член постійної спеціалізованої вченої ради (COUNCIL_MEMBER).
              ВАЖЛИВО: "рецензент дисертації" — це ПП.7, а НЕ пп.8. Не плутайте з рецензентом наукового журналу.
            пп.8: Редакційно-видавнича діяльність / керівник наукової теми.
              ЗАРАХОВУЄТЬСЯ: керівник наукової теми, головний редактор / член редколегії фахового видання,
              рецензент НАУКОВОГО ВИДАННЯ (журналу/збірника). Рецензент дисертації сюди НЕ належить — це пп.7.
            пп.9: Член експертної ради МОН, НАЗЯВО або акредитаційної комісії.
            пп.10: Участь у міжнародному проекті (Erasmus, Horizon, НАТО, грант).
            пп.11: ≥3 роки наукового консультування за договором.
            пп.12: ≥5 апробаційних або науково-популярних публікацій (тези конференцій).
            пп.13: ≥50 аудиторних годин занять іноземною мовою.
            пп.14: Керівництво студентом-призером олімпіади/конкурсу або участь в оргкомітеті/журі.
            пп.15: Керівництво школярем-призером олімпіади/МАН.
            пп.16: Статус учасника бойових дій (УБД).
            пп.17: Участь у миротворчій операції ООН.
            пп.18: Участь у навчаннях НАТО.
            пп.19: Участь у професійному/громадському об'єднанні.
            пп.20: ≥5 років практичного досвіду за спеціальністю (крім педагогічного).

            ФОРМАТ ВІДПОВІДІ — ТІЛЬКИ JSON:
            {"type": підпункт, "fulfilled": true/false, "currentCount": скільки_є, "requiredCount": скільки_треба, "matchesType": true/false, "reasoning": "поелементний аналіз українською: 1) назва — зараховано/ні (причина)"}

            matchesType=false якщо зміст НЕ відповідає вказаному підпункту.
            Рахуйте КОЖЕН окремий елемент: '2 свідоцтва' = currentCount:2, NOT 4!
            Числові вирази типу '2 авторських свідоцтва' означають рівно 2, а не 4.
            Нумеровані списки: рахуйте кожен пункт окремо.

            """.formatted(cutoffYear);
    }

    /** Результат перевірки виконання */
    public record FulfillmentItem(int idx, int type, boolean fulfilled,
                                  int currentCount, int requiredCount,
                                  boolean matchesType, String reasoning) {}

    /**
     * Класифікує текстовий опис досягнення за підпунктами п.38
     */
    public ClassificationResult classify(String achievementDescription) {
        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .user(CLASSIFICATION_PROMPT + achievementDescription)
                    .call()
                    .content();

            String json = extractJson(response);
            return objectMapper.readValue(json, ClassificationResult.class);
        } catch (Exception e) {
            log.error("Classification error: {}", e.getMessage());
            return new ClassificationResult(0, 0.0, "Помилка класифікації: " + e.getMessage());
        }
    }

    /**
     * Перевіряє виконання для кожного досягнення ОКРЕМО (надійніший ніж батч).
     */
    public List<FulfillmentItem> checkFulfillment(List<String> descriptions) {
        List<FulfillmentItem> results = new ArrayList<>();
        ChatClient chatClient = chatClientBuilder.build();

        for (int i = 0; i < descriptions.size(); i++) {
            try {
                String desc = descriptions.get(i);
                if (desc.length() > 1500) {
                    desc = desc.substring(0, 1500);
                }

                String response = chatClient.prompt()
                        .user(buildFulfillmentPrompt() + "Досягнення:\n" + desc)
                        .call()
                        .content();

                log.debug("AI fulfillment [{}] response: {}", i, response);

                String jsonStr = extractJson(response);
                Map<String, Object> parsed = objectMapper.readValue(
                        jsonStr, new TypeReference<Map<String, Object>>() {});

                int type = parsed.get("type") != null ? ((Number) parsed.get("type")).intValue() : 0;
                boolean fulfilled = Boolean.TRUE.equals(parsed.get("fulfilled"));
                int currentCount = parsed.get("currentCount") != null ? ((Number) parsed.get("currentCount")).intValue() : 0;
                int requiredCount = parsed.get("requiredCount") != null ? ((Number) parsed.get("requiredCount")).intValue() : 1;
                boolean matchesType = parsed.get("matchesType") == null || Boolean.TRUE.equals(parsed.get("matchesType"));
                String reasoning = parsed.get("reasoning") != null ? parsed.get("reasoning").toString() : "";

                results.add(new FulfillmentItem(i, type, fulfilled, currentCount, requiredCount, matchesType, reasoning));
                log.info("AI fulfillment [{}]: type={}, fulfilled={}, {}/{}, matches={}", i, type, fulfilled, currentCount, requiredCount, matchesType);
            } catch (Exception e) {
                log.warn("AI fulfillment failed for item [{}]: {}", i, e.getMessage());
                results.add(null);
            }
        }
        return results;
    }

    /**
     * Витягує JSON об'єкт або масив з тексту відповіді AI.
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) return text;
        String trimmed = text.trim();

        Matcher codeMatcher = Pattern.compile(
                "```(?:json)?\\s*([\\[{].*?[\\]}])\\s*```", Pattern.DOTALL).matcher(trimmed);
        if (codeMatcher.find()) {
            return codeMatcher.group(1);
        }

        // Try object {...} first for single items
        int objStart = trimmed.indexOf('{');
        int objEnd = trimmed.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return trimmed.substring(objStart, objEnd + 1);
        }

        // Then try array [...]
        int arrStart = trimmed.indexOf('[');
        int arrEnd = trimmed.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            return trimmed.substring(arrStart, arrEnd + 1);
        }

        return text;
    }
}
