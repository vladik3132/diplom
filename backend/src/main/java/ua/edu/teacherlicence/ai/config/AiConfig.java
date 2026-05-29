package ua.edu.teacherlicence.ai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфігурація AI модуля.
 * Mistral AI підключення через OpenAI-сумісний API (application.yml: spring.ai.openai.*).
 * Для переключення на Ollama — замінити base-url та api-key.
 *
 * Модуль активується тільки якщо ai.enabled=true
 *
 * ПРИМІТКА: ToolCallbackProvider свідомо НЕ оголошений як @Bean, щоб не потрапити
 * у глобальний Spring AI ToolCallbackResolver — інакше виникає циклічна залежність:
 *   ChatClient.Builder → ToolCallbackResolver → AiToolsService → ComplianceService →
 *   → QualificationMatchAiService → ChatClient.Builder (цикл).
 * Провайдер створюється локально в {@link ua.edu.teacherlicence.ai.service.AiAssistantService}.
 */
@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
public class AiConfig {

    /**
     * Пам'ять чату для підтримки діалогу (multi-turn).
     * Зберігає історію повідомлень за conversationId.
     * InMemoryChatMemory — in-process, не персистентна (очищається при рестарті).
     * Для продакшена варто замінити на JDBC-backed або Redis.
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
}
