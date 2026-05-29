--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 006: academic_titles — список вчених звань викладача.
-- Один викладач може мати декілька (Доцент кафедри X → Професор кафедри Y).
-- Backfill з flat-полів teachers.academic_title / title_attestat / title_attestat_date.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:006-academic-titles-table splitStatements:false
--comment: Create academic_titles table + backfill from Teacher flat fields
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'academic_titles') THEN
        CREATE TABLE academic_titles (
            id            BIGSERIAL PRIMARY KEY,
            teacher_id    BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
            title_name    TEXT,
            attestat      VARCHAR(255),
            attestat_date DATE,
            issued_by     VARCHAR(255)
        );

        CREATE INDEX idx_academic_titles_teacher ON academic_titles(teacher_id);

        -- Backfill з flat-полів teachers — один рядок на викладача якщо у нього є вчене звання.
        INSERT INTO academic_titles (teacher_id, title_name, attestat, attestat_date)
        SELECT t.id, t.academic_title, t.title_attestat, t.title_attestat_date
          FROM teachers t
         WHERE COALESCE(NULLIF(TRIM(t.academic_title), ''), NULLIF(TRIM(t.title_attestat), '')) IS NOT NULL
            OR t.title_attestat_date IS NOT NULL;
    END IF;
END $$;
