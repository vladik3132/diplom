--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 015: Видалення застарілої колонки teachers.position.
-- ──────────────────────────────────────────────────────────────
-- Після завершення рефакторингу (Стадії 1-4) усі читачі переключені
-- на StaffPosition (через TeacherPositionService.getEffectivePosition).
-- Колонка teachers.position більше не використовується як джерело правди.
--
-- На рівні Java entity поле залишається як @Transient — для зворотної
-- сумісності з імпортерами, які встановлюють position у пам'яті як
-- проміжне значення перед викликом ensureStaffPosition().
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:015-drop-teacher-position-column splitStatements:true
--comment: Drops the legacy teachers.position column
ALTER TABLE teachers
    DROP COLUMN IF EXISTS position;
