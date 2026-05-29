package ua.edu.teacherlicence.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.user.model.Role;
import ua.edu.teacherlicence.user.model.User;
import ua.edu.teacherlicence.user.repository.UserRepository;

import java.nio.file.AccessDeniedException;

/**
 * Utility bean to resolve the current authenticated user
 * and perform ownership / department checks.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;

    /** Get the currently authenticated User entity. */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Не авторизовано");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено: " + auth.getName()));
    }

    /** Get the Teacher entity bound to the current user (null for admin without binding). */
    public Teacher getCurrentTeacher() {
        User user = getCurrentUser();
        if (user.getTeacherId() == null) return null;
        return teacherRepository.findById(user.getTeacherId()).orElse(null);
    }

    /** Get the department ID of the current user's teacher (null if no binding). */
    public Long getCurrentDepartmentId() {
        Teacher teacher = getCurrentTeacher();
        if (teacher == null || teacher.getDepartment() == null) return null;
        return teacher.getDepartment().getId();
    }

    public boolean isAdmin() {
        return getCurrentUser().getRole() == Role.ADMIN;
    }

    public boolean isHead() {
        return getCurrentUser().getRole() == Role.HEAD_OF_DEPARTMENT;
    }

    public boolean isTeacher() {
        return getCurrentUser().getRole() == Role.TEACHER;
    }

    /**
     * Check that the current user can access data for a given teacher.
     * ADMIN — any teacher. HEAD — same department. TEACHER — only self.
     */
    public void checkTeacherAccess(Long teacherId) throws AccessDeniedException {
        User user = getCurrentUser();
        if (user.getRole() == Role.ADMIN) return;

        if (user.getTeacherId() == null) {
            throw new AccessDeniedException("Ваш обліковий запис не прив'язаний до викладача");
        }

        if (user.getRole() == Role.TEACHER) {
            if (!user.getTeacherId().equals(teacherId)) {
                throw new AccessDeniedException("Ви можете переглядати лише власні дані");
            }
            return;
        }

        // HEAD_OF_DEPARTMENT — check same department
        if (user.getRole() == Role.HEAD_OF_DEPARTMENT) {
            Teacher myTeacher = teacherRepository.findById(user.getTeacherId()).orElse(null);
            Teacher targetTeacher = teacherRepository.findById(teacherId).orElse(null);
            if (myTeacher == null || targetTeacher == null
                    || myTeacher.getDepartment() == null || targetTeacher.getDepartment() == null
                    || !myTeacher.getDepartment().getId().equals(targetTeacher.getDepartment().getId())) {
                throw new AccessDeniedException("Доступ лише до викладачів вашої кафедри");
            }
        }
    }

    /**
     * Check department access. ADMIN — any. HEAD — own department only.
     */
    public void checkDepartmentAccess(Long departmentId) throws AccessDeniedException {
        User user = getCurrentUser();
        if (user.getRole() == Role.ADMIN) return;

        Long myDeptId = getCurrentDepartmentId();
        if (myDeptId == null || !myDeptId.equals(departmentId)) {
            throw new AccessDeniedException("Доступ лише до вашої кафедри");
        }
    }
}
