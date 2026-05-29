--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 019: Видалення рейтингового критерію COUNCIL_CHAIR.
-- ──────────────────────────────────────────────────────────────
-- Згідно з керівними документами голова разової спецради
-- НЕ враховується в рейтингу. Роль CHAIR залишається в
-- attestation_activity (для перевірки пп.7), але рейтингу не дає.
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:019-drop-criterion-check splitStatements:true
--comment: Видаляємо CHECK constraint (зробимо новий без COUNCIL_CHAIR)
ALTER TABLE teacher_ratings DROP CONSTRAINT IF EXISTS teacher_ratings_criterion_check;

--changeset teacherlicence:019-delete-council-chair-rows splitStatements:true
--comment: Видаляємо вже нараховані рейтингові записи з COUNCIL_CHAIR (якщо такі є)
DELETE FROM teacher_ratings WHERE criterion = 'COUNCIL_CHAIR';

--changeset teacherlicence:019-add-criterion-check-without-chair splitStatements:true
--comment: Новий CHECK constraint без COUNCIL_CHAIR
ALTER TABLE teacher_ratings
    ADD CONSTRAINT teacher_ratings_criterion_check
    CHECK (criterion IN (
        'SCOPUS_ARTICLE','WOS_ARTICLE','CATEGORY_A_ARTICLE','CATEGORY_B_ARTICLE',
        'PATENT','DECLARATIVE_PATENT','COPYRIGHT',
        'TEXTBOOK','MONOGRAPH','STUDY_GUIDE',
        'PRACTICUM','METHODICAL_GUIDELINES','E_COURSE','LECTURE_NOTES',
        'DOCTORAL_DEFENSE','PHD_DEFENSE','DOCTORAL_SUPERVISION','PHD_SUPERVISION',
        'OFFICIAL_OPPONENT','REVIEWER','COUNCIL_MEMBER',
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
