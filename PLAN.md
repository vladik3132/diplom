# TeacherLicence — План розробки

## Завершені етапи

### Етап 1: Ядро системи (Backend + Frontend)
- [x] Spring Boot 3.5.0 + Java 21 + Maven
- [x] React 19 + TypeScript + Vite + Ant Design 5
- [x] JWT-автентифікація (jjwt 0.12.6) + Spring Security 6
- [x] H2 (dev) / PostgreSQL (prod) + Flyway міграції
- [x] Моделі: User, Teacher, Faculty, Department, CareerRecord, LanguageSkill
- [x] CRUD контролери: TeacherController, UserController, AuthController
- [x] Фронтенд: LoginPage, DashboardPage, TeacherListPage, TeacherProfilePage
- [x] AppLayout з боковим меню та JWT-авторизацією
- [x] DataSeeder — 3 тестових юзери (admin, head, teacher)

### Етап 2: Публікації
- [x] Модель Publication (SCOPUS, WOS, FAKHOVE, THESES, CONFERENCE, TEXTBOOK, MONOGRAPH, OTHER)
- [x] PublicationService + PublicationController (/api/publications)
- [x] PublicationsPage з таблицею, фільтром по типу та кнопкою "Додати"

### Етап 3: Досягнення та відповідність п.38
- [x] Модель Achievement з 20 типами AchievementType
- [x] ComplianceService — логіка: >=4 типів = COMPLIANT, 3 = WARNING, <3 = NON_COMPLIANT
- [x] AchievementsPage, ComplianceReportPage
- [x] ComplianceBadge компонент

### Етап 4: Підвищення кваліфікації
- [x] Модель QualificationImprovement
- [x] QualificationService + QualificationController
- [x] QualificationPage

### Етап 5: Дисципліни
- [x] Моделі: Discipline, TeacherDiscipline, DisciplineDocument
- [x] DisciplineService + DisciplineController
- [x] DisciplineListPage

### Етап 6: Редакційно-видавничі плани
- [x] Моделі: EditorialPlan, EditorialPlanItem
- [x] EditorialService + EditorialController
- [x] EditorialPlanPage

### Етап 7: Діаграма Ганта
- [x] Модель GanttEvent
- [x] GanttService + GanttController
- [x] GanttPage

### Етап 8: Конструктор DOCX
- [x] Модель DocxTemplate (JSON template config)
- [x] DocxGeneratorService (Apache POI) — блоки: text, table_publications, table_achievements, table_qualifications, page_break
- [x] DocxController + DocxConstructorPage (блоковий конструктор)

### Етап 9: AI-асистент
- [x] Spring AI + OpenAI-compatible starter (Mistral API як фолбек)
- [x] AiAssistantService (чат), AchievementClassifierService (класифікація п.38)
- [x] AiController + AiAssistantPage
- [x] @ConditionalOnProperty(ai.enabled=true)

### Етап 10: Імпорт даних (DOCX парсинг)
- [x] DataImportService — парсинг DOCX таблиць (кадрове забезпечення кафедри)
- [x] DataImportController (POST /api/import/docx)
- [x] Парсинг 11 колонок: ПІБ, посада, дисципліни, освіта, наука, кар'єра, бойовий досвід, мови, кваліфікації, ідентифікатори
- [x] Detail rows: п.38 пп.1-20 секції → Achievement + Publication entities
- [x] 10 стратегій extractJournalName() (каскад regex)
- [x] Діаграма алгоритму: `DOCX_PARSING_ALGORITHM.excalidraw`

### Етап 11: Нотифікації
- [x] Модель Notification + WebSocket (STOMP + SockJS)
- [x] NotificationsPage

### Етап 12: Виправлення багів
- [x] AuthController — додано try-catch для 403 -> 401 з JSON-повідомленням
- [x] TeacherListPage — додано модалку "Додати викладача" з формою
- [x] application.yml — виправлено друкарську помилку `truen` -> `true`
- [x] Vite proxy — змінено на port 8081
- [x] AiAssistantPage — оновлено текст помилки

### Етап 13: Seed Data (тестові дані)
- [x] DataSeeder: 3 факультети, 6 кафедр, 10 викладачів
- [x] 26 досягнень, 10 публікацій, 12 дисциплін, 6 кваліфікацій
- [x] Прив'язка Teacher до User (admin, head, teacher)

### Етап 14: AI-парсер публікацій
- [x] PublicationAiParser — batch-парсинг через Mistral (10 записів за виклик)
- [x] Інтеграція AI + regex fallback у parsePublicationEntries()
- [x] Витяг: title, coauthors, pages, volume (AI) + type, year, DOI, URL, journal (regex)
- [x] @ConditionalOnProperty(ai.enabled=true) + optional injection

