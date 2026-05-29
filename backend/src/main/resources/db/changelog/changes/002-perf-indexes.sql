--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════
-- 002: Performance indexes for 400 teachers + 100 concurrent users.
-- Безпечно виконується багаторазово (IF NOT EXISTS + перевірка таблиць).
-- ══════════════════════════════════════════════════════════════

--changeset teacherlicence:002-perf-indexes splitStatements:false
--comment: Hot-path indexes (teachers, publications, educations, achievements, etc.)
DO $$
BEGIN
    -- teachers — пошук за прізвищем (AI-tool findTeacherByLastName, TeacherListPage search)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'teachers') THEN
        CREATE INDEX IF NOT EXISTS idx_teachers_last_name_lower ON teachers (LOWER(last_name));
    END IF;

    -- publications — compliance п.1 (фахові/Scopus/WoS) + field_relevant + year-сорт
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'publications') THEN
        CREATE INDEX IF NOT EXISTS idx_publications_teacher_type_category
            ON publications (teacher_id, type, article_category);
        CREATE INDEX IF NOT EXISTS idx_publications_teacher_field_relevant
            ON publications (teacher_id, field_relevant);
        CREATE INDEX IF NOT EXISTS idx_publications_year_desc
            ON publications (publication_year DESC);
    END IF;

    -- lazy-join колекції по teacher_id
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'educations') THEN
        CREATE INDEX IF NOT EXISTS idx_educations_teacher ON educations (teacher_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'career_records') THEN
        CREATE INDEX IF NOT EXISTS idx_career_records_teacher ON career_records (teacher_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'language_skills') THEN
        CREATE INDEX IF NOT EXISTS idx_language_skills_teacher ON language_skills (teacher_id);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'military_educations') THEN
        CREATE INDEX IF NOT EXISTS idx_military_educations_teacher ON military_educations (teacher_id);
    END IF;

    -- users — логін
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users') THEN
        CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
    END IF;

    -- achievements — сортування за датою
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'achievements') THEN
        CREATE INDEX IF NOT EXISTS idx_achievements_date_achieved
            ON achievements (date_achieved DESC);
    END IF;

    -- teacher_disciplines — обернений FK на discipline_id
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'teacher_disciplines') THEN
        CREATE INDEX IF NOT EXISTS idx_teacher_disciplines_discipline
            ON teacher_disciplines (discipline_id);
    END IF;

    -- disciplines — обернений FK на educational_program_id
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'disciplines') THEN
        CREATE INDEX IF NOT EXISTS idx_disciplines_educational_program
            ON disciplines (educational_program_id);
    END IF;

    -- educational_programs — по кафедрі
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'educational_programs') THEN
        CREATE INDEX IF NOT EXISTS idx_educational_programs_department
            ON educational_programs (department_id);
    END IF;
END $$;
