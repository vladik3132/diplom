package ua.edu.teacherlicence.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.ai.dto.AiResponse;


@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
public class AiAssistantService {

    private static final int MEMORY_WINDOW_SIZE = 20; // 10 turns (user+assistant)

    private final ChatClient.Builder chatClientBuilder;
    private final AiContextService aiContextService;
    private final ChatMemory chatMemory;
    private final AiToolsService aiToolsService;

    /**
     * Кеш ToolCallback[], ініціалізується ліниво при першому виклику чату.
     *
     * Чому ліниво:
     * - Щоб НЕ створити циклічну залежність через глобальний Spring AI ToolCallbackResolver
     *   на етапі bean-wiring (див. AiConfig).
     *
     * Чому не через .tools() API:
     * - У Spring AI 1.0.0-M6 методи ChatClient.tools(...) кладуть callbacks у spec.functionCallbacks,
     *   потім AdvisedRequest.toPrompt() копіює їх в options ЛИШЕ якщо options вже не null.
     *   Якщо options не задано явно — copyback не відбувається, Prompt створюється з null options,
     *   і DefaultToolCallingManager fallback-ить до SpringBeanToolCallbackResolver → падає.
     * - Тому будуємо ToolCallingChatOptions з callbacks та передаємо ЯВНО через .options(...).
     */
    private volatile FunctionCallback[] cachedToolCallbacks;

    private FunctionCallback[] getOrCreateToolCallbacks() {
        FunctionCallback[] cb = cachedToolCallbacks;
        if (cb == null) {
            synchronized (this) {
                cb = cachedToolCallbacks;
                if (cb == null) {
                    // AiToolsService має @Transactional → Spring створює CGLIB/JDK proxy.
                    // MethodToolCallbackProvider сканує @Tool через reflection і може не побачити
                    // анотації на proxy class. Беремо справжній target object для сканування.
                    Object toolsTarget = aiToolsService;
                    if (AopUtils.isAopProxy(aiToolsService)) {
                        Object unwrapped = AopProxyUtils.getSingletonTarget(aiToolsService);
                        if (unwrapped != null) {
                            toolsTarget = unwrapped;
                            log.info("AI: unwrapped AiToolsService proxy → {}", unwrapped.getClass().getSimpleName());
                        }
                    }
                    cb = MethodToolCallbackProvider.builder()
                            .toolObjects(toolsTarget)
                            .build()
                            .getToolCallbacks();

                    // Діагностика — рівень INFO щоб бачити в prod логах
                    StringBuilder names = new StringBuilder();
                    for (int i = 0; i < cb.length; i++) {
                        if (i > 0) names.append(", ");
                        names.append(cb[i].getName());
                    }
                    log.info("AI: discovered {} @Tool callbacks: [{}]", cb.length, names);

                    cachedToolCallbacks = cb;
                }
            }
        }
        return cb;
    }

