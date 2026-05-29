--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 004: Add publication_date column to publications.
-- Backfill з publication_year (year-01-01).
-- Idempotent — IF NOT EXISTS.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:004-publication-date splitStatements:false
--comment: Add publication_date column + backfill from publication_year
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'publications')
       AND NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_name = 'publications' AND column_name = 'publication_date'
       ) THEN
        ALTER TABLE publications ADD COLUMN publication_date date;

        -- Backfill: для всіх існуючих рядків з publication_year — виставити дату YYYY-01-01.
        UPDATE publications
           SET publication_date = make_date(publication_year, 1, 1)
         WHERE publication_year IS NOT NULL
           AND publication_date IS NULL;
    END IF;
END $$;

--changeset teacherlicence:004-publication-date-index splitStatements:true
--comment: Index for date-based filtering (compliance 5-years, rating periods)
CREATE INDEX IF NOT EXISTS idx_publications_publication_date
    ON publications (publication_date DESC);
