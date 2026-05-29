--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 007: teacher_compliance_cache.title_matches_department
-- Boolean — хоча б одне вчене звання викладача відповідає напряму
-- кафедри (AI). Використовується у вимозі Закону про вищу освіту:
-- ≥3 особи зі ступенем АБО званням за напрямом кафедри.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:007-title-matches-department splitStatements:false
--comment: Add title_matches_department column with default FALSE
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'teacher_compliance_cache')
       AND NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_name = 'teacher_compliance_cache' AND column_name = 'title_matches_department'
       ) THEN
        ALTER TABLE teacher_compliance_cache
            ADD COLUMN title_matches_department BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;
