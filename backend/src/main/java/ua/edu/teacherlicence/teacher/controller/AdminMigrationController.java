package ua.edu.teacherlicence.teacher.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.teacherlicence.teacher.service.CombatDatesNormalizationService;
import ua.edu.teacherlicence.teacher.service.CombatDatesNormalizationService.NormalizationReport;

/**
 * Контролер одноразових admin-утиліт міграції/нормалізації даних.
 *
 * <p>Викликається вручну, не через Liquibase, бо потребує доступу до AI-сервісів
 * та інших Spring beans, які не доступні в SQL-міграціях.
 *
 * <p>Шлях: {@code POST /api/admin/...}, тільки ADMIN.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMigrationController {

    private final CombatDatesNormalizationService combatDatesNormalizationService;

    /**
     * Прогоняє всі непорожні {@code Teacher.combatExperienceDates} через AI-нормалізатор
     * (фолбек на regex) і зберігає у канонічній формі
     * {@code "дд.мм.рррр – дд.мм.рррр[, ...]"}.
     *
     * @param dryRun якщо true — лише повертає звіт, БД не змінюється. Default: false.
     */
    @PostMapping("/normalize-combat-dates")
    public ResponseEntity<NormalizationReport> normalizeCombatDates(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("Combat dates normalization requested (dryRun={})", dryRun);
        NormalizationReport report = combatDatesNormalizationService.normalizeAll(dryRun);
        log.info("Combat dates normalization done: scanned={}, alreadyCanonical={}, "
                        + "byAi={}, byRegex={}, failed={}",
                report.totalScanned(), report.alreadyCanonical(),
                report.normalizedByAi(), report.normalizedByRegex(), report.failed());
        return ResponseEntity.ok(report);
    }
}
