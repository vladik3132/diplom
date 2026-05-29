package ua.edu.teacherlicence.ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.ai.dto.AiRequest;
import ua.edu.teacherlicence.ai.dto.AiResponse;
import ua.edu.teacherlicence.ai.dto.ClassificationResult;
import ua.edu.teacherlicence.ai.service.AchievementClassifierService;
import ua.edu.teacherlicence.ai.service.AiAssistantService;
import ua.edu.teacherlicence.ai.service.AiEmbeddingIndexService;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
@PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_DEPARTMENT')")
public class AiController {

    private final AiAssistantService aiAssistantService;
    private final AchievementClassifierService classifierService;
    /**
     * Optional: присутній тільки якщо ai.rag.enabled=true та є VectorStore bean (prod).
     * У dev — null, endpoint реіндексації повертає 501.
     */
    private final ObjectProvider<AiEmbeddingIndexService> embeddingIndexProvider;

    @PostMapping("/chat")
    public AiResponse chat(@RequestBody AiRequest request) {
        return aiAssistantService.chat(
                request.getMessage(),
                request.getContext(),
                request.getConversationId()
        );
    }

    @PostMapping("/generate")
    public AiResponse generateText(@RequestBody AiRequest request) {
        return aiAssistantService.generateText(request.getMessage());
    }

    @PostMapping("/classify")
    public ClassificationResult classify(@RequestBody AiRequest request) {
        return classifierService.classify(request.getMessage());
    }

    /**
     * Очистити історію конкретної розмови. Викликається при "Новий чат".
     * Ідемпотентна — не помиляється якщо розмови немає.
     */
    @DeleteMapping("/chat/{conversationId}")
    public void clearConversation(@PathVariable String conversationId) {
        aiAssistantService.clearConversation(conversationId);
    }

    /**
     * Повна реіндексація RAG (vector store) усіх викладачів.
     * Потрібна після bulk import або коли треба терміново оновити семантичний індекс
     * (scheduled reindex спрацює автоматично, але цей endpoint дозволяє не чекати).
     * Доступний лише ADMIN.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reindexAll() {
        AiEmbeddingIndexService idx = embeddingIndexProvider.getIfAvailable();
        if (idx == null || !idx.isAvailable()) {
            return ResponseEntity.status(501).body(Map.of(
                    "error", "RAG indexing is not available in this environment",
                    "hint", "Requires prod profile with pgvector configured and ai.rag.enabled=true"
            ));
        }
        int count = idx.reindexAll();
        return ResponseEntity.ok(Map.of("indexed", count, "status", "ok"));
    }

    /**
     * Точкова реіндексація одного викладача (upsert у vector store).
     * Корисно після редагування профілю без чекання scheduled reindex.
     */
    @PostMapping("/reindex/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_DEPARTMENT')")
    public ResponseEntity<Map<String, Object>> reindexTeacher(@PathVariable Long teacherId) {
        AiEmbeddingIndexService idx = embeddingIndexProvider.getIfAvailable();
        if (idx == null || !idx.isAvailable()) {
            return ResponseEntity.status(501).body(Map.of("error", "RAG not available"));
        }
        idx.indexTeacher(teacherId);
        return ResponseEntity.ok(Map.of("teacherId", teacherId, "status", "ok"));
    }
}
