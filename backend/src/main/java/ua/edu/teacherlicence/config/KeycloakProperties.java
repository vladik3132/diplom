package ua.edu.teacherlicence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    private boolean enabled = false;
    private String authUrl = "https://auth.viti.edu.ua";
    private String realm = "grade-book";
    private String jwkSetUri;
}
