--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 014: Bootstrap штатних позицій з Teacher.position.
-- ──────────────────────────────────────────────────────────────
-- Для викладачів у яких заповнено Teacher.position, але немає
-- жодного запису у staff_positions з його teacher_id — створюємо
-- штатну позицію автоматично:
--   positionTitle = teacher.position
--   department_id = teacher.department_id
--   orderNumber   = MAX(orderNumber по кафедрі) + 1 (NULL → 1)
--   rate          = 1.0 (placeholder; адмін відкоригує)
--   bootstrapped  = TRUE (індикатор "потребує перевірки")
--
-- Це створює базу для подальшого рефакторингу — після цієї міграції
-- кожен викладач, що має посаду, має відповідний запис у штатці.
--
-- Idempotency: WHERE NOT EXISTS гарантує що міграція не створить
-- дублів при повторному запуску.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:014-bootstrap-staff-positions splitStatements:true
--comment: Створює штатні позиції для викладачів у яких є position, але немає staff_position
INSERT INTO staff_positions (
    department_id,
    order_number,
    position_title,
    rate,
    teacher_id,
    bootstrapped
)
SELECT
    t.department_id,
    COALESCE(
        (SELECT MAX(sp.order_number) + 1
         FROM staff_positions sp
         WHERE sp.department_id = t.department_id),
        1
    ),
    t.position,
    1.0,
    t.id,
    TRUE
FROM teachers t
WHERE t.position IS NOT NULL
  AND t.position <> ''
  AND t.department_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM staff_positions sp
    WHERE sp.teacher_id = t.id
  );
