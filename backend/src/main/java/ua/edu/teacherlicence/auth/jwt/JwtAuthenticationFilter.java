package ua.edu.teacherlicence.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.edu.teacherlicence.auth.keycloak.KeycloakUserSyncService;
import ua.edu.teacherlicence.user.model.Role;
import ua.edu.teacherlicence.user.model.User;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    // Optional — only present when keycloak.enabled=true
    private NimbusJwtDecoder keycloakJwtDecoder;
    private KeycloakUserSyncService keycloakUserSyncService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Autowired(required = false)
    public void setKeycloakJwtDecoder(NimbusJwtDecoder keycloakJwtDecoder) {
        this.keycloakJwtDecoder = keycloakJwtDecoder;
    }

    @Autowired(required = false)
    public void setKeycloakUserSyncService(KeycloakUserSyncService keycloakUserSyncService) {
        this.keycloakUserSyncService = keycloakUserSyncService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            // Path 1: Try local HMAC JWT
            if (jwtTokenProvider.validateToken(token)) {
                authenticateLocal(token, request);
            }
            // Path 2: Try Keycloak RSA JWT
            else if (keycloakJwtDecoder != null && keycloakUserSyncService != null) {
                authenticateKeycloak(token, request);
            } else {
                log.warn("Token present but no auth path matched. keycloakDecoder={}, syncService={}",
                        keycloakJwtDecoder != null, keycloakUserSyncService != null);
            }
        } else {
            log.debug("No token in request: {} {}", request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateLocal(String token, HttpServletRequest request) {
        String email = jwtTokenProvider.getEmailFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @SuppressWarnings("unchecked")
    private void authenticateKeycloak(String token, HttpServletRequest request) {
        try {
            Jwt jwt = keycloakJwtDecoder.decode(token);

            String email = jwt.getClaimAsString("email");
            String firstName = jwt.getClaimAsString("given_name");
            String lastName = jwt.getClaimAsString("family_name");

            // Extract roles from realm_access.roles
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            List<String> keycloakRoles = realmAccess != null
                    ? (List<String>) realmAccess.get("roles")
                    : List.of();

            Role appRole = KeycloakUserSyncService.mapKeycloakRole(keycloakRoles);

            // Sync user (find or create, link to Teacher)
            User user = keycloakUserSyncService.syncKeycloakUser(email, firstName, lastName, appRole);

            // Build Spring Security authentication
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("Keycloak auth: email={} keycloakRoles={} appRole={} dbRole={} authorities={}",
                    email, keycloakRoles, appRole, user.getRole(), authorities);
        } catch (JwtException e) {
            log.debug("Keycloak JWT validation failed: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Keycloak auth error: {}", e.getMessage());
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
