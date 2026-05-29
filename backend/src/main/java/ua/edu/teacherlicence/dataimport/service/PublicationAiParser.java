package ua.edu.teacherlicence.dataimport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-парсер бібліографічних записів публікацій.
 * Використовує Mistral (ChatClient) для витягнення структурованих даних:
 * назва, автори, сторінки, том/випуск.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
public class PublicationAiParser {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Результат парсингу одного запису.
     */
    public record ParsedFields(String title, String authors, String pages, String volume) {}

    private static final String SYSTEM_PROMPT = """
            Ти — парсер академічних бібліографічних записів українських та англомовних публікацій.
            Для кожного запису витягни:
            - title: ТІЛЬКИ назва публікації/статті (без авторів, журналу, конференції, сторінок, року, DOI, URL)
            - authors: список усіх авторів через кому (формат: "Прізвище І.Б." або повне ім'я якщо ініціалів немає)
            - pages: сторінки (наприклад "10-20" або "244 с.")
            - volume: том/випуск (число або "5(3)")

            ВАЖЛИВІ правила:
            1. Назва конференції/збірника/журналу НЕ є назвою публікації! Витягуй саме назву СТАТТІ/РОБОТИ.
            2. Якщо запис містить ТІЛЬКИ назву конференції без окремої назви статті — постав title = назва конференції.
            3. Якщо поле не можна визначити — постав null.
            4. Поверни ТІЛЬКИ валідний JSON масив без будь-яких пояснень чи коментарів.
            5. Кількість об'єктів у масиві ПОВИННА дорівнювати кількості вхідних записів.

            Приклад:
            Вхід:
            [0] Бовда Е.М., Романюк В.А., Бовда В.Е. Сучасні підходи в побудові системи управління інформаційно-телекомунікаційними мережами військового призначення. І МІЖНАРОДНА НАУКОВО-ТЕХНІЧНА КОНФЕРЕНЦІЯ «Системи і технології зв'язку». С. 20-29.
            [1] Sova, O., Zhuk, O., Redziuk, Y. (2023). Mathematical model for optimizing the structure of a heterogeneous telecommunication network. Eastern-European Journal of Enterprise Technologies, 6(4 (126)), 17–35.

            Вихід:
            [{"idx":0,"title":"Сучасні підходи в побудові системи управління інформаційно-телекомунікаційними мережами військового призначення","authors":"Бовда Е.М., Романюк В.А., Бовда В.Е.","pages":"20-29","volume":null},{"idx":1,"title":"Mathematical model for optimizing the structure of a heterogeneous telecommunication network","authors":"Sova O., Zhuk O., Redziuk Y.","pages":"17-35","volume":"6(4 (126))"}]
            """;

    /**
     * Парсить масив бібліографічних записів через AI, з батч-обробкою.
     *
     * @param rawEntries список сирих текстів публікацій
     * @return список ParsedFields (null для записів, де AI не спрацював)
     */
    public List<ParsedFields> parseEntries(List<String> rawEntries) {
        List<ParsedFields> results = new ArrayList<>(Collections.nCopies(rawEntries.size(), null));

        int batchSize = 10;
        for (int i = 0; i < rawEntries.size(); i += batchSize) {
            int end = Math.min(i + batchSize, rawEntries.size());
            List<String> batch = rawEntries.subList(i, end);
            try {
                List<ParsedFields> batchResult = callAi(batch);
                for (int j = 0; j < batchResult.size() && (i + j) < results.size(); j++) {
                    results.set(i + j, batchResult.get(j));
                }
                log.info("AI parsed publications batch [{}-{}]: {} results", i, end - 1, batchResult.size());
            } catch (Exception e) {
                log.warn("AI publication parsing failed for batch [{}-{}]: {}", i, end - 1, e.getMessage());
                // null залишається — fallback на regex
            }
        }
        return results;
    }

    /**
     * Відправляє батч записів до AI і парсить відповідь.
     */
    private List<ParsedFields> callAi(List<String> entries) throws Exception {
        // Формуємо текст записів
        StringBuilder userText = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            // Обрізаємо дуже довгі записи
            if (entry.length() > 800) {
                entry = entry.substring(0, 800);
            }
            userText.append("[").append(i).append("] ").append(entry).append("\n");
        }

        // Виклик AI
        ChatClient chatClient = chatClientBuilder.build();
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userText.toString())
                .call()
                .content();

        log.debug("AI publication response: {}", response);

        // Витягуємо JSON з відповіді (може бути обгорнутий у ```json...```)
        String jsonStr = extractJson(response);

        // Парсимо JSON
        List<Map<String, Object>> parsed = objectMapper.readValue(
                jsonStr, new TypeReference<List<Map<String, Object>>>() {});

        // Мапимо в ParsedFields
        List<ParsedFields> result = new ArrayList<>();
        for (Map<String, Object> item : parsed) {
            String title = getStr(item, "title");
            String authors = getStr(item, "authors");
            String pages = getStr(item, "pages");
            String volume = getStr(item, "volume");

            // Валідація: title має бути непорожній
            if (title != null && title.trim().length() < 3) {
                title = null;
            }

            result.add(new ParsedFields(title, authors, pages, volume));
        }

        return result;
    }

    /**
     * Витягує JSON масив із тексту відповіді AI.
     * AI може повернути JSON обгорнутий у ```json...``` або з текстом навколо.
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "[]";
        }

        // Спробуємо знайти JSON масив у відповіді
        String trimmed = response.trim();

        // Прибираємо ```json ... ``` обгортку
        Matcher codeMatcher = Pattern.compile("```(?:json)?\\s*(\\[.*?])\\s*```",
                Pattern.DOTALL).matcher(trimmed);
        if (codeMatcher.find()) {
            return codeMatcher.group(1);
        }

        // Шукаємо перший [ і останній ]
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