### Етап 15: Покращення парсера DOCX
- [x] PublicationType.THESES — окремий тип для тез доповідей (виокремлено з CONFERENCE)
- [x] Frontend: label "Тези доповідей" + volcano color tag
- [x] detectPublicationType(): "тези" → THESES (до CONFERENCE у пріоритеті)
- [x] Фікс Unicode: `[а-щ]` → `[а-яіїєґ]` (включає і, ї, є, ґ для підпунктів)
- [x] Фікс parseQualifications() split regex: `\s` → `\s*` + letter lookahead (2.Certificate без пробілу)
- [x] Фікс parseOneQualification() крок 1b: витяг title з лапок в рядку організації
- [x] Очищення org: "Certificate of (The)" prefix, "for participating..." suffix
- [x] Фікс cert number regex: `[\s-]` → `[ -]` (не захоплює через \n)
- [x] Перевірено на реальних даних: Бовда (18 кваліфікацій), Редзюк (3 кваліфікації)
- [x] Результат імпорту: 2 викладачі, 39 публікацій (20 THESES), 21 кваліфікація, 15 досягнень

---

## Поточні завдання

### Етап 16: Перевірка CRUD end-to-end
- [ ] Створення/редагування/видалення викладача
- [ ] Створення публікацій, досягнень, кваліфікацій
- [ ] Перевірка всіх "Додати" кнопок на кожній сторінці
- [ ] Перевірка AI-чату з Mistral

### Етап 17: Фільтрація та пошук
- [ ] Пошук на сторінках списків (викладачі, публікації, досягнення)
- [ ] Фільтри по кафедрі, статусу, типу
- [ ] Сортування таблиць

### Етап 18: Роль-based доступ
- [ ] Прив'язка Teacher <-> User (teacherId в User)
- [ ] TEACHER бачить тільки свої дані
- [ ] HEAD_OF_DEPARTMENT бачить дані своєї кафедри
- [ ] ADMIN бачить все
- [ ] Приховати адмін-функції для TEACHER ролі

### Етап 19: Валідація форм
- [ ] Фронтенд-валідація (обов'язкові поля, формати email, ORCID)
- [ ] Бекенд-валідація (@Valid, DTO constraints)
- [ ] Повідомлення про помилки користувачу

### Етап 20: Docker та деплой
- [ ] Dockerfile для backend (multi-stage build)
- [ ] Dockerfile для frontend (nginx)
- [ ] docker-compose.yml (backend + frontend + PostgreSQL)
- [ ] Nginx конфігурація (reverse proxy)
- [ ] Production application.yml

### Етап 21: Git та документація
- [ ] git init + .gitignore
- [ ] Initial commit
- [ ] README.md

---

## Архітектура імпорту DOCX

Детальна блок-схема алгоритму: **`DOCX_PARSING_ALGORITHM.excalidraw`**

### Огляд pipeline:
```
DOCX File (Apache POI)
    │
    ├── Phase 1: Classify rows (>= 10 cells = data, else = detail)
    │
    ├── Phase 2: parseDataRow() — 11 columns
    │   ├── Col 0: parseTeacherBasicInfo() — ПІБ, рік, звання, досвід
    │   ├── Col 1: position (direct) + employmentType
    │   ├── Col 2: parseDisciplines() — find-or-create + link
    │   ├── Col 3: parseEducation() — ВНЗ, спеціальність, диплом
    │   ├── Col 4: parseScientific() — ступінь, звання, дисертація
    │   ├── Col 5: parseCareerRecords() — по рядках, дати, організації
    │   ├── Col 6: combatVeteran (direct)
    │   ├── Col 7: parseLanguageSkills() — мова, рівень (СМР/CEFR), сертифікат
    │   ├── Col 8: parseQualifications() — нумеровані + підпункти а)б)в)
    │   └── Col 9: parseIdentifiers() — ORCID, email, Scopus, WoS, Scholar
    │
    └── Phase 3: Detail rows (pairs: name label + achievement text)
        ├── matchTeacherByNameLabel() — матч по прізвищу
        ├── splitBySections() — regex п.38 пп.(1-20)
        └── For each section:
            ├── Create Achievement entity
            └── пп.1/3/12 → parsePublicationEntries()
                ├── Phase 1: Split, detect type/year/DOI/URL/journal
                ├── Phase 2: AI batch (Mistral) OR regex fallback
                └── Phase 3: Save publications
```

### Типи публікацій (PublicationType):
| Тип | Колір тегу | Ключові слова |
|-----|-----------|---------------|
| SCOPUS | blue | scopus |
| WOS | purple | web of science, wos |
| FAKHOVE | green | фахов, вісник, збірник наукових |
| THESES | volcano | тези, тез доповідей |
| CONFERENCE | orange | конференц, proceedings, conference |
| TEXTBOOK | cyan | посібник, підручник |
| MONOGRAPH | magenta | монографія, monograph |
| OTHER | default | все інше |

---

## Технічний стек
- **Backend**: Spring Boot 3.5.0, Java 21, Maven, Spring Security 6, Spring AI (OpenAI-compatible)
- **Frontend**: React 19, TypeScript, Vite, Ant Design 5.x
- **DB**: H2 (dev), PostgreSQL (prod), Flyway
- **Auth**: JWT (jjwt 0.12.6), BCrypt
- **AI**: Mistral API (через OpenAI-compatible starter), PublicationAiParser (batch 10/call)
- **Docs**: Apache POI (DOCX generation + parsing)
- **Realtime**: WebSocket (STOMP + SockJS)

## Облікові записи (dev)
| Роль | Email | Пароль |
|------|-------|--------|
| ADMIN | admin@university.edu.ua | admin |
| HEAD_OF_DEPARTMENT | head@university.edu.ua | head |
| TEACHER | teacher@university.edu.ua | teacher |