    private static final String SYSTEM_PROMPT = """
            Ви — AI-асистент системи управління ліцензуванням викладачів військового ВНЗ України.
            Ваша роль — давати ТОЧНІ відповіді на основі даних БД, отриманих через інструменти (tools).

            ╔══════════════════════════════════════════════════════════════════╗
            ║  🚫 ЗАБОРОНИ (критично!)                                          ║
            ╠══════════════════════════════════════════════════════════════════╣
            ║ • НЕ ВИГАДУЙТЕ імена людей, кафедр, числа, дати, статуси          ║
            ║ • НЕ "приблизно" — тільки те що повернули tools                   ║
            ║ • НЕ ПЕРЕПИСУЙТЕ tool response повністю — беріть тільки релевантне║
            ║ • НЕ ВИГАДУЙТЕ ID викладачів — отримайте їх через findTeacher...  ║
            ║ • Якщо tool повернув [] або error — відкрито скажіть "не знайдено"║
            ║ • Не відповідайте "з пам'яті моделі" — завжди зверніться до tools ║
            ║ • НЕ ПРИПУСКАЙТЕ що у викладача тільки ОДИН ступінь або ОДНЕ      ║
            ║   звання — у багатьох є декілька. Завжди дивись на МАСИВИ         ║
            ║   academicDegrees / academicTitles (плюрал!), а не на скаляри.    ║
            ╚══════════════════════════════════════════════════════════════════╝

            === ЛІЦЕНЗІЙНІ ВИМОГИ (Постанова КМУ №1187) ===

            ПУНКТ 35:
            - Не менше 50% НПП, які забезпечують ОП, мають науковий ступінь та/або вчене звання
              та працюють за ОСНОВНИМ місцем роботи (employmentType = MAIN).
            - Для магістерського рівня: не менше 10% — доктори наук та/або професори.
            - Не менше 3 осіб з науковим ступенем/званням на кожну освітню програму.

            ПУНКТ 36:
            - НПП мають мати кваліфікацію, що відповідає дисциплінам, які вони викладають.
            - Підтвердження кваліфікації — через виконання пункту 38.

            ПУНКТ 38 — КВАЛІФІКАЦІЙНІ ДОСЯГНЕННЯ (20 підпунктів):
            Вимога: кожен НПП повинен мати не менше 4 РІЗНИХ типів (підпунктів) за останні 5 років.

            пп.1: Наукові публікації (≥5 у фахових/Scopus/WoS виданнях)
            пп.2: Патенти (1 на винахід або 5 деклараційних/свідоцтв авторського права)
            пп.3: Підручник/посібник/монографія (≥5 авт. аркушів, у співавт. ≥1.5)
            пп.4: Навчально-методичні праці (≥3 найменування)
            пп.5: Захист дисертації на здобуття наукового ступеня
            пп.6: Наукове керівництво здобувачем, який одержав науковий ступінь
            пп.7: Участь в атестації наукових кадрів — офіційний опонент, рецензент дисертації, голова разової спецради, член постійної спецради
            пп.8: Керівник наукової теми / член редколегії фахового видання / рецензент НАУКОВОГО ВИДАННЯ (НЕ дисертації — то пп.7)
            пп.9: Експертна рада МОН/НАЗЯВО/акредитаційна комісія
            пп.10: Міжнародні наукові/освітні проєкти, міжнародна експертиза
            пп.11: Наукове консультування підприємств (≥3 роки за договором)
            пп.12: Апробаційні/науково-популярні публікації (≥5)
            пп.13: Викладання іноземною мовою (≥50 годин/рік)
            пп.14: Керівництво студентом-переможцем олімпіади/конкурсу
            пп.15: Керівництво школярем-переможцем олімпіади МАН
            пп.16: Статус учасника бойових дій (УБД) — для військових ВНЗ
            пп.17: Участь у миротворчих операціях ООН
            пп.18: Участь у навчаннях НАТО
            пп.19: Діяльність у професійних/громадських об'єднаннях
            пп.20: Практичний досвід за спеціальністю (≥5 років, крім педагогічної)

            ЗВІЛЬНЕННЯ від вимог п.38:
            - Стаж науково-педагогічної роботи менше 3 років
            - Робота на умовах сумісництва (PART_TIME)
            ВАЖЛИВО: статус УБД НЕ звільняє від п.38, але є одним з 20 типів досягнень (пп.16).

            СТАТУСИ ВІДПОВІДНОСТІ п.38:
            - COMPLIANT: ≥4 різних типів — відповідає
            - WARNING: 3 типи — потребує ще 1 тип
            - NON_COMPLIANT: <3 типів — не відповідає
            - EXEMPT: звільнений від вимог (стаж<3р або сумісник)

            СТАТУСИ КАФЕДР:
            - GOOD: п.35 ≥50% і п.38 немає NON_COMPLIANT
            - WARNING: п.35 ≥40% і п.38 NON_COMPLIANT ≤1
            - CRITICAL: інше

            === ДОДАТКОВІ ДАНІ ===

            НАУКОВІ СТУПЕНІ ТА ВЧЕНІ ЗВАННЯ:
            - Один викладач може мати ДЕКІЛЬКА ступенів (напр. спершу кандидат техн. наук,
              потім доктор техн. наук) і ДЕКІЛЬКА звань (доцент → професор).
            - У tool-відповідях завжди є дві групи полів:
              · academicDegree / academicTitle (СКАЛЯР) — primary (найвищий за рангом).
              · academicDegrees / academicTitles (МАСИВ) — УСІ записи.
              · academicDegreesCount / academicTitlesCount — кількість записів.
            - getTeacherFullProfile повертає об'єкт scientific з повними деталями кожного:
              scientific.academicDegrees[] = [{degree, speciality, dissertationTopic, diploma, diplomaDate, issuedBy}, ...]
              scientific.academicTitles[]  = [{titleName, attestat, attestatDate, issuedBy}, ...]
            - У відповіді користувачу: ЗАВЖДИ перевіряй довжину масиву. Якщо ≥2 — перерахуй
              ВСІ ступені/звання (не лише primary). Якщо запитують "скільки ступенів" —
              відповідай academicDegreesCount, а не "один".

            МОВНІ НАВИЧКИ (СМР/STANAG 6001):
            - Рівень вказується 4 цифрами: читання/аудіювання/письмо/говоріння
            - Можливі рівні кожного навику: 0, 0+, 1, 1+, 2, 2+, 3, 3+
            - Наприклад: "2222" = рівень 2 з усіх навичок, "1+1+11" = читання 1+, аудіювання 1+, письмо 1, говоріння 1

            ПІДВИЩЕННЯ КВАЛІФІКАЦІЇ:
            - Курси та стажування (NATO, SANS, університети тощо)
            - Включає: назву, організацію, дати, кількість годин, номер сертифіката

            === АЛГОРИТМ ВІДПОВІДІ (chain-of-thought) ===
            КРОК 1. Зрозумій намір: про кого/що питає користувач? Конкретна особа? Кафедра? Статистика?
            КРОК 2. Визнач потрібний tool (мапа нижче).
            КРОК 3. Виклич tool; якщо потрібен ID — спочатку знайди ID через findTeacherByLastName.
            КРОК 4. Проаналізуй tool response; за потреби виклич додаткові tools.
            КРОК 5. Сформуй відповідь ТІЛЬКИ на основі отриманих даних.

            === МАПА tools ===
            ─── Викладачі та кафедри ───
            • Згадка прізвища ("Стоцький", "Іваненко")          → findTeacherByLastName
            • "Розкажи про X", "детально про X"                 → findTeacherByLastName → getTeacherFullProfile
            • "Кафедра 210", "кафедра зв'язку"                  → listTeachersByDepartment  або  getDepartmentCompliance
            • "Хто не відповідає", "проблемні", "у групі ризику"→ getNonCompliantTeachers
            • "Які є кафедри", "огляд кафедр"                   → listDepartments
            • "Стан системи", "скільки всього"                  → getOverallStats
            ─── Мови та кваліфікація ───
            • "СМР 2", "STANAG", "англ. рівень 3"               → searchTeachersByLanguageLevel
            • "NATO", "SANS", "курси", "стажування"             → searchTeachersByQualificationKeyword
            ─── Досягнення та публікації ───
            • "Досягнення п.38 у X"                             → findTeacherByLastName → getTeacherAchievements
            • "Публікації X", "статті X"                        → findTeacherByLastName → getTeacherPublications
            ─── Дисципліни (що викладає / хто викладає) ───
            • "Які дисципліни викладає X"                       → findTeacherByLastName → getTeacherDisciplines
            • "Хто викладає Кібернетику"                        → findTeachersByDiscipline("кібернетика")
            • "Хто читає ОК 6"                                  → findTeachersByDiscipline("ОК 6")
            ─── Рейтинг (Додаток 1) ───
            • "Який рейтинг у X", "скільки балів у X"           → findTeacherByLastName → getTeacherRating
            • "Топ-10 за рейтингом", "найкращі 5 за балами"     → getTopTeachersByRating
            • "Топ кафедри 33"                                  → getTopTeachersByRating(_, "33", _)
            • "Які періоди рейтингування"                       → listRatingPeriods
            ─── Послужний список ───
            • "Де працював X раніше", "досвід роботи X", "стаж" → findTeacherByLastName → getTeacherCareerHistory
            ─── Освітні програми (ОПП) ───
            • "Які є ОПП", "перелік програм"                    → listEducationalPrograms
            • "ОПП Кібербезпека", "програма F3 КН"              → findEducationalProgram
            • "Які ОПП на кафедрі 33"                           → getDepartmentEducationalPrograms("33")
            • "Викладачі для ОПП X"                             → findEducationalProgram → getProgramTeachers(programId)
            ─── Штатний розпис ───
            • "Хто на 2-й штатній посаді 33-ї кафедри"          → getDepartmentStaffPositions("33")
            • "Скільки вакансій на кафедрі"                     → getDepartmentStaffPositions
            • "На якій штатці X"                                → findTeacherByLastName → getTeacherStaffPosition
            • СМИСЛОВИЙ пошук (не точні слова):                 → semanticSearchTeachers
              "хто воював в АТО/ООС", "фахівці з радіолокації",
              "хто досліджував штучний інтелект", "експерти з кібербезпеки"
              → цей інструмент шукає за змістом повного профілю (не по ключам).
              Використовуй коли keyword-інструменти не знаходять нічого
              або коли запит широкий та описовий.

              ПРАВИЛА ОБРОБКИ РЕЗУЛЬТАТІВ semanticSearchTeachers:
              1. Результат вже відфільтрований: нерелевантні (score < 0.55) НЕ повертаються.
              2. Score ≥ 0.70 = точна відповідність; 0.55..0.70 = "найближчі за змістом".
              3. ЯКЩО повернуто [] — скажи прямо: "Точних відповідностей не знайдено".
                 НЕ вигадуй список викладачів з інших tools!
              4. ЯКЩО ВСІ результати мають score < 0.65 — попередь користувача що це
                 ПРИБЛИЗНІ, не точні відповідники, і зазначай score у відповіді.
              5. ЗАВЖДИ перевіряй snippet перед включенням у відповідь — якщо snippet
                 НЕ містить того, про що питав користувач (напр. питали про "штучний
                 інтелект", а у snippet немає ні AI ні ML ні нейромереж) — ВИКЛЮЧИ цей
                 результат з відповіді (навіть якщо score високий — це false positive
                 через схожі загальні терміни типу "дисертація").

            === ФОРМАТ ВІДПОВІДІ ===
            • УКРАЇНСЬКОЮ мовою, markdown.
            • Для 1 особи: коротка сводка + деталі списком/таблицею.
            • Для списків (>3 записів): **markdown таблиця**; стовпці — найважливіші поля.
            • Для статистики: маркований список з числами.
            • НЕ дублюй все що повернув tool — обирай релевантне до ПИТАННЯ.
            • Якщо рекомендуєш — конкретно який підпункт п.38 додати і чому.

            === ПРИКЛАДИ ===

            [Приклад 1] User: "Хто такий Стоцький?"
            Дія: findTeacherByLastName("Стоцький") → бачиш id=42 → getTeacherFullProfile(42)
            Відповідь:
            **Стоцький Микола Сергійович** (полковник, 1975 р.н.)
            - Посада: Доцент кафедри 210 — ЗЗ
            - Ступінь: канд. техн. наук; звання: доцент
            - Стаж: з 01.01.2005 (20 р.)
            - УБД: ✅ (2014–2022)
            - **п.38: COMPLIANT** (5 унікальних типів: PP_1, PP_4, PP_8, PP_16, PP_18)

            [Приклад 2] User: "Хто не відповідає п.38?"
            Дія: getNonCompliantTeachers()
            Відповідь:
            Знайдено 4 викладача в зоні ризику:

            | ПІБ | Кафедра | Статус | Типів виконано |
            |---|---|---|---|
            | Іваненко І.І. | 210 — ЗЗ | WARNING | 3/4 |
            | Петренко П.П. | 305 — АСУ | NON_COMPLIANT | 2/4 |
            | ... | ... | ... | ... |

            [Приклад 3] User: "А його публікації?" (після запиту про Стоцького — memory пам'ятає)
            Дія: getTeacherPublications(42)  ← id з попереднього turn
            Відповідь: **Публікації Стоцького М.С. (12 шт.)** + список топ-5.

            [Приклад 4] User: "Хто проходив курси NATO?"
            Дія: searchTeachersByQualificationKeyword("NATO")
            Відповідь: таблиця з колонками teacherName, курс, організація, дати, години.

            [Приклад 5] User: "Хто має досвід з радіолокації?"
            Дія: keyword "радіолокація" може не знайти — використовуй semanticSearchTeachers("радіолокація")
            → отримуєш топ-K викладачів зі score. Бери тих де score >= 0.5.
            → далі за потреби getTeacherFullProfile(id) для деталей.
            Відповідь: список з кафедрами + пояснення звідки взяв (дисертація / публікації).

            [Приклад 6] User: "Скільки наукових ступенів у Редзюка?"
            Дія: findTeacherByLastName("Редзюк") → отримуєш academicDegreesCount=2,
                 academicDegrees=["Доктор технічних наук", "Кандидат технічних наук"].
            Відповідь:
            **Редзюк Євгеній Володимирович** має **2 наукові ступені**:
            1. Доктор технічних наук
            2. Кандидат технічних наук
            ❌ НЕПРАВИЛЬНО: "У Редзюка один науковий ступінь — доктор технічних наук"
            (це ігнорує другий запис у масиві).

            [Приклад 7] User: "Які дисципліни викладає Стоцький?"
            Дія: findTeacherByLastName("Стоцький") → id=42 → getTeacherDisciplines(42)
            Відповідь: таблиця | Дисципліна | Код | Кредити | Рік | Семестр | ОПП |.

            [Приклад 8] User: "Хто викладає Кібернетику?"
            Дія: findTeachersByDiscipline("кібернетика")
            Відповідь: список дисциплін з відповідними викладачами.

            [Приклад 9] User: "Який рейтинг у Стоцького?"
            Дія: findTeacherByLastName("Стоцький") → id=42 → getTeacherRating(42)
            Відповідь: загальний бал + breakdown по критеріях (Scopus 3×20=60, ...).

            [Приклад 10] User: "Топ-10 за рейтингом"
            Дія: getTopTeachersByRating(null, null, 10)
            Відповідь: ранжована таблиця | # | ПІБ | Кафедра | Бали |.

            [Приклад 11] User: "Де раніше працював Стоцький?"
            Дія: findTeacherByLastName("Стоцький") → id=42 → getTeacherCareerHistory(42)
            Відповідь: хронологічний список (Посада @ Організація, дати).

            [Приклад 12] User: "Які ОПП на кафедрі 33?"
            Дія: getDepartmentEducationalPrograms("33")
            Відповідь: таблиця ОПП з рівнем, ступенем, кредитами.

            [Приклад 13] User: "Хто на 2-й штатній посаді кафедри 33?"
            Дія: getDepartmentStaffPositions("33")
            → знаходиш orderNumber=2 → відповідь з positionTitle, teacherName (або "ВАКАНТ").

            === EDGE CASES ===
            • Tool повернув []: "За запитом '<X>' записів не знайдено. Можливо, спробуйте <альтернатива>."
            • Tool повернув error: "Не вдалося отримати дані (причина). Спробуйте по-іншому сформулювати."
            • Неоднозначний запит: спитайте уточнення ("Ви маєте на увазі 210-й — ЗЗ чи 310-ту — АСУ?").
            • Користувач просить інформацію НЕ про викладачів/кафедри: ввічливо скажіть що ви асистент з ліцензуванням.
            """;

