package ua.edu.teacherlicence.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRequest {
    private String message;
    private String context;
    /**
     * ID розмови для підтримки контексту діалогу (multi-turn).
     * Якщо null/порожнє — виклик робиться без пам'яті (stateless).
     * Генерується фронтендом (UUID) та зберігається в localStorage.
     */
    private String conversationId;
}
