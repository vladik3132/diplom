--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 017: Рефактор ролей атестаційної діяльності (пп.7).
-- ──────────────────────────────────────────────────────────────
-- Старі ролі: OPPONENT, COUNCIL_MEMBER, SPORADIC_COUNCIL.
-- Нові ролі:  OPPONENT, REVIEWER, CHAIR, COUNCIL_MEMBER.
--
-- COUNCIL_MEMBER тепер ОДНОЗНАЧНО означає "Член постійної спецради".
-- SPORADIC_COUNCIL → REVIEWER (узгоджено з користувачем як найпоширеніша
-- разова роль).
--
-- Поле count видалено: 1 запис = 1 факт.
-- Додано date_from / date_to для періоду членства у постійній спецраді.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:017-drop-old-role-check splitStatements:true
--comment: Видаляємо старий CHECK constraint на attestation_activity.role
ALTER TABLE attestation_activity DROP CONSTRAINT IF EXISTS attestation_activity_role_check;

--changeset teacherlicence:017-migrate-sporadic-to-reviewer splitStatements:true
--comment: SPORADIC_COUNCIL → REVIEWER (нова назва ролі)
UPDATE attestation_activity SET role = 'REVIEWER' WHERE role = 'SPORADIC_COUNCIL';

--changeset teacherlicence:017-add-new-role-check splitStatements:true
--comment: Новий CHECK constraint з новими значеннями ролей
ALTER TABLE attestation_activity
    ADD CONSTRAINT attestation_activity_role_check
    CHECK (role IN ('OPPONENT', 'REVIEWER', 'CHAIR', 'COUNCIL_MEMBER'));

--changeset teacherlicence:017-drop-count-column splitStatements:true
--comment: Видаляємо застаріле поле count (1 запис = 1 факт)
ALTER TABLE attestation_activity DROP COLUMN IF EXISTS count;

--changeset teacherlicence:017-add-period-columns splitStatements:true
--comment: Поля dateFrom/dateTo для періоду членства у постійній спецраді
ALTER TABLE attestation_activity ADD COLUMN date_from DATE;
ALTER TABLE attestation_activity ADD COLUMN date_to DATE;
