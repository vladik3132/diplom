package ua.edu.teacherlicence.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.compliance.model.DepartmentComplianceSummary;
import ua.edu.teacherlicence.compliance.repository.DepartmentComplianceSummaryRepository;

import java.util.List;
import java.util.Optional;

/**
 * Обслуговування materialized view {@code department_compliance_summary}.
 *
 * MV сама не оновлюється — треба викликати REFRESH MATERIALIZED VIEW CONCURRENTLY.
 * Це технічне оновлення (не бізнес "scheduled fallback") — триває < 1 сек,
 * запускається:
 *   - scheduled раз на 10 хв (фонова фіксація актуальності агрегатів)
 *   - після змін у teacher_compliance_cache (via event listener, debounced)
 *   - руками через admin endpoint /api/compliance/refresh-mv
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentSummaryService {

    private final DepartmentComplianceSummaryRepository repo;

    @Transactional(readOnly = true)
    public List<DepartmentComplianceSummary> findAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<DepartmentComplianceSummary> findById(Long departmentId) {
        return repo.findById(departmentId);
    }

    /** REFRESH MATERIALIZED VIEW CONCURRENTLY. Нon-blocking, не ексклюзивний. */
    public void refreshMaterializedView() {
        try {
            long t0 = System.currentTimeMillis();
            repo.refreshMaterializedView();
            log.debug("department_compliance_summary refreshed in {} ms", System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.warn("Failed to refresh department_compliance_summary MV: {}", e.getMessage());
        }
    }

    /**
     * Scheduled refresh раз на 10 хв — страхувальник на випадок пропущеного event-listener-а.
     * Це НЕ "scheduled fallback compliance" — це технічне оновлення одного MV,
     * яке обчислюється з persistent teacher_compliance_cache.
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void scheduledMvRefresh() {
        refreshMaterializedView();
    }
}
