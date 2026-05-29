package ua.edu.teacherlicence.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ua.edu.teacherlicence.compliance.repository.TeacherComplianceCacheRepository;

import static ua.edu.teacherlicence.compliance.config.ComplianceAsyncConfig.COMPLIANCE_EXECUTOR;

/**
 * Warm-up compliance cache at startup.
 *
 * При першому запуску після міграції V3 таблиці кешу порожні → /compliance
 * і /compliance-summary повертатимуть порожньо. Цей listener:
 *  1. Перевіряє чи кеш пустий.
 *  2. Якщо так — асинхронно запускає повну перебудову + refresh materialized view.
 *
 * Виконується лише якщо cache заповнений менш ніж на 50 % від кількості викладачів
 * (тобто після міграції, або якщо хтось зруйнував cache вручну).
 *
 * Після першого успішного warm-up наступні рестарти — миттєві (cache persistent).
 */
@Slf4j
@Component
@Profile("!schema-gen")
@RequiredArgsConstructor
public class ComplianceWarmupListener {

    private final TeacherComplianceCacheRepository cacheRepo;
    private final ComplianceCacheService complianceCache;
    private final DepartmentSummaryService departmentSummary;

    /**
     * Ordered after Liquibase & JPA init. @Async щоб не блокувати Spring ApplicationReadyEvent
     * (сервер відкриває порт і починає приймати запити паралельно).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Integer.MAX_VALUE)
    @Async(COMPLIANCE_EXECUTOR)
    public void warmUpCacheIfEmpty() {
        long cached = cacheRepo.count();
        if (cached > 0) {
            log.info("Compliance cache already populated ({} entries) — skipping warm-up.", cached);
            // Гарантуємо що MV актуальна (особливо якщо cache modified іншим шляхом).
            try {
                departmentSummary.refreshMaterializedView();
            } catch (Exception e) {
                log.warn("MV refresh on startup failed (non-fatal): {}", e.getMessage());
            }
            return;
        }

        log.info("Compliance cache is empty — starting warm-up ...");
        long t0 = System.currentTimeMillis();
        try {
            int n = complianceCache.refreshAll();
            log.info("Warm-up: rebuilt compliance cache for {} teachers in {} ms", n,
                    System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("Compliance warm-up failed: {}", e.getMessage(), e);
            return;
        }

        try {
            departmentSummary.refreshMaterializedView();
            log.info("Warm-up: department_compliance_summary MV refreshed.");
        } catch (Exception e) {
            log.warn("MV refresh after warm-up failed (non-fatal): {}", e.getMessage());
        }
    }
}