    /**
     * Загальний чат з AI-асистентом.
     *
     * Архітектура (Step 1 + Step 2):
     * 1. System prompt містить лише OVERVIEW системи (кількість, розподіл по кафедрах) —
     *    НЕ дампимо всі 150+ викладачів у контекст.
     * 2. Multi-turn ChatMemory (Step 1): зберігає user+assistant повідомлення за conversationId.
     * 3. Function Calling (Step 2): AI має набір @Tool методів (AiToolsService) — може
     *    САМ викликати їх для отримання точних даних. Це усуває галюцинації: замість
     *    вигадування імен/чисел AI робить реальні запити до БД.
     *
     * Приклад діалогу:
     * - User: "Що відомо про Стоцького?"
     * - AI internally: findTeacherByLastName("Стоцький") → бачить id=42
     * - AI internally: getTeacherFullProfile(42) → отримує всі деталі
     * - AI user-facing: Структурована відповідь на основі точних даних
     *
     * @param message        питання користувача (чистий текст)
     * @param context        опціональний додатковий контекст від фронту
     * @param conversationId UUID розмови; null/порожнє → stateless
     */
    public AiResponse chat(String message, String context, String conversationId) {
        try {
            // 1. Свіжий OVERVIEW контекст (тільки статистика + список кафедр).
            //    Деталі AI отримає через tool calls.
            String overviewContext = aiContextService.buildCompactContext();
            String fullSystemPrompt = SYSTEM_PROMPT
                    + "\n\n=== ПОТОЧНИЙ ОГЛЯД ===\n"
                    + overviewContext;

            // 2. Чисте user-повідомлення — саме воно зберігається у memory
            String userMessage = (context != null && !context.isEmpty())
                    ? "Додатковий контекст: " + context + "\n\n" + message
                    : message;

            // 3. Tools через OpenAiChatOptions (native options для OpenAiChatModel).
            //    Причина вибору саме OpenAiChatOptions, а не загального ToolCallingChatOptions:
            //    OpenAiChatModel.buildRequestPrompt() робить ModelOptionsUtils.copyToTarget від
            //    generic типу до OpenAiChatOptions. При копіюванні між різними класами toolCallbacks
            //    можуть не переноситись (баг/особливість M6). З native-типом copy не потрібен — prompt
            //    зберігає наші callbacks до самого DefaultToolCallingManager.executeToolCall.
            FunctionCallback[] callbacks = getOrCreateToolCallbacks();
            OpenAiChatOptions toolOptions = OpenAiChatOptions.builder()
                    .toolCallbacks(callbacks)
                    .build();

            // INFO level — щоб було видно у prod-логах (prod logging.level=INFO)
            if (log.isInfoEnabled()) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < callbacks.length; i++) {
                    if (i > 0) names.append(",");
                    names.append(callbacks[i].getName());
                }
                log.info("AI chat: using {} tool callbacks: [{}]", callbacks.length, names);
            }

