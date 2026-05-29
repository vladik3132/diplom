package ua.edu.teacherlicence.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.dto.AuthResponse;
import ua.edu.teacherlicence.auth.dto.LoginRequest;
import ua.edu.teacherlicence.auth.dto.RegisterRequest;
import org.springframework.security.core.Authentication;
import ua.edu.teacherlicence.auth.jwt.JwtTokenProvider;
import ua.edu.teacherlicence.auth.service.AuthService;
import ua.edu.teacherlicence.config.KeycloakProperties;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.user.model.User;
import ua.edu.teacherlicence.user.repository.UserRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final KeycloakProperties keycloakProperties;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            String token = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(buildAuthResponse(token, request.getEmail()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Невірний email або пароль"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Помилка автентифікації: " + e.getMessage()));
        }
    }

    /** Only ADMIN can register new users. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.register(
                    request.getEmail(),
                    request.getPassword(),
                    request.getRole()
            );

            String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** Returns current user info (role, teacherId, departmentId). Works with both local and Keycloak auth. */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        Long departmentId = null;
        if (user.getTeacherId() != null) {
            Teacher teacher = teacherRepository.findById(user.getTeacherId()).orElse(null);
            if (teacher != null && teacher.getDepartment() != null) {
                departmentId = teacher.getDepartment().getId();
            }
        }

        return ResponseEntity.ok(AuthResponse.builder()
                .email(user.getEmail())
                .role(user.getRole().name())
                .teacherId(user.getTeacherId())
                .departmentId(departmentId)
                .build());
    }

    /**
     * Refresh a still-valid local JWT token.
     * The client sends its current (not yet expired) token in the Authorization header;
     * a fresh token with a new expiry is returned.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Невалідний токен"));
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Користувача не знайдено"));
        }
        String newToken = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(buildAuthResponse(newToken, email));
    }

    // ─────────────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(String token, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        Long departmentId = null;
        if (user.getTeacherId() != null) {
            Teacher teacher = teacherRepository.findById(user.getTeacherId()).orElse(null);
            if (teacher != null && teacher.getDepartment() != null) {
                departmentId = teacher.getDepartment().getId();
            }
        }

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .teacherId(user.getTeacherId())
                .departmentId(departmentId)
                .build();
    }
}
