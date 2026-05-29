package ua.edu.teacherlicence.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-сервіс для перевірки відповідності публікації напряму діяльності кафедри.
 * Використовується при створенні/імпорті публікацій для пп.1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
public class PublicationRelevanceAiService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    /** Кеш: normalized(title) + departmentId → result */
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
            Ти — експерт з ліцензування вищої освіти України.
            Твоє завдання: визначити, чи відповідає тематика наукової публікації
            напряму діяльності (спеціальності) кафедри.

            Правила оцінки:
            1. Публікація вважається відповідною, якщо її тема пов'язана зі спеціальністю кафедри
               або суміжними напрямами.
            2. Враховуй міждисциплінарність: публікація з математики може відповідати кафедрі ІТ,
               публікація з психології — кафедрі педагогіки тощо.
            3. Загальнопедагогічні, загальнометодичні публікації відповідають будь-якій кафедрі ВНЗ.
            4. Якщо тематика абсолютно не пов'язана — не відповідає.

            Відповідай ТІЛЬКИ у форматі JSON: {"relevant": true/false}
            """;

    /**
     * Перевіряє чи публікація відповідає напряму кафедри.
     *
     * @param publicationTitle   Назва публікації
     * @param departmentName     Назва кафедри
     * @param departmentId       ID кафедри (для кешу)
     * @param specialties        Спеціальності ОПП кафедри (для контексту)
     * @return true = відповідає, false = не відповідає
     */
    public boolean checkRelevance(
            String publicationTitle,
            String departmentName,
            Long departmentId,
            List<String> specialties
    ) {
        if (publicationTitle == null || publicationTitle.isBlank()) {
            return false;
        }

        String cacheKey = normalize(publicationTitle) + ":" + departmentId;
        return cache.computeIfAbsent(cacheKey, k ->
                doCheck(publicationTitle, departmentName, specialties)
        );
    }

    /**
     * Пакетна перевірка кількох публікацій (для імпорту).
     * Використовує один AI-запит для до 15 публікацій.
     */
    public Map<String, Boolean> checkRelevanceBatch(
            List<String> titles,
            String departmentName,
            Long departmentId,
            List<String> specialties
    ) {
        Map<String, Boolean> results = new java.util.LinkedHashMap<>();

        // Розділяємо на вже кешовані та нові
        List<String> uncachedTitles = new java.util.ArrayList<>();
        for (String title : titles) {
            String cacheKey = normalize(title) + ":" + departmentId;
            Boolean cached = cache.get(cacheKey);
            if (cached != null) {
                results.put(title, cached);
            } else {
                uncachedTitles.add(title);
            }
        }

        if (uncachedTitles.isEmpty()) return results;

        // Пакетна перевірка — батчами по 15
        int batchSize = 15;
        for (int i = 0; i < uncachedTitles.size(); i += batchSize) {
            List<String> batch = uncachedTitles.subList(i, Math.min(i + batchSize, uncachedTitles.size()));
            Map<String, Boolean> batchResults = doCheckBatch(batch, departmentName, specialties);
            for (var entry : batchResults.entrySet()) {
                results.put(entry.getKey(), entry.getValue());
                cache.put(normalize(entry.getKey()) + ":" + departmentId, entry.getValue());
            }
        }

        // Для тих, що не ввійшли в результат батчу — fallback true
        for (String title : uncachedTitles) {
            results.putIfAbsent(title, true);
        }

        return results;
    }

    private boolean doCheck(String title, String departmentName, List<String> specialties) {
        try {
            String userPrompt = buildSinglePrompt(title, departmentName, specialties);

            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            return parseSingleResponse(response);
        } catch (Exception e) {
            log.error("AI publication relevance check error: {}", e.getMessage());
            return true; // Fallback: вважаємо відповідною
        }
    }

    private Map<String, Boolean> doCheckBatch(List<String> titles, String departmentName, List<String> specialties) {
        Map<String, Boolean> results = new java.util.LinkedHashMap<>();
        try {
            String batchSystemPrompt = """
                    Ти — експерт з ліцензування вищої освіти України.
                    Твоє завдання: для КОЖНОЇ публікації визначити, чи відповідає її тематика
                    напряму діяльності (спеціальності) кафедри.

                    Правила:
                    1. Публікація відповідна, якщо пов'язана зі спеціальністю кафедри або суміжними напрямами.
                    2. Міждисциплінарні публікації — враховуй контекст.
                    3. Загальнопедагогічні/методичні — відповідають будь-якій кафедрі ВНЗ.

                    Відповідай ТІЛЬКИ JSON-масивом: [{"index": 0, "relevant": true/false}, ...]
                    """;

            StringBuilder sb = new StringBuilder();
            sb.append("=== КАФЕДРА ===\n");
            sb.append("Назва: ").append(departmentName).append("\n");
            if (specialties != null && !specialties.isEmpty()) {
                sb.append("Спеціальності ОПП: ").append(String.join("; ", specialties)).append("\n");
            }
            sb.append("\n=== ПУБЛІКАЦІЇ ===\n");
            for (int i = 0; i < titles.size(); i++) {
                sb.append(i).append(". ").append(titles.get(i)).append("\n");
            }

            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .system(batchSystemPrompt)
                    .user(sb.toString())
                    .call()
                    .content();

            // Parse batch response
            String json = extractJsonArray(response);
            JsonNode array = objectMapper.readTree(json);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    int idx = node.has("index") ? node.get("index").asInt(-1) : -1;
                    boolean relevant = node.has("relevant") && node.get("relevant").asBoolean(true);
                    if (idx >= 0 && idx < titles.size()) {
                        results.put(titles.get(idx), relevant);
                    }
                }
            }
        } catch (Exception e) {
            log.error("AI batch publication relevance check error: {}", e.getMessage());
            // Fallback: все відповідне
            for (String title : titles) {
                results.put(title, true);
            }
        }

        return results;
    }

    private String buildSinglePrompt(String title, String departmentName, List<String> specialties) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== КАФЕДРА ===\n");
        sb.append("Назва: ").append(departmentName).append("\n");
        if (specialties != null && !specialties.isEmpty()) {
            sb.append("Спеціальності ОПП: ").append(String.join("; ", specialties)).append("\n");
        }
        sb.append("\n=== ПУБЛІКАЦІЯ ===\n");
        sb.append("Назва: ").append(title).append("\n");
        sb.append("\nВідповідай JSON: {\"relevant\": true/false}");
        return sb.toString();
    }

    private boolean parseSingleResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            return node.has("relevant") && node.get("relevant").asBoolean(true);
        } catch (Exception e) {
            log.warn("Failed to parse AI relevance response: {}", response);
            return true; // Fallback
        }
    }

    private String extractJson(String text) {
        Matcher matcher = Pattern.compile("\\{[^}]+}").matcher(text);
        if (matcher.find()) return matcher.group();
        return text.trim();
    }

    private String extractJsonArray(String text) {
        Matcher matcher = Pattern.compile("\\[.*]", Pattern.DOTALL).matcher(text);
        if (matcher.find()) return matcher.group();
        return text.trim();
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    public void clearCache() {
        cache.clear();
    }

    public void clearCacheForDepartment(Long departmentId) {
        cache.keySet().removeIf(k -> k.endsWith(":" + departmentId));
    }
}
