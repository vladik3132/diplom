--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 010: Нормалізація керівних посад до канонічної форми.
-- ──────────────────────────────────────────────────────────────
-- Міграція 009 пропускала рядки, що починаються з "Начальник кафедри"
-- / "Заступник начальника кафедри" / "Завідувач кафедри" — тому в них
-- лишилась повна назва кафедри:
--   "Начальник кафедри комп'ютерних наук" → лишилось як є.
--
-- А мало стати: "Начальник кафедри" (канонічна форма, без назви кафедри).
-- Назва кафедри додається динамічно через POSITION_FULL placeholder.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:010-normalize-head-of-department splitStatements:true
--comment: "Начальник кафедри X..." → "Начальник кафедри"
UPDATE teachers
SET position = 'Начальник кафедри'
WHERE position IS NOT NULL
  AND LOWER(position) ~ '^начальник кафедри'
  AND position <> 'Начальник кафедри';

--changeset teacherlicence:010-normalize-deputy-head splitStatements:true
--comment: "Заступник начальника кафедри X..." → "Заступник начальника кафедри"
UPDATE teachers
SET position = 'Заступник начальника кафедри'
WHERE position IS NOT NULL
  AND LOWER(position) ~ '^заступник начальника кафедри'
  AND position <> 'Заступник начальника кафедри';

--changeset teacherlicence:010-normalize-zaviduvach splitStatements:true
--comment: "Завідувач кафедри X..." → "Завідувач кафедри"
UPDATE teachers
SET position = 'Завідувач кафедри'
WHERE position IS NOT NULL
  AND LOWER(position) ~ '^завідувач кафедри'
  AND position <> 'Завідувач кафедри';
