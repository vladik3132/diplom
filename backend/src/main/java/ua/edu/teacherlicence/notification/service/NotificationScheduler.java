package ua.edu.teacherlicence.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;
import ua.edu.teacherlicence.achievement.service.ComplianceService;
import ua.edu.teacherlicence.discipline.model.DisciplineDocument;
import ua.edu.teacherlicence.discipline.repository.DisciplineDocumentRepository;
import ua.edu.teacherlicence.user.model.User;
import ua.edu.teacherlicence.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final ComplianceService complianceService;
    private final UserRepository userRepository;

    private static final int[] REMINDER_DAYS = {30, 14, 7, 1};

    /**
     * Щоденна перевірка дедлайнів документів
     * Запускається о 08:00 щодня
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void checkDocumentDeadlines() {
        log.info("Запуск перевірки дедлайнів документів...");
        // Реалізація буде додана після створення DisciplineDocumentRepository
        // Перевіряє дедлайни за 30/14/7/1 днів
    }

    /**
     * Щомісячна перевірка відповідності п.38
     * Запускається 1-го числа кожного місяця о 09:00
     */
    @Scheduled(cron = "0 0 9 1 * *")
    public void checkComplianceMonthly() {
        log.info("Запуск щомісячної перевірки відповідності п.38...");

        List<ComplianceReportDto> reports = complianceService.checkComplianceAll();

        for (ComplianceReportDto report : reports) {
            if (report.getStatus() == ComplianceStatus.WARNING
                    || report.getStatus() == ComplianceStatus.NON_COMPLIANT) {

                // Знайти user по teacherId
                userRepository.findAll().stream()
                        .filter(u -> report.getTeacherId().equals(u.getTeacherId()))
                        .findFirst()
                        .ifPresent(user -> {
                            String title = report.getStatus() == ComplianceStatus.WARNING
                                    ? "Потрібна увага: відповідність п.38"
                                    : "Не відповідає вимогам п.38";

                            String message = String.format(
                                    "%s: %d з %d необхідних типів досягнень. %s",
                                    report.getTeacherName(),
                                    report.getUniqueTypeCount(),
                                    4,
                                    report.getMissingInfo() != null && !report.getMissingInfo().isEmpty()
                                            ? report.getMissingInfo().get(0)
                                            : ""
                            );

                            notificationService.create(
                                    user.getId(),
                                    title,
                                    message,
                                    "COMPLIANCE_WARNING",
                                    "/teachers/" + report.getTeacherId()
                            );
                        });
            }
        }

        log.info("Перевірка п.38 завершена. Оброблено {} викладачів", reports.size());
    }
}
