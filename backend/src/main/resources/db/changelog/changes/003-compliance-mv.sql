--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 003: Compliance materialized view.
-- Cache-таблиці (teacher_compliance_cache, teacher_discipline_match_cache,
-- teacher_program_match_cache) створюються у 001-init-schema як звичайні
-- JPA-ентіті. Тут — лише MATERIALIZED VIEW з агрегатами п.35/п.38.
--
-- MV мапиться на entity {@link DepartmentComplianceSummary} з @Subselect,
-- тому Hibernate НЕ намагається керувати її DDL.
-- ══════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────────
-- Додаткові індекси на cache-таблиці (PK уже створив Hibernate).
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:003-compliance-indexes splitStatements:true
--comment: Secondary indexes on compliance cache tables
CREATE INDEX IF NOT EXISTS idx_tcc_status           ON teacher_compliance_cache (status);
CREATE INDEX IF NOT EXISTS idx_tcc_updated          ON teacher_compliance_cache (updated_at);
CREATE INDEX IF NOT EXISTS idx_tdmc_teacher         ON teacher_discipline_match_cache (teacher_id);
CREATE INDEX IF NOT EXISTS idx_tdmc_discipline      ON teacher_discipline_match_cache (discipline_id);
CREATE INDEX IF NOT EXISTS idx_tdmc_fully_compliant ON teacher_discipline_match_cache (fully_compliant);
CREATE INDEX IF NOT EXISTS idx_tpmc_teacher         ON teacher_program_match_cache (teacher_id);
CREATE INDEX IF NOT EXISTS idx_tpmc_program         ON teacher_program_match_cache (program_id);

-- ──────────────────────────────────────────────────────────────
-- Materialized view: department_compliance_summary
-- Оновлюється через REFRESH MATERIALIZED VIEW CONCURRENTLY у DepartmentSummaryService.
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:003-department-compliance-summary splitStatements:false
--comment: Materialized view aggregating п.35 and п.38 metrics per department
DROP MATERIALIZED VIEW IF EXISTS department_compliance_summary;

CREATE MATERIALIZED VIEW department_compliance_summary AS
SELECT
    d.id           AS department_id,
    d.number       AS department_number,
    d.name         AS department_name,
    f.name         AS faculty_name,

    COUNT(t.id)                                                 AS total_teachers,
    COUNT(t.id) FILTER (WHERE t.employment_type = 'MAIN')       AS main_employment_teachers,
    COUNT(t.id) FILTER (WHERE t.employment_type = 'PART_TIME')  AS part_time_teachers,

    -- П.35: % на основній роботі зі ступенем/званням
    COUNT(t.id) FILTER (
        WHERE t.employment_type = 'MAIN'
          AND (t.academic_degree IS NOT NULL OR t.academic_title IS NOT NULL)
    ) AS with_degree_and_main_count,

    CASE
        WHEN COUNT(t.id) FILTER (WHERE t.employment_type = 'MAIN') > 0
        THEN ROUND(
            100.0 * COUNT(t.id) FILTER (
                WHERE t.employment_type = 'MAIN'
                  AND (t.academic_degree IS NOT NULL OR t.academic_title IS NOT NULL)
            )::numeric / COUNT(t.id) FILTER (WHERE t.employment_type = 'MAIN'),
            1
        )::double precision
        ELSE 0
    END AS with_degree_and_main_percent,

    COUNT(t.id) FILTER (
        WHERE LOWER(COALESCE(t.academic_degree, '')) LIKE '%доктор%'
           OR LOWER(COALESCE(t.academic_title, ''))  LIKE '%професор%'
    ) AS doctors_or_professors_count,

    CASE
        WHEN COUNT(t.id) > 0
        THEN ROUND(
            100.0 * COUNT(t.id) FILTER (
                WHERE LOWER(COALESCE(t.academic_degree, '')) LIKE '%доктор%'
                   OR LOWER(COALESCE(t.academic_title, ''))  LIKE '%професор%'
            )::numeric / COUNT(t.id),
            1
        )::double precision
        ELSE 0
    END AS doctors_or_professors_percent,

    COUNT(c.teacher_id) FILTER (WHERE c.status = 'COMPLIANT')     AS point38_compliant,
    COUNT(c.teacher_id) FILTER (WHERE c.status = 'WARNING')       AS point38_warning,
    COUNT(c.teacher_id) FILTER (WHERE c.status = 'NON_COMPLIANT') AS point38_non_compliant,
    COUNT(c.teacher_id) FILTER (WHERE c.status = 'EXEMPT')        AS point38_exempt,

    CURRENT_TIMESTAMP AS refreshed_at
FROM departments d
LEFT JOIN faculties              f ON f.id            = d.faculty_id
LEFT JOIN teachers               t ON t.department_id = d.id
LEFT JOIN teacher_compliance_cache c ON c.teacher_id   = t.id
GROUP BY d.id, d.number, d.name, f.name;

--changeset teacherlicence:003-department-compliance-summary-uidx splitStatements:true
--comment: Unique index required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_dcs_department
    ON department_compliance_summary (department_id);