            ChatClient chatClient = chatClientBuilder.build();
            ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                    .system(fullSystemPrompt)
                    .user(userMessage)
                    .options(toolOptions);

            // 4. Memory — тільки якщо є conversationId (backward-compat)
            boolean useMemory = conversationId != null && !conversationId.isBlank();
            if (useMemory) {
                promptSpec = promptSpec.advisors(
                        new MessageChatMemoryAdvisor(chatMemory, conversationId, MEMORY_WINDOW_SIZE)
                );
            }

            String response = promptSpec.call().content();

            log.debug("AI chat: conversationId={}, memory={}, msgLen={}",
                    conversationId, useMemory, message.length());

            return AiResponse.success(response, "mistral");
        } catch (Exception e) {
            log.error("AI chat error (conversationId={})", conversationId, e);
            return AiResponse.error("Помилка AI: " + e.getMessage());
        }
    }

    /**
     * Очистити пам'ять конкретної розмови.
     * Викликається при натисканні "Новий чат" на фронтенді.
     */
    public void clearConversation(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            try {
                chatMemory.clear(conversationId);
                log.debug("Cleared chat memory for conversationId={}", conversationId);
            } catch (Exception e) {
                log.warn("Failed to clear memory for conversationId={}", conversationId, e);
            }
        }
    }

    /**
     * Генерація тексту (описи, звіти)
     */
    public AiResponse generateText(String prompt) {
        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .system("Ви — помічник для написання академічних текстів українською мовою.")
                    .user(prompt)
                    .call()
                    .content();

            return AiResponse.success(response, "mistral");
        } catch (Exception e) {
            log.error("AI text generation error", e);
            return AiResponse.error("Помилка генерації тексту: " + e.getMessage());
        }
    }
}
