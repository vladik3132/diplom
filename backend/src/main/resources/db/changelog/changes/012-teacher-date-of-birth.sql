--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 012: Заміна birth_year (Integer) на date_of_birth (DATE).
-- ──────────────────────────────────────────────────────────────
-- Раніше дата народження зберігалася лише роком. Для практичних
-- потреб (вітання, кадровий облік, документообіг) потрібна
-- повна дата у форматі dd.mm.yyyy.
--
-- Міграція:
--   1) додає колонку date_of_birth DATE
--   2) переносить існуючі дані: birth_year → date_of_birth = 1 січня року
--      (якщо точну дату невідомо, ставиться 01.01.YEAR як placeholder)
--   3) видаляє стару колонку birth_year
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:012-add-date-of-birth-column splitStatements:true
--comment: Додаємо нову колонку date_of_birth
ALTER TABLE teachers
    ADD COLUMN date_of_birth DATE;

--changeset teacherlicence:012-backfill-date-of-birth splitStatements:true
--comment: Переносимо рік народження в дату (1 січня року як placeholder)
UPDATE teachers
SET date_of_birth = make_date(birth_year, 1, 1)
WHERE birth_year IS NOT NULL;

--changeset teacherlicence:012-drop-birth-year-column splitStatements:true
--comment: Видаляємо стару колонку birth_year
ALTER TABLE teachers
    DROP COLUMN birth_year;
