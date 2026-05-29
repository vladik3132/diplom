--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 005: academic_degrees — список наукових ступенів викладача.
-- Backfill з flat-полів Teacher (academic_degree, dissertation_topic,
-- dissertation_speciality, degree_diploma, degree_diploma_date).
-- Idempotent (IF NOT EXISTS).
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:005-academic-degrees-table splitStatements:false
--comment: Create academic_degrees table
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'academic_degrees'
    ) THEN
        CREATE TABLE academic_degrees (
            id                 BIGSERIAL PRIMARY KEY,
            teacher_id         BIGINT NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
            degree             VARCHAR(255),
            speciality         TEXT,
            dissertation_topic TEXT,
            diploma            VARCHAR(255),
            diploma_date       DATE,
            issued_by          VARCHAR(500)
        );
        CREATE INDEX idx_academic_degrees_teacher ON academic_degrees(teacher_id);
    END IF;
END $$;

--changeset teacherlicence:005-academic-degrees-backfill splitStatements:false
--comment: Backfill from teachers.academic_degree (one row per existing teacher with degree)
DO $$
BEGIN
    -- Створюємо запис тільки для тих викладачів, у кого academic_degree непорожній
    -- І для кого ще НЕМАЄ запису у academic_degrees (idempotent повторний прогон).
    INSERT INTO academic_degrees (
        teacher_id, degree, speciality, dissertation_topic, diploma, diploma_date
    )
    SELECT
        t.id,
        t.academic_degree,
        t.dissertation_speciality,
        t.dissertation_topic,
        t.degree_diploma,
        t.degree_diploma_date
    FROM teachers t
    WHERE t.academic_degree IS NOT NULL
      AND length(trim(t.academic_degree)) > 0
      AND NOT EXISTS (
          SELECT 1 FROM academic_degrees a WHERE a.teacher_id = t.id
      );
END $$;
