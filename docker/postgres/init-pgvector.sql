-- Автоматична ініціалізація розширення pgvector при ПЕРШОМУ створенні БД.
-- Виконується docker-entrypoint тільки якщо volume pgdata порожній (нова БД).
-- Якщо БД вже існує з попередніх запусків — цей скрипт НЕ виконається;
-- для existing volume треба виконати вручну:
--   docker compose exec postgres psql -U $DB_USERNAME -d $DB_NAME \
--     -c "CREATE EXTENSION IF NOT EXISTS vector;"
--
-- Потрібно для Spring AI RAG (vector_store table з pgvector).

CREATE EXTENSION IF NOT EXISTS vector;
