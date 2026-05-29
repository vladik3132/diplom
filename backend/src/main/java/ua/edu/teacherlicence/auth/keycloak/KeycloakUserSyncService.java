package ua.edu.teacherlicence.auth.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.user.model.Role;
import ua.edu.teacherlicence.user.model.User;
import ua.edu.teacherlicence.user.repository.UserRepository;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KeycloakUserSyncService {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;

    /** Keycloak role name → application Role */
    private static final Map<String, Role> ROLE_MAP = Map.of(
            "TEACHER", Role.TEACHER,
            "DEPARTMENT_HEAD", Role.HEAD_OF_DEPARTMENT,
            "INSTITUTE_EDUCATIONAL_DEPARTMENT", Role.ADMIN
    );

    /**
     * Map the highest-priority Keycloak role to application role.
     */
    public static Role mapKeycloakRole(List<String> keycloakRoles) {
        // Priority: ADMIN > HEAD > TEACHER
        if (keycloakRoles.contains("SUPERADMIN")) return Role.ADMIN;
        if (keycloakRoles.contains("INSTITUTE_EDUCATIONAL_DEPARTMENT")) return Role.ADMIN;
        if (keycloakRoles.contains("DEPARTMENT_HEAD")) return Role.HEAD_OF_DEPARTMENT;
        if (keycloakRoles.contains("TEACHER")) return Role.TEACHER;
        return Role.TEACHER; // default
    }

    /**
     * Find or create a User for the Keycloak-authenticated person.
     * Matches Teacher by lastName + firstName (case-insensitive).
     */
    @Transactional
    public User syncKeycloakUser(String email, String firstName, String lastName, Role role) {
        // 1. Check if user already exists
        var existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            boolean dirty = false;
            // Always sync role from Keycloak (source of truth)
            if (user.getRole() != role) {
                log.info("Syncing role for {} from {} to {}", email, user.getRole(), role);
                user.setRole(role);
                dirty = true;
            }
            // Re-link teacherId якщо нема (наприклад роль помінялась з ADMIN→TEACHER
            // або викладача додали у БД після першого створення user)
            if (user.getTeacherId() == null && role != Role.ADMIN
                    && lastName != null && firstName != null) {
                var matches = teacherRepository
                        .findByLastNameIgnoreCaseAndFirstNameIgnoreCase(lastName, firstName);
                if (matches.size() == 1) {
                    Long tid = matches.get(0).getId();
                    user.setTeacherId(tid);
                    dirty = true;
                    log.info("Re-linked Keycloak user {} to teacher {} (id={}) on sync", email, lastName, tid);
                } else if (matches.size() > 1) {
                    log.warn("Multiple teachers match {} {} — cannot auto-link user {}",
                            lastName, firstName, email);
                }
            }
            if (dirty) userRepository.save(user);
            return user;
        }

        // 2. Try to match Teacher by name
        Long teacherId = null;
        if (lastName != null && firstName != null) {
            List<Teacher> matches = teacherRepository
                    .findByLastNameIgnoreCaseAndFirstNameIgnoreCase(lastName, firstName);
            if (matches.size() == 1) {
                teacherId = matches.get(0).getId();
                log.info("Matched Keycloak user {} to teacher {} {} (id={})",
                        email, lastName, firstName, teacherId);
            } else if (matches.size() > 1) {
                log.warn("Multiple teachers match {} {} — cannot auto-link user {}",
                        lastName, firstName, email);
            } else {
                log.info("No teacher found for {} {} — creating user {} without teacher link",
                        lastName, firstName, email);
            }
        }

        // 3. Create new user
        User newUser = User.builder()
                .email(email)
                .passwordHash(null) // Keycloak user, no local password
                .role(role)
                .teacherId(teacherId)
                .isActive(true)
                .build();
        userRepository.save(newUser);
        log.info("Created new user from Keycloak: {} role={} teacherId={}", email, role, teacherId);
        return newUser;
    }

    private static int rolePriority(Role role) {
        return switch (role) {
            case ADMIN -> 3;
            case HEAD_OF_DEPARTMENT -> 2;
            case TEACHER -> 1;
        };
    }
}
