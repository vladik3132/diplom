--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 013: Прапорець "автоматично створено" для staff_positions.
-- ──────────────────────────────────────────────────────────────
-- Готує грунт для bootstrap-міграції 014, яка створить штатні
-- позиції для викладачів у яких є Teacher.position, але немає
-- запису у штатному розписі.
--
-- bootstrapped = true → рядок створено автоматично, потребує
-- перевірки/коригування адміністратором (rate, ШПК, тариф, ВОС).
-- При будь-якому ручному редагуванні через UI прапорець скидається
-- у false на стороні backend.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:013-add-staff-position-bootstrapped splitStatements:true
--comment: Додає колонку bootstrapped до staff_positions
ALTER TABLE staff_positions
    ADD COLUMN bootstrapped BOOLEAN NOT NULL DEFAULT FALSE;
