package ua.edu.teacherlicence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Клієнт закрив з'єднання (browser tab закрита / fetch скасований / proxy timeout)
     * поки backend ще писав відповідь. Це НЕ помилка коду — нормальна мережева ситуація.
     * Логуємо як DEBUG і повертаємо null (відповідь все одно вже не дійде до клієнта).
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleClientDisconnect(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected before response was fully written: {}", ex.getMessage());
        return null;
    }

    /**
     * Tomcat-специфічна форма Broken pipe — той самий кейс, інший wrapper.
     * Reflection-check у catch-handler нижче, бо ClientAbortException імпортується
     * з org.apache.catalina.connector — спеціальний handler не потрібен (gracefully
     * деградує до handleAll), але ми відрізняємо це повідомлення.
     */

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Доступ заборонено"));
    }

    @ExceptionHandler(java.nio.file.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleNioAccessDenied(java.nio.file.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Доступ заборонено"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingPart(MissingServletRequestPartException ex) {
        log.error("Missing request part: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Відсутня частина запиту: " + ex.getRequestPartName()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.error("File too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Файл занадто великий. Максимум 50MB"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        // Broken pipe / клієнт відключився — не вважаємо помилкою коду
        if (isClientAbort(ex)) {
            log.debug("Client aborted request: {}", ex.getMessage());
            return null;
        }
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getClass().getSimpleName() + ": " + ex.getMessage()));
    }

    /**
     * Перевірка чи є exception результатом обриву з'єднання клієнтом
     * (Broken pipe / Connection reset / ClientAbortException).
     */
    private boolean isClientAbort(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            String className = t.getClass().getName();
            if (className.contains("ClientAbortException")
                    || className.contains("AsyncRequestNotUsableException")) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Broken pipe")
                    || msg.contains("Connection reset")
                    || msg.contains("connection abort"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
