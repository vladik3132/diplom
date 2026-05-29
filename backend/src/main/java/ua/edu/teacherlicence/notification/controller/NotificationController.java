package ua.edu.teacherlicence.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.notification.model.Notification;
import ua.edu.teacherlicence.notification.service.NotificationService;
import ua.edu.teacherlicence.user.repository.UserRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private Long resolveUserId(Authentication auth, Long userId) {
        if (userId != null) return userId;
        String email = auth.getName();
        var user = userRepository.findByEmail(email);
        return user.map(u -> u.getId()).orElse(0L);
    }

    @GetMapping
    public List<Notification> getNotifications(
            Authentication auth,
            @RequestParam(required = false) Long userId) {
        return notificationService.getByUserId(resolveUserId(auth, userId));
    }

    @GetMapping("/unread")
    public List<Notification> getUnread(
            Authentication auth,
            @RequestParam(required = false) Long userId) {
        return notificationService.getUnreadByUserId(resolveUserId(auth, userId));
    }

    @GetMapping("/unread/count")
    public Map<String, Long> getUnreadCount(
            Authentication auth,
            @RequestParam(required = false) Long userId) {
        return Map.of("count", notificationService.getUnreadCount(resolveUserId(auth, userId)));
    }

    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
    }

    @PatchMapping("/read-all")
    public void markAllAsRead(
            Authentication auth,
            @RequestParam(required = false) Long userId) {
        notificationService.markAllAsRead(resolveUserId(auth, userId));
    }
}
