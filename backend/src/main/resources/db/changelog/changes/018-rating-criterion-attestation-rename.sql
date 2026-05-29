--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 018: Рейтингові критерії атестаційної діяльності (пп.7).
-- ──────────────────────────────────────────────────────────────
-- Зміни в enum RatingCriterion:
--   - ONE_TIME_REVIEWER (5 балів) → REVIEWER (10 балів)
--   - COUNCIL_MEMBER (10 балів) → COUNCIL_MEMBER (5 балів)
--   - OFFICIAL_OPPONENT (20 балів) — без змін
--   - + COUNCIL_CHAIR (20 балів) — новий критерій для ролі CHAIR
--
-- Зміна ваг — конфігурація на стороні Java enum; SQL-міграція потрібна
-- лише для:
--  1) перейменування існуючих записів teacher_ratings;
--  2) оновлення CHECK constraint.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:018-drop-criterion-check splitStatements:true
--comment: Видаляємо старий CHECK constraint на teacher_ratings.criterion
ALTER TABLE teacher_ratings DROP CONSTRAINT IF EXISTS teacher_ratings_criterion_check;

--changeset teacherlicence:018-rename-one-time-reviewer splitStatements:true
--comment: ONE_TIME_REVIEWER → REVIEWER у вже нарахованих рейтингах
UPDATE teacher_ratings SET criterion = 'REVIEWER' WHERE criterion = 'ONE_TIME_REVIEWER';

--changeset teacherlicence:018-add-new-criterion-check splitStatements:true
--comment: Новий CHECK constraint з оновленим переліком критеріїв
ALTER TABLE teacher_ratings
    ADD CONSTRAINT teacher_ratings_criterion_check
    CHECK (criterion IN (
        'SCOPUS_ARTICLE','WOS_ARTICLE','CATEGORY_A_ARTICLE','CATEGORY_B_ARTICLE',
        'PATENT','DECLARATIVE_PATENT','COPYRIGHT',
        'TEXTBOOK','MONOGRAPH','STUDY_GUIDE',
        'PRACTICUM','METHODICAL_GUIDELINES','E_COURSE','LECTURE_NOTES',
        'DOCTORAL_DEFENSE','PHD_DEFENSE','DOCTORAL_SUPERVISION','PHD_SUPERVISION',
        'OFFICIAL_OPPONENT','REVIEWER','COUNCIL_CHAIR','COUNCIL_MEMBER',
        'EDITORIAL_BOARD','EXPERT_COUNCIL','INTERNATIONAL_PROJECT','SCIENTIFIC_CONSULTING',
        'APPROBATION_SCOPUS','APPROBATION_INTERNATIONAL','APPROBATION_DOMESTIC',
        'FOREIGN_LANGUAGE_TEACHING',
        'OLYMPIAD_INTERNATIONAL_PRIZE','OLYMPIAD_NATIONAL_PRIZE','SCIENCE_GROUP_LEADER',
        'COMBAT_VETERAN','COMBAT_EXPERIENCE','UN_PEACEKEEPING','NATO_EXERCISES',
        'PROFESSIONAL_ASSOCIATION',
        'PROFESSOR_TITLE','DOCENT_TITLE','QUALIFICATION_CREDIT','FOREIGN_INTERNSHIP',
        'OPEN_LESSON','METHODOLOGICAL_EXPERIMENT','ACADEMIC_MOBILITY',
        'WORKING_GROUP_CHAIR','WORKING_GROUP_MEMBER',
        'SMR_LEVEL_1','SMR_LEVEL_2','SMR_LEVEL_3',
        'MILITARY_COURSE_3_6','MILITARY_COURSE_6_10','MILITARY_COURSE_10_PLUS',
        'MILITARY_ED_OPERATIONAL','MILITARY_ED_STRATEGIC'
    ));
