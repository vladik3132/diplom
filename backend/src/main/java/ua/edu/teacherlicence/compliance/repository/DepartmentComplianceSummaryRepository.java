package ua.edu.teacherlicence.compliance.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.compliance.model.DepartmentComplianceSummary;

/**
 * Repository для materialized view {@code department_compliance_summary}.
 * Містить метод явного REFRESH — викликається після змін у teacher_compliance_cache.
 */
@Repository
public interface DepartmentComplianceSummaryRepository
        extends JpaRepository<DepartmentComplianceSummary, Long>, DepartmentComplianceSummaryRepositoryCustom {
}

/**
 * Custom методи — щоб REFRESH MATERIALIZED VIEW мати як кастомну операцію.
 */
interface DepartmentComplianceSummaryRepositoryCustom {
    void refreshMaterializedView();
}

@Repository
class DepartmentComplianceSummaryRepositoryCustomImpl implements DepartmentComplianceSummaryRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    /**
     * REFRESH MATERIALIZED VIEW CONCURRENTLY — неблокуюча рефрешшка.
     * Потребує UNIQUE INDEX на view (створено у V3 міграції).
     * Виконується у власній транзакції (REQUIRES_NEW) щоб не тягнути довгі tx.
     */
    @Override
    @Transactional
    public void refreshMaterializedView() {
        em.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY department_compliance_summary")
                .executeUpdate();
    }
}
