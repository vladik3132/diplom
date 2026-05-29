package ua.edu.teacherlicence.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.user.model.Role;
import ua.edu.teacherlicence.user.model.User;
import ua.edu.teacherlicence.user.repository.UserRepository;

import java.util.List;

/**
 * Сервіс нотифікацій про зміни даних викладачів.
 *
 * Кому шлемо при зміні даних викладача T:
 *  - Адмінам — за всіх (DATA_CHANGE).
 *  - Начальникам тієї ж кафедри — за свою кафедру.
 *  - Самому викладачу T (якщо у нього є user-account з role=TEACHER) —
 *    тільки коли зміни вніс не він сам.
 *
 * Самонотифікації (актор = адресат) пропускаються.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeNotificationService {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;

    /**
     * Надіслати нотифікацію про зміну даних викладача.
     *
     * @param teacher      викладач, дані якого змінились
     * @param changedByUser користувач, що вніс зміни
     * @param action       дія (додано/оновлено/видалено)
     * @param section      розділ (Публікації, Дані п.38 тощо)
     * @param details      короткий опис змін
     */
    @Async
    public void notifyDataChanged(Teacher teacher, User changedByUser, String action, String section, String details) {
        if (teacher == null) return;

        String teacherName = teacher.getLastName() + " " + teacher.getFirstName();
        String title = section + ": " + action;
        String message = teacherName + " — " + details;
        String linkUrl = "/teachers/" + teacher.getId();

        // 1. Нотифікації для адміністраторів
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        for (User admin : admins) {
            // Не нотифікуємо самого себе
            if (admin.getId().equals(changedByUser.getId())) continue;
            try {
                notificationService.create(admin.getId(), title, message, "DATA_CHANGE", linkUrl);
            } catch (Exception e) {
                log.warn("Failed to notify admin {}: {}", admin.getEmail(), e.getMessage());
            }
        }

        // 2. Нотифікації для начальників кафедри
        if (teacher.getDepartment() != null) {
            Long deptId = teacher.getDepartment().getId();
            // Знаходимо всіх викладачів кафедри, щоб знайти начальника
            List<Long> deptTeacherIds = teacherRepository.findByDepartmentId(deptId)
                    .stream().map(Teacher::getId).toList();

            List<User> heads = userRepository.findByRoleAndTeacherIdIn(
                    Role.HEAD_OF_DEPARTMENT, deptTeacherIds);

            for (User head : heads) {
                // Не нотифікуємо самого себе
                if (head.getId().equals(changedByUser.getId())) continue;
                try {
                    notificationService.create(head.getId(), title, message, "DATA_CHANGE", linkUrl);
                } catch (Exception e) {
                    log.warn("Failed to notify head {}: {}", head.getEmail(), e.getMessage());
                }
            }
        }

        // 3. Нотифікація самому викладачу (якщо у нього є user-account і не він сам змінив).
        //    Це покриває кейс «начальник кафедри / адмін щось мені змінив».
        userRepository.findByTeacherId(teacher.getId()).ifPresent(teacherUser -> {
            if (changedByUser != null && teacherUser.getId().equals(changedByUser.getId())) return;
            try {
                notificationService.create(teacherUser.getId(), title, message, "DATA_CHANGE", linkUrl);
            } catch (Exception e) {
                log.warn("Failed to notify teacher {}: {}", teacherUser.getEmail(), e.getMessage());
            }
        });
    }

    /**
     * Скорочений виклик: визначає Teacher з teacherId.
     */
    @Async
    public void notifyDataChanged(Long teacherId, User changedByUser, String action, String section, String details) {
        teacherRepository.findById(teacherId).ifPresent(
                teacher -> notifyDataChanged(teacher, changedByUser, action, section, details));
    }
}
