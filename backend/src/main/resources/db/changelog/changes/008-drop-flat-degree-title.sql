--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 008: Drop flat degree/title columns from teachers.
--
-- academic_degrees (005) and academic_titles (006) are now the
-- sole source of truth.  The 8 flat columns are redundant.
--
-- Steps:
--   1. Recreate department_compliance_summary MV — it referenced
--      t.academic_degree / t.academic_title (003).  Now uses
--      EXISTS sub-queries on academic_degrees / academic_titles.
--   2. Recreate the CONCURRENTLY-required unique index on the MV.
--   3. DROP the 8 flat columns from teachers.
-- ══════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────────
-- 1. Recreate materialized view without flat-column references
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:008-recreate-compliance-mv splitStatements:false
--comment: Recreate department_compliance_summary using EXISTS sub-queries instead of flat degree/title cols
DROP MATERIALIZED VIEW IF EXISTS department_compliance_summary CASCADE;

CREATE MATERIALIZED VIEW department_compliance_summary AS
SELECT
    d.id           AS department_id,
    d.number       AS department_number,
    d.name         AS department_name,
    f.name         AS faculty_name,

    COUNT(t.id)                                                 AS total_teachers,
    COUNT(t.id) FILTER (WHERE t.employment_type = 'MAIN')       AS main_employment_teachers,
    COUNT(t.id) FILTER (WHERE t.employment_type = 'PART_TIME')  AS part_time_teachers,

    -- П.35: % на основній роботі зі ступенем або вченим званням
    COUNT(t.id) FILTER (
        WHERE t.employment_type = 'MAIN'
          AND (
              EXISTS (SELECT 1 FROM academic_degrees ad  WHERE ad.teacher_id  = t.id)
              OR
              EXISTS (SELECT 1 FROM academic_titles  at2 WHERE at2.teacher_id = t.id)
          )
    ) AS with_degree_and_main_count,

    CASE
        WHEN COUNT(t.id) FILTER (WHERE t.employment_type = 'MAIN') > 0
        THEN ROUND(
            100.0 * COUNT(t.id) FILTER (
                WHERE t.employment_type = 'MAIN'
                  AND (
                      EXISTS (SELECT 1 FROM academic_degrees ad  WHERE ad.teacher_id  = t.id)
                      OR
                      EXISTS (SELECT 1 FROM academic_titles  at2 WHERE at2.teacher_id = t.id)
                  )
            )::numeric / COUNT(t.id) FILTER (WHERE t.employment_type = 'MAIN'),
            1
        )::double precision
        ELSE 0
    END AS with_degree_and_main_percent,

    -- П.38: доктори або професори
    COUNT(t.id) FILTER (
        WHERE EXISTS (
            SELECT 1 FROM academic_degrees ad
            WHERE ad.teacher_id = t.id
              AND LOWER(COALESCE(ad.degree, '')) LIKE '%доктор%'
        )
        OR EXISTS (
            SELECT 1 FROM academic_titles at2
            WHERE at2.teacher_id = t.id
              AND LOWER(COALESCE(at2.title_name, '')) LIKE '%професор%'
        )
    ) AS doctors_or_professors_count,

    CASE
        WHEN COUNT(t.id) > 0
        THEN ROUND(
            100.0 * COUNT(t.id) FILTER (
                WHERE EXISTS (
                    SELECT 1 FROM academic_degrees ad
                    WHERE ad.teacher_id = t.id
                      AND LOWER(COALESCE(ad.degree, '')) LIKE '%доктор%'
                )
                OR EXISTS (
                    SELECT 1 FROM academic_titles at2
                    WHERE at2.teacher_id = t.id
                      AND LOWER(COALESCE(at2.title_name, '')) LIKE '%професор%'
                )
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
LEFT JOIN faculties              f  ON f.id            = d.faculty_id
LEFT JOIN teachers               t  ON t.department_id = d.id
LEFT JOIN teacher_compliance_cache c ON c.teacher_id   = t.id
GROUP BY d.id, d.number, d.name, f.name;

-- ──────────────────────────────────────────────────────────────
-- 2. Unique index required for REFRESH MATERIALIZED VIEW CONCURRENTLY
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:008-recreate-compliance-mv-uidx splitStatements:true
--comment: Unique index on department_compliance_summary (department_id) for CONCURRENTLY refresh
CREATE UNIQUE INDEX IF NOT EXISTS idx_dcs_department
    ON department_compliance_summary (department_id);

-- ──────────────────────────────────────────────────────────────
-- 3. Drop the 8 flat columns from teachers
--    (data was already migrated to academic_degrees / academic_titles in 005/006)
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:008-drop-flat-degree-cols splitStatements:true
--comment: Drop redundant flat academic degree/title columns from teachers
ALTER TABLE teachers DROP COLUMN IF EXISTS academic_degree;
ALTER TABLE teachers DROP COLUMN IF EXISTS academic_title;
ALTER TABLE teachers DROP COLUMN IF EXISTS dissertation_topic;
ALTER TABLE teachers DROP COLUMN IF EXISTS dissertation_speciality;
ALTER TABLE teachers DROP COLUMN IF EXISTS degree_diploma;
ALTER TABLE teachers DROP COLUMN IF EXISTS degree_diploma_date;
ALTER TABLE teachers DROP COLUMN IF EXISTS title_attestat;
ALTER TABLE teachers DROP COLUMN IF EXISTS title_attestat_date;
