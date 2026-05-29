package ua.edu.teacherlicence.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Jackson configuration:
 * - Hibernate lazy-loading support
 * - LocalDate format: dd.MM.yyyy (Ukrainian standard)
 *   Also accepts yyyy-MM-dd (ISO) on deserialization for backward compatibility
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        return module;
    }

    @Bean
    public JavaTimeModule javaTimeModule() {
        JavaTimeModule module = new JavaTimeModule();
        // Серіалізація: завжди dd.MM.yyyy
        module.addSerializer(LocalDate.class, new LocalDateSerializer(DD_MM_YYYY));
        // Десеріалізація: приймає dd.MM.yyyy і yyyy-MM-dd (для сумісності)
        module.addDeserializer(LocalDate.class, new JsonDeserializer<>() {
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String str = p.getValueAsString();
                if (str == null || str.isBlank()) return null;
                str = str.trim();
                // Спочатку dd.MM.yyyy
                try {
                    return LocalDate.parse(str, DD_MM_YYYY);
                } catch (DateTimeParseException ignored) {}
                // Потім ISO yyyy-MM-dd
                try {
                    return LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (DateTimeParseException ignored) {}
                return null;
            }
        });
        return module;
    }
}
