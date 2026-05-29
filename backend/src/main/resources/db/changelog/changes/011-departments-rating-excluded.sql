--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 011: Прапорець виключення кафедри з рейтингу.
-- ──────────────────────────────────────────────────────────────
-- Деякі НПП ведуть пари, але адміністративно віднесені до
-- "віртуальних" кафедр управління (number=0, number=888) — вони
-- не мають брати участь у рейтингу.
--
-- За замовчуванням всі кафедри РАХУЮТЬСЯ у рейтингу (false).
-- Адмін через UI може помітити будь-яку кафедру як виключену.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:011-add-rating-excluded-column splitStatements:true
--comment: Додає колонку rating_excluded з default=false
ALTER TABLE departments
    ADD COLUMN rating_excluded BOOLEAN NOT NULL DEFAULT FALSE;

--changeset teacherlicence:011-mark-virtual-departments splitStatements:true
--comment: Помічаємо віртуальні кафедри (0, 888) як виключені з рейтингу
UPDATE departments
SET rating_excluded = TRUE
WHERE number IN ('0', '888');
