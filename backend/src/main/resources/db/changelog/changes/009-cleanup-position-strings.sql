--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 009: Очищення teachers.position від сміття після DOCX-імпорту.
-- ──────────────────────────────────────────────────────────────
-- Під час імпорту з DOCX/Excel позиції зберігалися у форматі:
--   "Професор кафедри комп'ютерних наук та інтелектуальних технологій (основна)"
-- замість чистого "Професор".
--
-- Тепер назва кафедри підставляється динамічно через POSITION_FULL,
-- а тип зайнятості зберігається в окремому полі employmentType.
-- "кафедри X..." суфікс і "(основна)"/"(сумісник)" — застарілий баласт.
--
-- Зберігаються без змін посади, де "кафедри" є частиною самої назви:
--   - "Начальник кафедри" (включно з "— професор", "— доцент" тощо)
--   - "Заступник начальника кафедри"
--   - "Завідувач кафедри"
-- ══════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────────
-- 1. Прибрати трейлінгові "(основна)" / "(сумісник)" і подібні дужкові суфікси
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:009-cleanup-position-trailing-parens splitStatements:true
--comment: Strip trailing "(основна)" / "(сумісник)" / "(...)" from teachers.position
UPDATE teachers
SET position = TRIM(REGEXP_REPLACE(position, '[[:space:]]*\([^)]*\)[[:space:]]*$', ''))
WHERE position IS NOT NULL
  AND position ~ '\([^)]*\)[[:space:]]*$';

-- ──────────────────────────────────────────────────────────────
-- 2. Видалити "кафедри ..." суфікс з посад на кшталт "Професор/Доцент/Викладач".
--    Зберегти "Начальник кафедри / Заступник начальника кафедри / Завідувач кафедри",
--    бо "кафедри" там — частина назви посади, а не баласт.
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:009-cleanup-position-kafedra-suffix splitStatements:true
--comment: Strip "кафедри X..." suffix from non-leadership positions (case-insensitive)
UPDATE teachers
SET position = TRIM(REGEXP_REPLACE(position, '[[:space:]]+кафедри.*$', '', 'i'))
WHERE position IS NOT NULL
  AND position ~* '[[:space:]]+кафедри'
  AND LOWER(position) NOT LIKE 'начальник кафедри%'
  AND LOWER(position) NOT LIKE 'заступник начальника кафедри%'
  AND LOWER(position) NOT LIKE 'завідувач кафедри%';

-- ──────────────────────────────────────────────────────────────
-- 3. Повторно прибрати дужкові суфікси для випадків,
--    коли вони залишилися ПЕРЕД "кафедри" (рідкісно, але можливо).
--    Напр. "Професор (доктор) кафедри X" → крок 2 → "Професор (доктор)" → крок 3 → "Професор".
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:009-cleanup-position-trailing-parens-2 splitStatements:true
--comment: Re-strip parens that may now be trailing after step 2
UPDATE teachers
SET position = TRIM(REGEXP_REPLACE(position, '[[:space:]]*\([^)]*\)[[:space:]]*$', ''))
WHERE position IS NOT NULL
  AND position ~ '\([^)]*\)[[:space:]]*$';

-- ──────────────────────────────────────────────────────────────
-- 4. Прибрати множинні пробіли всередині рядка (на випадок артефактів regex)
-- ──────────────────────────────────────────────────────────────
--changeset teacherlicence:009-cleanup-position-spaces splitStatements:true
--comment: Collapse multiple spaces to one in teachers.position
UPDATE teachers
SET position = TRIM(REGEXP_REPLACE(position, '[[:space:]]+', ' ', 'g'))
WHERE position IS NOT NULL
  AND position ~ '[[:space:]]{2,}';
