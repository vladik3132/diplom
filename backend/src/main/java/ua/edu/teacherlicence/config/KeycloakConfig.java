package ua.edu.teacherlicence.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KeycloakConfig {

    private final KeycloakProperties keycloakProperties;

    @Bean
    public NimbusJwtDecoder keycloakJwtDecoder() {
        log.info("Configuring Keycloak JWT decoder with JWK Set URI: {}", keycloakProperties.getJwkSetUri());
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(keycloakProperties.getJwkSetUri())
                .build();
        // Skip issuer validation for multi-environment support
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator()
        ));
        return decoder;
    }
}
