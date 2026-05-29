package ua.edu.teacherlicence.dataimport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.repository.AchievementRepository;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.discipline.model.Discipline;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.DisciplineRepository;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.fakhove.dto.VerificationResult;
import ua.edu.teacherlicence.fakhove.model.JournalCategory;
import ua.edu.teacherlicence.fakhove.service.FakhovyiJournalService;
import ua.edu.teacherlicence.publication.model.ApprobationSubtype;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.MethodicalSubtype;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationStatus;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.teacher.service.TeacherService;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.AcademicTitle;
import ua.edu.teacherlicence.teacher.model.CareerRecord;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository;
import ua.edu.teacherlicence.teacher.repository.CareerRecordRepository;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.teacher.util.AcademicDegreeRanking;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
public class AiImportService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final AchievementRepository achievementRepository;
    private final DepartmentRepository departmentRepository;
    private final DisciplineRepository disciplineRepository;
    private final TeacherDisciplineRepository teacherDisciplineRepository;
    private final PublicationRepository publicationRepository;
    private final QualificationImprovementRepository qualificationRepository;
    private final CareerRecordRepository careerRecordRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final ua.edu.teacherlicence.teacher.repository.EducationRepository educationRepository;
    private final FakhovyiJournalService fakhovyiJournalService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ua.edu.teacherlicence.scopus.ScopusApiService scopusApiService;
    private final PpDataParser ppDataParser;
    private final DisciplineMatcher disciplineMatcher;
    private final ua.edu.teacherlicence.publication.service.DstuCitationGenerator dstuGenerator;
    private final ua.edu.teacherlicence.achievement.service.AchievementComposer achievementComposer;
    private final ua.edu.teacherlicence.ppdata.service.PpDataValidationService ppDataValidationService;

    // ppData repositories
    private final ScientificSupervisionRepository scientificSupervisionRepo;
    private final AttestationActivityRepository attestationActivityRepo;
    private final EditorialActivityRepository editorialActivityRepo;
    private final ExpertCouncilRepository expertCouncilRepo;
    private final InternationalProjectRepository internationalProjectRepo;
    private final ScientificConsultingRepository scientificConsultingRepo;
    private final ForeignLanguageTeachingRepository foreignLanguageTeachingRepo;
    private final OlympiadGuidanceRepository olympiadGuidanceRepo;
    private final MilitaryMissionRepository militaryMissionRepo;
    private final ProfessionalAssociationRepository professionalAssociationRepo;
    private final PracticalExperienceRepository practicalExperienceRepo;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final AcademicTitleRepository academicTitleRepository;

    /** Holder для розпарсених наукових даних, що зберігаються в окремі сутності AcademicDegree/AcademicTitle. */
    static class ScientificData {
        String degreeName;
        String dissertationTopic;
        String dissertationSpeciality;
        String degreeDiploma;
        LocalDate degreeDiplomaDate;
        String titleName;
        String titleAttestat;
        LocalDate titleAttestatDate;

        boolean hasDegree() {
            return degreeName != null || degreeDiploma != null || dissertationTopic != null
                    || dissertationSpeciality != null || degreeDiplomaDate != null;
        }

        boolean hasTitle() {
            return titleName != null || titleAttestat != null || titleAttestatDate != null;
        }
    }

    private static final String SYSTEM_PROMPT_BASIC = """
            Ви — парсер таблиць кадрового забезпечення ВНЗ України.
            Вам надається текст ОДНОГО рядка з таблиці DOCX (11 стовпців, розділених " | ").
            Витягніть основні дані про ОДНОГО викладача.

            ВАЖЛИВО: Поверніть ТІЛЬКИ один валідний JSON об'єкт (НЕ масив! без markdown, без ```json, без пояснень).
            Перший символ відповіді має бути { а останній }.

            === СТРУКТУРА СТОВПЦІВ ТАБЛИЦІ ===
            Стовпець 0: ПІБ (прізвище, ім'я, по батькові), рік народження, військове звання, стаж
            Стовпець 1: ПОСАДА (наприклад "Професор кафедри ...", "Доцент кафедри ...", "Начальник кафедри ...")
            Стовпець 2: Освітні компоненти (дисципліни)
            Стовпець 3: ОСВІТА — ВНЗ, рік закінчення, спеціальність, диплом
            Стовпець 4: НАУКОВА КВАЛІФІКАЦІЯ — науковий ступінь, вчене звання, дисертація, атестат
            Стовпець 5: Послужний список (кар'єра)
            Стовпець 6: Бойовий досвід
            Стовпець 7: Іноземні мови
            Стовпець 8: Підвищення кваліфікації
            Стовпець 9: Ідентифікатори (ORCID, email, Scopus, WoS, Google Scholar)
            Стовпець 10: Номери підпунктів п.38

            Формат JSON об'єкта:
            {
              "lastName": "ПРІЗВИЩЕ",
              "firstName": "Ім'я",
              "patronymic": "По батькові",
              "dateOfBirth": "1980-06-21",
              "militaryRank": "полковник",
              "position": "Професор кафедри комп'ютерних наук (основна)",
              "employmentType": "MAIN",
              "experienceYears": 25,
              "academicDegree": "Кандидат технічних наук",
              "academicTitle": "Доцент",
              "university": "Військовий інститут телекомунікацій та інформатизації",
              "universitySpeciality": "Програмне забезпечення автоматизованих систем",
              "universityDiploma": "Диплом серія МО №012345",
              "universityGraduationYear": 2005,
              "universityDiplomaDate": "2005-06-25",
              "educations": [
                {
                  "institution": "Київський технікум електронних приладів",
                  "city": "м. Київ",
                  "degree": "Фаховий молодший бакалавр",
                  "speciality": "Радіотехнічні вимірювання",
                  "qualification": null,
                  "graduationYear": 1988,
                  "diploma": "Диплом з відзнакою ИТ-1 №061872",
                  "diplomaDate": "1988-02-27"
                },
                {
                  "institution": "Військовий інститут телекомунікацій та інформатизації",
                  "city": "м. Київ",
                  "degree": "Магістр",
                  "speciality": "Програмне забезпечення автоматизованих систем",
                  "qualification": "Інженер-програміст",
                  "graduationYear": 2005,
                  "diploma": "Диплом серія МО №012345",
                  "diplomaDate": "2005-06-25"
                }
              ],
              "dissertationTopic": "Тема дисертації (або 'Тема закрита' якщо закрита)",
              "dissertationSpeciality": "Озброєння і військова техніка",
              "degreeDiploma": "ДК №012345",
              "degreeDiplomaDate": "2015-07-01",
              "titleAttestat": "Атестат 12ДЦ №012345",
              "titleAttestatDate": "2016-12-15",
              "combatVeteranStatus": true,
              "combatExperienceDates": "03.09.2015-11.11.2015",
              "combatVeteranDocDate": "2016-05-10",
              "combatVeteranDocIssuedBy": "МО України",
              "orcidId": "0000-0001-2345-6789",
              "email": "name@example.com",
              "scopusId": "12345",
              "wosId": "C-1998-2019",
              "googleScholarUrl": "https://...",
              "disciplines": ["Кросплатформне програмування", "Проєктування алгоритмів"],
              "achievementNumbers": [1, 2, 3, 4, 14, 16, 19],
              "qualifications": [
                {
                  "title": "Назва курсу або тема",
                  "organization": "Організація, яка проводила",
                  "startDate": "2024-01-15",
                  "endDate": "2024-03-20",
                  "hours": 180,
                  "credits": 6.0,
                  "certificateNumber": "№ 123456",
                  "certificateDate": "2024-04-01",
                  "certificateUrl": "https://..."
                }
              ],
              "careerRecords": [
                {
                  "position": "Посада (наприклад, Викладач кафедри ...)",
                  "organization": "Назва організації (інститут, університет тощо)",
                  "startDate": "2011-02-01",
                  "endDate": "2013-02-01"
                }
              ],
              "foreignLanguages": [
                {
                  "language": "Англійська",
                  "level": "СМР 1+222 або A1-A2 або Elementary",
                  "certificateDetails": "Повний текст з реквізитами сертифіката",
                  "certificateNumber": "MIL-ENG-2024-001 або серія/номер",
                  "certificateDate": "2024-03-15",
                  "certificateOrganization": "Назва організації, яка видала сертифікат"
                }
              ]
            }

            === КРИТИЧНІ ПРАВИЛА РОЗРІЗНЕННЯ ПОЛІВ ===

            ПОСАДА (position) — це ТІЛЬКИ зі стовпця 1. Приклади:
              "Професор кафедри комп'ютерних наук", "Доцент кафедри зв'язку", "Начальник кафедри".
              НІКОЛИ не плутайте посаду з вченим званням!

            НАУКОВИЙ СТУПІНЬ (academicDegree) — ТІЛЬКИ зі стовпця 4. Це:
              - "Кандидат технічних наук" (к.т.н.), "Доктор військових наук" (д.в.н.), "PhD"
              - Скорочення: к.т.н., к.в.н., д.т.н., к.е.н., к.ф.-м.н. тощо → розшифруйте повністю
              - Підтверджується дипломом (ДК №...)
              - ОБОВ'ЯЗКОВО шукайте у стовпці 4!

            ДИСЕРТАЦІЯ — зі стовпця 4:
              - dissertationTopic — тема дисертації. Якщо "Тема закрита" або "Закрита" — так і пишіть.
              - dissertationSpeciality — шифр та назва спеціальності дисертації.
                Приклади: "Озброєння і військова техніка", "Інформаційні технології".
                Може бути записано як "спеціальність: Інформаційні технології" або просто без слова Спеціальність.
                ВАЖЛИВО: це НЕ спеціальність диплому! Це спеціальність за якою захищена дисертація.

            ВЧЕНЕ ЗВАННЯ (academicTitle) — ТІЛЬКИ зі стовпця 4. Це ОДНЕ з трьох:
              "Професор", "Доцент", "Старший дослідник"
              - Підтверджується атестатом (12ДЦ, 12ПР, 02СД...)
              - УВАГА: "Професор кафедри ..." у стовпці 1 — це ПОСАДА, НЕ звання!
              - "Доцент кафедри ..." у стовпці 1 — це ПОСАДА, НЕ звання!
              - Вчене звання "Професор" чи "Доцент" вказується ТІЛЬКИ у стовпці 4 (наукова кваліфікація)

            ОСВІТА (educations) — ТІЛЬКИ зі стовпця 3. Містить:
              - Назву ВНЗ, місто, рік закінчення, спеціальність, кваліфікацію, диплом
              - ОБОВ'ЯЗКОВО заповнюйте, якщо стовпець 3 не порожній!
              - Повертайте ВСІ освіти як масив об'єктів educations (навіть якщо одна).
              - Кожен об'єкт: {"institution": "...", "city": "...", "degree": "Магістр/Спеціаліст/Бакалавр/Фаховий молодший бакалавр", "speciality": "...", "qualification": "...", "graduationYear": 2005, "diploma": "Диплом серія МО №012345", "diplomaDate": "2005-06-25"}
              - Також заповнюйте university (= institution останньої/найвищої освіти) та universitySpeciality для сумісності

            Загальні правила:
            - lastName — ПРІЗВИЩЕ (великими літерами)
            - employmentType — "MAIN" або "PART_TIME" (якщо "сумісник")
            - combatVeteranStatus — true якщо є бойовий досвід (стовпець 6)
            - disciplines — масив назв дисциплін зі стовпця 2. Без кодів (F3), (F6) тощо
            - achievementNumbers — масив номерів зі стовпця 10 (останній)
            - qualifications — масив курсів підвищення кваліфікації зі стовпця 8.
              Кожен запис — окремий курс або сертифікат. Якщо є під-пункти (а, б, в) — кожен окремо.
              Дати у форматі ISO: "YYYY-MM-DD". Часи — число годин. Кредити — дробове число.
            - careerRecords — масив записів послужного списку зі стовпця 5.
              Кожен рядок — окремий запис. Розділіть посаду і організацію.
              Дати у форматі ISO: "YYYY-MM-DD". Якщо дата лише MM.YYYY — перший день місяця.
              Якщо "по теперішній час" — endDate = null.
            - foreignLanguages — масив іноземних мов зі стовпця 7 (НЕ стовпець 8!).
              ВАЖЛИВО: Сертифікати з іноземних мов (Elementary, A1-A2, СМР, English Course) — це foreignLanguages, НЕ qualifications!
              language — назва мови ("Англійська", "Німецька"), level — рівень (СМР, CEFR, Elementary тощо),
              certificateDetails — повний текст з реквізитами.
              certificateNumber — номер/серія сертифіката (напр. "MIL-ENG-2024-001", "СМР №1234").
              certificateDate — дата видачі у форматі ISO "YYYY-MM-DD". Якщо невідома — null.
              certificateOrganization — організація, яка видала сертифікат.
            - Якщо поле невідоме — null
            - ЗАБОРОНЕНО транслітерувати кирилицю на латиницю! Прізвище, ім'я, по-батькові, посади, назви ВНЗ, дисципліни — все ТІЛЬКИ українською мовою (кирилицею), як у вхідному тексті. Ніколи не перекладайте та не транслітеруйте на англійську.
            """;

    private static final String SYSTEM_PROMPT_ACHIEVEMENTS = """
            Ви — парсер досягнень викладача ВНЗ України за пунктом 38 ліцензійних умов.
            Вам надається текст деталізації досягнень одного викладача. Текст містить секції виду "п.38 пп.X." з описами.

            ВАЖЛИВО: Поверніть ТІЛЬКИ валідний JSON об'єкт (без markdown, без ```json, без пояснень).

            Формат:
            {
              "achievements": [
                {
                  "ppNumber": 1,
                  "description": "Повний текст секції пп.1 (скорочений до 2000 символів)"
                }
              ],
              "publications": [
                {
                  "title": "Назва публікації (повний бібліографічний опис)",
                  "journalName": "Назва журналу, збірника або видання",
                  "conferenceInfo": "Назва конференції, дата і місце проведення (тільки для тез/апробацій)",
                  "publisher": "Видавництво (наприклад: Наукова думка, Springer, IEEE)",
                  "city": "Місто видання (наприклад: Київ, London)",
                  "issn": "ISSN журналу (якщо є)",
                  "isbn": "ISBN книги (якщо є)",
                  "volume": "Том, номер випуску (наприклад: Т.15, №3 або Vol.10, Issue 2)",
                  "pages": "Сторінки (наприклад: С. 45-52 або pp. 100-115)",
                  "totalPages": 120,
                  "type": "ARTICLE",
                  "ppNumber": 1,
                  "year": 2024,
                  "doi": "10.1234/...",
                  "url": "https://...",
                  "authors": "Іванов І.І., Петров П.П."
                }
              ]
            }

            Правила:
            - achievements — кожна секція п.38 пп.X стає окремим елементом
            - Текст перед першою секцією (Статті Scopus:, Статті у фахових виданнях:) = пп.1
            - publications — витягніть КОЖНУ окрему публікацію з:
              * Тексту перед першою секцією (зазвичай Scopus/фахові статті)
              * Секції пп.1 (наукові публікації у виданнях Scopus/WoS/фахових)
              * Секції пп.3 (підручники, навчальні посібники, монографії) — type "TEXTBOOK", "STUDY_GUIDE" або "MONOGRAPH"
              * Секції пп.4 (КОЖНА окрема методична праця: РПНД, практикуми, конспекти лекцій, методичні вказівки, методичні рекомендації, методичні розробки, НМК, е-курси, силабуси) — type "METHODICAL". УВАГА: РПНД (робоча програма навчальної дисципліни) — це ПУБЛІКАЦІЯ типу METHODICAL!
              * Секції пп.12 (тези конференцій, апробації) — type "APPROBATION"
              * Будь-яких інших секцій, де згадуються конкретні публікації

            === ТИПИ ПУБЛІКАЦІЙ ===
            - "ARTICLE" — наукові статті (Scopus, WoS, фахові видання тощо)
            - "PATENT" — патент на винахід
            - "DECLARATIVE_PATENT" — деклараційний патент / патент на корисну модель
            - "COPYRIGHT" — свідоцтво про авторське право
            - "TEXTBOOK" — підручники (пп.3)
            - "STUDY_GUIDE" — навчальні посібники (пп.3)
            - "MONOGRAPH" — монографії
            - "METHODICAL" — навчально-методичні праці: практикуми, конспекти лекцій, методичні вказівки, РПНД, е-курси (пп.4)
            - "APPROBATION" — тези доповідей, доповіді/статті на конференціях, апробації (пп.12)
            - "POPULAR_SCIENTIFIC" — науково-популярне, науково-експертне видання
            - "OTHER" — все інше (якщо не підходить жоден тип вище)

            Як визначити тип:
            - Якщо згадано Scopus/Scopus-indexed → "ARTICLE"
            - Якщо згадано Web of Science/WoS → "ARTICLE"
            - Якщо "фахове видання", "наукове видання" → "ARTICLE"
            - Якщо "патент на винахід" → "PATENT"
            - Якщо "деклараційний патент", "патент на корисну модель" → "DECLARATIVE_PATENT"
            - Якщо "свідоцтво про авторське право" → "COPYRIGHT"
            - Якщо "тези", "тези доповідей", "матеріали конференції", "апробація" → "APPROBATION"
            - CEUR Workshop Proceedings: визначай тип за змістом — тези доповідей → "APPROBATION", повноцінна наукова стаття → "ARTICLE"
            - Якщо "підручник" → "TEXTBOOK"
            - Якщо "навчальний посібник", "посібник" (не методичний) → "STUDY_GUIDE"
            - Якщо "практикум", "конспект лекцій", "методичні вказівки", "РПНД", "робоча програма навчальної дисципліни", "робоча програма", "методична розробка", "методичні рекомендації", "е-курс", "електронний курс", "навчально-методичний комплекс", "НМК", "силабус" → "METHODICAL"
            - Якщо "монографія" → "MONOGRAPH"
            - Якщо "науково-популярне", "науково-експертне" → "POPULAR_SCIENTIFIC"
            - Інакше → "OTHER"

            === ВАЖЛИВО: ВИТЯГНІТЬ НАЗВУ ВИДАННЯ ===
            journalName — це НАЗВА ЖУРНАЛУ, ЗБІРНИКА або ВИДАННЯ, де опубліковано роботу. Приклади:
            - "Збірник наукових праць ВІТІ" (збірник)
            - "Зв'язок" (журнал)
            - "Системи обробки інформації" (журнал)
            - "IEEE Communications Letters" (журнал Scopus)
            - "Modern Information Technologies in the Sphere of Security and Defence" (журнал)
            Для підручників/посібників: journalName = null, замість цього заповніть publisher (видавництво).
            Для тез конференцій: journalName = назва збірника тез, conferenceInfo = назва конференції і дати.
            ЗАВЖДИ намагайтеся витягнути назву видання з бібліографічного опису!

            - ppNumber — номер підпункту п.38, з якого взята публікація (1, 2, 3, 4 або 12)
              * Статті Scopus/WoS/фахові → ppNumber: 1
              * Патенти, свідоцтва → ppNumber: 2
              * Підручники, посібники, монографії → ppNumber: 3
              * Методичні праці → ppNumber: 4
              * Тези конференцій, апробації → ppNumber: 12
            - doi — тільки якщо явно зазначено
            - authors — ПОВНИЙ список авторів (включно з автором-викладачем) у форматі "Прізвище І. Б."
            - ВАЖЛИВО: Кожна публікація — окремий елемент масиву! Якщо в одній секції є 5 статей — це 5 елементів.
            - РПНД (робоча програма навчальної дисципліни) — ЦЕ публікація типу METHODICAL (пп.4)! ОБОВ'ЯЗКОВО включайте!
            - Не включайте до publications: ОПП (освітньо-професійна програма), свідоцтва авторських прав, патенти, членство в організаціях
            """;

    // =====================================================================
    // AI-промпт для парсингу структурованих ppData (пп.5-20)
    // =====================================================================
    private static final String SYSTEM_PROMPT_PPDATA = """
            Ви — парсер структурованих даних секцій пп.5-20 пункту 38 ліцензійних умов ВНЗ України.
            Вам надається текст секцій досягнень одного викладача (пп.5-пп.20). Витягніть структуровані дані для кожної секції.

            ВАЖЛИВО: Поверніть ТІЛЬКИ валідний JSON об'єкт (без markdown, без ```json, без пояснень).

            Формат:
            {
              "pp5": {
                "dissertationTopic": "Повна тема дисертації",
                "academicDegree": "Кандидат технічних наук",
                "degreeDiploma": "Диплом ДК №012345",
                "degreeDiplomaDate": "2015-07-01"
              },
              "pp6": [
                {
                  "studentName": "Прізвище І.Б.",
                  "topic": "Тема дисертації здобувача",
                  "defenseDate": "2020-05-15",
                  "degreeType": "PHD",
                  "diplomaNumber": "ДК №012345"
                }
              ],
              "pp7": [
                {
                  "role": "OPPONENT",
                  "councilName": "Назва спеціалізованої вченої ради",
                  "studentName": "Прізвище І.Б.",
                  "defenseDate": "2021-03-20",
                  "count": 1
                }
              ],
              "pp8": [
                {
                  "role": "BOARD_MEMBER",
                  "journalOrProjectName": "Назва журналу або проекту",
                  "dateFrom": "2019-01-01",
                  "dateTo": "2024-12-31",
                  "description": "Повний текст запису"
                }
              ],
              "pp9": [
                {
                  "councilName": "Назва ради",
                  "type": "NAZYAVO",
                  "role": "член",
                  "dateFrom": "2020-01-01",
                  "dateTo": "2023-12-31",
                  "orderNumber": "наказ №123"
                }
              ],
              "pp10": [
                {
                  "projectName": "Назва проекту",
                  "program": "ERASMUS",
                  "role": "учасник",
                  "dateFrom": "2021-09-01",
                  "dateTo": "2023-08-31",
                  "description": "Повний текст"
                }
              ],
              "pp11": [
                {
                  "organizationName": "Назва організації",
                  "contractNumber": "договір №123",
                  "dateFrom": "2020-01-01",
                  "dateTo": "2022-12-31",
                  "yearsCount": 3
                }
              ],
              "pp13": [
                {
                  "disciplineName": "Назва дисципліни",
                  "language": "Англійська",
                  "hours": 60,
                  "academicYear": "2023-2024",
                  "semester": 1
                }
              ],
              "pp14": [
                {
                  "activityType": "OLYMPIAD або SCIENTIFIC_COMPETITION або COMPETITION або SCIENTIFIC_GROUP або SPORTS або ARTS або OTHER",
                  "competitionScope": "INTERNATIONAL або NATIONAL (масштаб заходу, якщо відомо)",
                  "olympiadName": "Назва олімпіади/конкурсу/гуртка",
                  "studentName": "Прізвище І.Б. (якщо є)",
                  "result": "І місце (якщо є)",
                  "year": 2023,
                  "role": "SUPERVISOR або GROUP_LEADER або COACH або JURY або COMMITTEE або CURATOR",
                  "departmentName": "Назва кафедри (для гуртків)",
                  "participantCount": 14,
                  "academicYear": "2023-2024",
                  "orderNumber": "наказ №133",
                  "orderDate": "2023-08-20",
                  "description": "Повний текст запису (якщо не вдається розпарсити)"
                }
              ],
              "pp15": [
                {
                  "activityType": "OLYMPIAD або SCIENTIFIC_COMPETITION або COMPETITION або OTHER",
                  "competitionScope": "INTERNATIONAL або NATIONAL (якщо відомо)",
                  "olympiadName": "Назва олімпіади/конкурсу МАН",
                  "studentName": "Прізвище І.Б.",
                  "result": "призер",
                  "year": 2023,
                  "role": "SUPERVISOR"
                }
              ],
              "pp16": {
                "combatVeteranStatus": true,
                "combatVeteranDoc": "Посвідчення УБД №123456",
                "combatVeteranDocDate": "2016-05-10",
                "combatVeteranDocIssuedBy": "МО України"
              },
              "pp17": [
                {
                  "missionName": "Назва миротворчої операції",
                  "country": "Ліван",
                  "dateFrom": "2018-03-01",
                  "dateTo": "2018-09-30"
                }
              ],
              "pp18": [
                {
                  "missionName": "Назва навчання/навчань НАТО",
                  "country": "США",
                  "dateFrom": "2019-06-01",
                  "dateTo": "2019-06-15"
                }
              ],
              "pp19": [
                {
                  "organizationName": "Назва професійної організації",
                  "role": "член",
                  "dateFrom": "2020-01-01",
                  "dateTo": null,
                  "certificateNumber": "сертифікат №123"
                }
              ],
              "pp20": [
                {
                  "organizationName": "Назва організації",
                  "position": "посада",
                  "dateFrom": "2015-01-01",
                  "dateTo": "2020-12-31",
                  "yearsCount": 5,
                  "specialtyName": "Назва спеціальності"
                }
              ]
            }

            === ПРАВИЛА ДЛЯ КОЖНОЇ СЕКЦІЇ ===

            пп.6 — Наукове керівництво:
            - degreeType: "PHD" (PhD), "CANDIDATE" (кандидат наук), "DSC" (доктор наук), "DOCTOR" (доктор наук)
            - studentName — ПІБ здобувача
            - defenseDate — дата захисту (ISO формат "YYYY-MM-DD")

            пп.7 — Участь в атестації:
            - role: "OPPONENT" (офіційний опонент дисертації), "REVIEWER" (рецензент дисертації),
              "CHAIR" (голова разової спецради), "COUNCIL_MEMBER" (член постійної спецради)
            - defenseDate — дата захисту (для OPPONENT/REVIEWER/CHAIR; ISO "YYYY-MM-DD")
            - dateFrom / dateTo — період членства у постійній спецраді (тільки для COUNCIL_MEMBER)

            пп.8 — Редакційно-видавнича діяльність:
            - role: "CHIEF_EDITOR" (головний редактор), "BOARD_MEMBER" (член редколегії), "REVIEWER" (рецензент), "THEME_LEADER" (керівник теми НДР), "RESPONSIBLE_EXECUTOR" (відповідальний виконавець НДР)

            пп.9 — Експертна рада:
            - type: "MON" (МОН), "NAZYAVO" (НАЗЯВО), "ACCREDITATION" (акредитаційна), "NMR" (науково-методична рада), "STATE_SERVICE" (державна служба)

            пп.10 — Міжнародні проекти:
            - program: "ERASMUS", "HORIZON", "NATO", "BILATERAL", "GRANT", "OTHER"

            пп.13 — Викладання іноземною мовою:
            - language — повна назва мови ("Англійська", "Німецька", "Французька")

            пп.14 — Олімпіади студентські, пп.15 — Олімпіади МАН/школярі:
            - role: "SUPERVISOR" (керівник), "JURY" (член журі), "COMMITTEE" (оргкомітет)

            пп.17 — Миротворчі операції ООН
            пп.18 — Навчання НАТО

            пп.20 — Досвід практичної роботи за спеціальністю

            === ЗАГАЛЬНІ ПРАВИЛА ===
            - Дати у форматі ISO: "YYYY-MM-DD". Якщо відома лише рік — "YYYY-01-01"
            - Якщо секція відсутня або порожня — НЕ включайте ключ у відповідь
            - Для кожного запису витягуйте ВСЮ доступну інформацію з тексту
            - Якщо в секції кілька записів (1., 2., 3.) — кожен окремий об'єкт у масиві
            - Якщо поле невідоме — null
            - КРИТИЧНО: Уважно розбирайте текст. Кожна деталь важлива — імена, дати, номери, організації!
            """;

    public DataImportService.ImportResult importFromDocx(InputStream inputStream, Long departmentId) {
        DataImportService.ImportResult result = new DataImportService.ImportResult();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            Department department = departmentId != null
                    ? departmentRepository.findById(departmentId).orElse(null)
                    : null;

            for (XWPFTable table : document.getTables()) {
                List<XWPFTableRow> rows = table.getRows();
                if (rows.isEmpty()) continue;

                // Phase 1: Розділяємо рядки
                List<XWPFTableRow> dataRows = new ArrayList<>();
                List<XWPFTableRow> detailRows = new ArrayList<>();

                for (int i = 1; i < rows.size(); i++) {
                    XWPFTableRow row = rows.get(i);
                    if (row.getTableCells().size() >= 10) {
                        dataRows.add(row);
                    } else {
                        detailRows.add(row);
                    }
                }

                log.info("AI Import: {} data rows, {} detail rows", dataRows.size(), detailRows.size());

                // Phase 2: Парсимо кожен рядок даних через AI
                Map<String, Teacher> savedTeachers = new LinkedHashMap<>();

                for (int idx = 0; idx < dataRows.size(); idx++) {
                    try {
                        XWPFTableRow row = dataRows.get(idx);
                        String rowText = formatRowForAi(row);

                        String aiError = null;
                        List<Map<String, Object>> parsed;
                        try {
                            parsed = parseWithAi(SYSTEM_PROMPT_BASIC,
                                    "Ось рядок таблиці кадрового забезпечення (стовпці через ' | '):\n\n" + rowText);
                        } catch (Exception aiEx) {
                            aiError = aiEx.getMessage();
                            parsed = Collections.emptyList();
                        }
                        if (parsed.isEmpty()) {
                            result.errors.add("AI не зміг розпарсити рядок " + idx
                                    + (aiError != null ? ": " + aiError : " (порожня відповідь)"));
                            continue;
                        }

                        Map<String, Object> data = parsed.get(0);
                        ScientificData sci = new ScientificData();
                        Teacher teacher = mapToTeacher(data, department, sci);
                        if (teacher.getLastName() == null || teacher.getLastName().isEmpty()) continue;

                        // Fallback: витягуємо пропущені поля з сирих стовпців
                        postProcessFromRawColumns(teacher, row, sci);

                        teacher = teacherRepository.save(teacher);
                        // Гарантуємо штатну позицію для нового викладача (bootstrap якщо немає).
                        teacherPositionService.ensureStaffPosition(teacher);
                        persistScientific(teacher, sci);
                        result.teachersImported++;
                        result.importedTeacherIds.add(teacher.getId());
                        savedTeachers.put(teacher.getLastName().toUpperCase(), teacher);

                        // Освіти (множинні)
                        saveEducationsFromAi(data, teacher);

                        // Дисципліни
                        @SuppressWarnings("unchecked")
                        List<String> disciplineNames = (List<String>) data.get("disciplines");
                        if (disciplineNames != null) {
                            List<Discipline> allDisciplines = disciplineRepository.findAll();
                            for (String discName : disciplineNames) {
                                if (discName == null || discName.trim().length() < 3) continue;
                                String trimmed = discName.trim();

                                Discipline disc = disciplineMatcher.findBestMatch(trimmed, allDisciplines);
                                if (disc == null) {
                                    result.errors.add("Дисципліна не знайдена: \"" + trimmed
                                            + "\" (викладач: " + teacher.getLastName()
                                            + " " + teacher.getFirstName() + ")");
                                    log.warn("AI: Discipline not found for '{}', teacher: {} {}",
                                            trimmed, teacher.getLastName(), teacher.getFirstName());
                                    continue;
                                }

                                log.info("AI: Matched discipline '{}' -> '{}' (id={})",
                                        trimmed, disc.getName(), disc.getId());
                                TeacherDiscipline td = new TeacherDiscipline();
                                td.setTeacher(teacher);
                                td.setDiscipline(disc);
                                teacherDisciplineRepository.save(td);
                                result.disciplinesAssigned++;
                            }
                        }

                        // Підвищення кваліфікації
                        saveQualificationsFromAi(data, teacher, result);

                        // Послужний список
                        saveCareerRecordsFromAi(data, teacher, result);

                        // Іноземні мови
                        saveLanguageSkillsFromAi(data, teacher, result);

                        log.info("AI: Saved teacher {} {} (disciplines: {})",
                                teacher.getLastName(), teacher.getFirstName(),
                                disciplineNames != null ? disciplineNames.size() : 0);

                    } catch (Exception e) {
                        log.warn("AI error on data row {}: {}", idx, e.getMessage());
                        result.errors.add("Рядок " + idx + ": " + e.getMessage());
                    }
                }

                // Phase 3: Обробляємо деталі парами через AI
                int d = 0;
                while (d < detailRows.size()) {
                    try {
                        String labelText = getRowText(detailRows.get(d));

                        // Зіставляємо з викладачем
                        Teacher matched = matchTeacherByLabel(labelText, savedTeachers);

                        if (matched != null && d + 1 < detailRows.size()) {
                            String achievementText = getRowText(detailRows.get(d + 1));
                            log.info("AI: Processing achievements for {} ({} chars)",
                                    matched.getLastName(), achievementText.length());

                            // Обмежуємо довжину для AI
                            String trimmedText = achievementText.length() > 12000
                                    ? achievementText.substring(0, 12000)
                                    : achievementText;

                            try {
                                List<Map<String, Object>> parsed = parseWithAi(SYSTEM_PROMPT_ACHIEVEMENTS,
                                        "Ось текст досягнень викладача " + matched.getLastName() + ":\n\n" + trimmedText);
                                if (!parsed.isEmpty()) {
                                    Map<String, Object> data = parsed.get(0);
                                    saveAchievementsFromAi(data, matched, result);
                                    savePublicationsFromAi(data, matched, result);

                                    // AI-парсинг ppData (пп.5-20) — окремий виклик ШІ
                                    try {
                                        int ppDataCount = parseAndSavePpDataWithAi(data, matched);
                                        result.ppDataImported += ppDataCount;
                                        if (ppDataCount > 0) {
                                            log.info("AI ppData: {} structured records for {}",
                                                    ppDataCount, matched.getLastName());
                                        }
                                    } catch (Exception ppEx) {
                                        log.warn("AI ppData failed for {}: {}", matched.getLastName(), ppEx.getMessage());
                                    }

                                    // Перерахувати qualifiedCount для PP_1 на основі збережених публікацій
                                    achievementComposer.recomposeForTeacher(matched);
                                }
                            } catch (Exception aiEx) {
                                log.warn("AI failed for achievements of {}: {}", matched.getLastName(), aiEx.getMessage());
                                result.errors.add("AI досягнення " + matched.getLastName() + ": " + aiEx.getMessage());
                            }
                            d += 2;
                        } else {
                            d++;
                        }
                    } catch (Exception e) {
                        log.warn("AI error on detail row {}: {}", d, e.getMessage());
                        result.errors.add("Деталі " + d + ": " + e.getMessage());
                        d++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("AI import error", e);
            result.errors.add("Загальна помилка: " + e.getMessage());
        }

        return result;
    }

    private void saveAchievementsFromAi(Map<String, Object> data, Teacher teacher,
                                         DataImportService.ImportResult result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> achievements = (List<Map<String, Object>>) data.get("achievements");
        if (achievements == null) return;

        for (Map<String, Object> achData : achievements) {
            Integer ppNum = getInt(achData, "ppNumber");
            String description = getStr(achData, "description");
            if (ppNum == null || ppNum < 1 || ppNum > 20) continue;

            AchievementType type = AchievementType.fromNumber(ppNum);
            if (type != null) {
                Achievement a = new Achievement();
                a.setTeacher(teacher);
                a.setAchievementType(type);

                // Формуємо заголовок: "пп.X — коротка версія опису" або "пп.X — назва типу"
                String titleBase = "пп." + ppNum;
                if (description != null && description.trim().length() > 5) {
                    String shortDesc = description.trim();
                    if (shortDesc.length() > 150) {
                        shortDesc = shortDesc.substring(0, 147) + "...";
                    }
                    a.setTitle(titleBase + " — " + shortDesc);
                } else {
                    a.setTitle(titleBase + " — " + type.getDescription());
                }

                if (description != null) {
                    a.setDescription(description.length() > 4000 ? description.substring(0, 4000) : description);
                } else {
                    a.setDescription(type.getDescription());
                }
                a.setVerified(false);
                achievementRepository.save(a);
                result.achievementsImported++;
                result.createdAchievementIds.add(a.getId());
            }
        }
    }

    private void savePublicationsFromAi(Map<String, Object> data, Teacher teacher,
                                         DataImportService.ImportResult result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> publications = (List<Map<String, Object>>) data.get("publications");
        if (publications == null) {
            log.warn("AI returned null publications for teacher '{} {}'",
                    teacher.getLastName(), teacher.getFirstName());
            return;
        }
        if (publications.isEmpty()) {
            log.warn("AI returned 0 publications for teacher '{} {}'",
                    teacher.getLastName(), teacher.getFirstName());
            return;
        }
        log.info("AI returned {} raw publications for teacher '{} {}'",
                publications.size(), teacher.getLastName(), teacher.getFirstName());

        List<Publication> pubs = new ArrayList<>();

        for (Map<String, Object> pub : publications) {
            String title = getStr(pub, "title");
            if (title == null || title.trim().length() < 10) {
                log.debug("Skipped publication with short/null title: '{}'", title);
                continue;
            }

            Publication p = new Publication();
            p.setTeacher(teacher);
            p.setTitle(title.trim().length() > 500 ? title.trim().substring(0, 500) : title.trim());
            p.setJournalName(getStr(pub, "journalName"));
            Integer pubYear = getInt(pub, "year");
            p.setYear(pubYear);
            // Якщо AI повернув повну дату — використовуємо її, інакше year-01-01.
            String dateStr = getStr(pub, "publicationDate");
            if (dateStr != null && !dateStr.isBlank()) {
                try {
                    java.time.LocalDate d = java.time.LocalDate.parse(dateStr);
                    p.setPublicationDate(d);
                    if (pubYear == null) p.setYear(d.getYear());
                } catch (Exception ignored) {
                    if (pubYear != null) p.setPublicationDate(java.time.LocalDate.of(pubYear, 1, 1));
                }
            } else if (pubYear != null) {
                p.setPublicationDate(java.time.LocalDate.of(pubYear, 1, 1));
            }
            p.setDoi(getStr(pub, "doi"));
            p.setUrl(getStr(pub, "url"));
            p.setAuthors(getStr(pub, "authors"));
            p.setPublisher(getStr(pub, "publisher"));
            p.setCity(getStr(pub, "city"));
            p.setIssn(getStr(pub, "issn"));
            p.setIsbn(getStr(pub, "isbn"));
            p.setVolume(getStr(pub, "volume"));
            p.setPages(getStr(pub, "pages"));
            p.setConferenceInfo(getStr(pub, "conferenceInfo"));
            Integer totalPages = getInt(pub, "totalPages");
            if (totalPages != null) p.setTotalPages(totalPages);
            p.setStatus(PublicationStatus.AI_VALIDATED);

            // ppNumber → sourceSection + ppType
            Integer ppNum = getInt(pub, "ppNumber");
            if (ppNum != null && ppNum >= 1 && ppNum <= 20) {
                p.setSourceSection("pp." + ppNum);
                AchievementType ppType = AchievementType.fromNumber(ppNum);
                if (ppType != null) {
                    p.setPpType(ppType);
                }
            }

            String typeStr = getStr(pub, "type", "OTHER");
            try {
                // Map legacy AI responses to new enum values + set articleCategory
                String upperType = typeStr.toUpperCase();
                String mapped = switch (upperType) {
                    case "SCOPUS", "WOS", "FAKHOVE" -> "ARTICLE";
                    case "CONFERENCE", "THESES" -> "APPROBATION";
                    default -> upperType;
                };
                p.setType(PublicationType.valueOf(mapped));

                // Set articleCategory from AI type hint
                if ("ARTICLE".equals(mapped)) {
                    switch (upperType) {
                        case "SCOPUS" -> p.setArticleCategory(ArticleCategory.SCOPUS);
                        case "WOS" -> p.setArticleCategory(ArticleCategory.WOS);
                        default -> {
                            // Try fakhove verification by journal name/ISSN
                            verifyAndSetCategory(p);
                        }
                    }
                }

                // CONFERENCE/THESES mapped to APPROBATION — check if venue is Scopus-indexed
                // e.g., CEUR Workshop Proceedings. Ставимо approbationSubtype=SCOPUS_WOS,
                // тип залишається APPROBATION, ppType=PP_12
                if (p.getType() == PublicationType.APPROBATION) {
                    boolean scopusFound = markScopusForApprobation(p);
                    if (scopusFound) {
                        log.info("Marked APPROBATION as Scopus-indexed: '{}'",
                                truncateForLog(p.getTitle(), 50));
                    }
                }
            } catch (IllegalArgumentException e) {
                p.setType(PublicationType.OTHER);
            }

            // Post-fix: CEUR/Proceedings/конференція → завжди APPROBATION
            // AI часто класифікує CEUR як ARTICLE, бо статті мають DOI і сторінки
            reclassifyConferenceProceedings(p);

            // Infer ppType from type if AI didn't provide ppNumber
            if (p.getPpType() == null) {
                int inferredPp = inferPpFromType(p.getType());
                p.setSourceSection("pp." + inferredPp);
                AchievementType ppType = AchievementType.fromNumber(inferredPp);
                if (ppType != null) p.setPpType(ppType);
            }

            // Post-correction: fix ppType when type contradicts AI-assigned ppNumber
            // e.g., AI assigned ppNumber=1 from document, but type=APPROBATION → should be pp.12
            correctPpTypeMismatch(p);

            // Автодетект підтипів для рейтингування
            autoAssignSubtypes(p);

            // Auto-detect Scopus/fakhovi status via journal verification
            // Scopus має пріоритет — перевіряється завжди
            enrichArticleCategory(p);

            // Перевірка актуальності (5 років)
            checkFreshness(p);

            // Якщо стаття без підтвердженої категорії і не OUTDATED → потребує уваги
            if (p.getType() == PublicationType.ARTICLE
                    && p.getArticleCategory() == null
                    && p.getStatus() != PublicationStatus.OUTDATED) {
                p.setStatus(PublicationStatus.NEEDS_ATTENTION);
                log.info("Article '{}' — journal not found in DB → NEEDS_ATTENTION",
                        truncateForLog(p.getTitle(), 50));
            }

            pubs.add(p);
        }

        // Дедуплікація: normalize(title) + year
        List<Publication> deduplicated = deduplicatePublications(pubs, teacher);
        if (deduplicated.size() < pubs.size()) {
            log.info("AI deduplication: {} → {} publications", pubs.size(), deduplicated.size());
        }

        for (Publication p : deduplicated) {
            try {
                // Автогенерація ДСТУ 8302:2015
                if (p.getDstuCitation() == null || p.getDstuCitation().isBlank()) {
                    String dstu = dstuGenerator.generate(p);
                    if (dstu != null) p.setDstuCitation(dstu);
                }
                publicationRepository.save(p);
                result.publicationsImported++;
            } catch (Exception e) {
                log.warn("Failed to save publication: {}", e.getMessage());
            }
        }
    }

    /**
     * Перевірка актуальності публікації (5 років).
     * Якщо рік старіший за 5 років — статус OUTDATED.
     */
    private void checkFreshness(Publication p) {
        java.time.LocalDate effective = p.effectiveDate();
        if (effective == null) return;
        java.time.LocalDate cutoff = java.time.LocalDate.now().minusYears(5);
        if (effective.isBefore(cutoff)) {
            p.setStatus(PublicationStatus.OUTDATED);
            log.info("Publication '{}' (date={}) is OUTDATED (cutoff={})",
                    truncateForLog(p.getTitle(), 40), effective, cutoff);
        }
    }

    /**
     * Перекласифікація: якщо journalName/publisher/conferenceInfo вказує на конференцію/proceedings,
     * а AI помилково поставив ARTICLE → виправляємо на APPROBATION.
     * CEUR Workshop Proceedings, матеріали конференцій тощо — це апробації (пп.12).
     */
    private void reclassifyConferenceProceedings(Publication p) {
        if (p.getType() != PublicationType.ARTICLE) return; // тільки помилково класифіковані ARTICLE

        String journalLower = p.getJournalName() != null ? p.getJournalName().toLowerCase() : "";
        String publisherLower = p.getPublisher() != null ? p.getPublisher().toLowerCase() : "";
        String confLower = p.getConferenceInfo() != null ? p.getConferenceInfo().toLowerCase() : "";
        String titleLower = p.getTitle() != null ? p.getTitle().toLowerCase() : "";

        boolean isProceedings = false;
        String reason = null;

        // "proceedings" в назві журналу (але НЕ CEUR — там бувають і статті, і тези)
        if (!journalLower.contains("ceur") && !publisherLower.contains("ceur")
                && (journalLower.contains("proceedings") || journalLower.contains("proceeding"))) {
            isProceedings = true;
            reason = "Proceedings in journal name";
        }
        // Матеріали/збірник конференції
        else if (journalLower.contains("матеріали") && (journalLower.contains("конференц")
                || journalLower.contains("семінар") || journalLower.contains("симпозіум"))) {
            isProceedings = true;
            reason = "Матеріали конференції";
        }
        // Тези в назві
        else if (titleLower.startsWith("тези") || journalLower.contains("тези доповідей")
                || journalLower.contains("тез доповідей") || journalLower.contains("збірник тез")) {
            isProceedings = true;
            reason = "Тези доповідей";
        }
        // conferenceInfo заповнено, а journalName = конференційний збірник
        else if (!confLower.isEmpty() && (journalLower.contains("збірник") || journalLower.contains("зб. наук. пр"))) {
            isProceedings = true;
            reason = "Збірник конференції";
        }

        if (isProceedings) {
            log.info("Reclassified ARTICLE → APPROBATION: '{}' (reason: {})",
                    truncateForLog(p.getTitle(), 50), reason);
            p.setType(PublicationType.APPROBATION);
            // Очищаємо articleCategory — для APPROBATION використовується approbationSubtype
            p.setArticleCategory(null);
        }

        // CEUR Workshop Proceedings → автоматично Scopus
        // Для ARTICLE → articleCategory, для APPROBATION → approbationSubtype
        if (journalLower.contains("ceur") || publisherLower.contains("ceur")) {
            if (p.getType() == PublicationType.APPROBATION) {
                p.setApprobationSubtype(ApprobationSubtype.SCOPUS_WOS);
                log.info("Auto-marked APPROBATION as SCOPUS_WOS (CEUR): '{}'", truncateForLog(p.getTitle(), 50));
            } else if (p.getArticleCategory() != ArticleCategory.SCOPUS) {
                p.setArticleCategory(ArticleCategory.SCOPUS);
                log.info("Auto-marked as Scopus (CEUR): '{}'", truncateForLog(p.getTitle(), 50));
            }
        }
    }

    private int inferPpFromType(PublicationType type) {
        if (type == null) return 1;
        return switch (type) {
            case ARTICLE, POPULAR_SCIENTIFIC -> 1;
            case PATENT, DECLARATIVE_PATENT, COPYRIGHT -> 2;
            case TEXTBOOK, STUDY_GUIDE, MONOGRAPH -> 3;
            case METHODICAL -> 4;
            case APPROBATION -> 12;
            case OTHER -> 1;
        };
    }

    /**
     * Виправляє ppType, якщо тип публікації суперечить присвоєному AI номеру пп.
     * Наприклад: AI присвоїв ppNumber=1 з документу, але тип = APPROBATION → має бути пп.12.
     */
    private void correctPpTypeMismatch(Publication p) {
        if (p.getType() == null || p.getPpType() == null) return;

        int correctPp = inferPpFromType(p.getType());
        int currentPp = p.getPpType().getNumber();

        // Correct mismatch: e.g., APPROBATION was assigned to pp.1 instead of pp.12
        if (correctPp != currentPp) {
            // Only correct when the type clearly belongs to a different pp
            boolean shouldCorrect = switch (p.getType()) {
                case APPROBATION -> currentPp != 12;              // APPROBATION → pp.12
                case PATENT, DECLARATIVE_PATENT, COPYRIGHT -> currentPp != 2;  // Patents → pp.2
                case TEXTBOOK, STUDY_GUIDE, MONOGRAPH -> currentPp != 3;  // Textbooks/Study guides → pp.3
                case METHODICAL -> currentPp != 4;                // Methodical → pp.4
                default -> false;  // ARTICLE, OTHER — trust AI's ppNumber
            };

            if (shouldCorrect) {
                AchievementType correctedType = AchievementType.fromNumber(correctPp);
                if (correctedType != null) {
                    log.info("Correcting ppType mismatch: '{}' type={} was pp.{} → pp.{}",
                            p.getTitle() != null ? p.getTitle().substring(0, Math.min(40, p.getTitle().length())) : "?",
                            p.getType(), currentPp, correctPp);
                    p.setPpType(correctedType);
                    p.setSourceSection("pp." + correctPp);
                }
            }
        }
    }

    /**
     * Збагачує articleCategory для ARTICLE.
     * Для APPROBATION — Scopus-підтвердження йде через approbationSubtype=SCOPUS_WOS.
     */
    private void enrichArticleCategory(Publication p) {
        // Для APPROBATION — Scopus перевірка ставить approbationSubtype, не articleCategory
        if (p.getType() == PublicationType.APPROBATION) {
            if (p.getApprobationSubtype() != ApprobationSubtype.SCOPUS_WOS
                    && scopusApiService != null && p.getTeacher() != null) {
                var result = scopusApiService.verifyPublication(
                        p.getTitle(), p.getDoi(),
                        p.getTeacher().getScopusId(),
                        p.getTeacher().getLastName(),
                        p.getTeacher().getFirstName());
                if (result.isConfirmed()) {
                    p.setApprobationSubtype(ApprobationSubtype.SCOPUS_WOS);
                    log.info("Scopus API confirmed APPROBATION '{}' for {} → SCOPUS_WOS",
                            truncateForLog(p.getTitle(), 50), p.getTeacher().getLastName());
                }
            }
            return;
        }

        if (p.getType() != PublicationType.ARTICLE) return;

        // Scopus API — завжди перевіряємо (пріоритет), крім вже підтверджених SCOPUS
        if (p.getArticleCategory() != ArticleCategory.SCOPUS
                && scopusApiService != null && p.getTeacher() != null) {
            var result = scopusApiService.verifyPublication(
                    p.getTitle(), p.getDoi(),
                    p.getTeacher().getScopusId(),
                    p.getTeacher().getLastName(),
                    p.getTeacher().getFirstName());
            if (result.isConfirmed()) {
                p.setArticleCategory(ArticleCategory.SCOPUS);
                log.info("Scopus API confirmed '{}' for {} (method: {})",
                        truncateForLog(p.getTitle(), 50),
                        p.getTeacher().getLastName(), result.getSearchMethod());
                return;
            }
        }

        // Якщо вже є категорія (AI-hint SCOPUS/WOS) — зберігаємо
        if (p.getArticleCategory() != null) return;

        // Фахові видання — тільки для ARTICLE
        verifyAndSetCategory(p);
    }

    /**
     * Перевіряє журнал/видавництво у базі фахових та встановлює articleCategory.
     */
    private void verifyAndSetCategory(Publication p) {
        if (tryVerifyFakhoviField(p, p.getJournalName())) return;
        tryVerifyFakhoviField(p, p.getPublisher());
    }

    private boolean tryVerifyFakhoviField(Publication p, String name) {
        if (name == null || name.isBlank()) return false;
        try {
            VerificationResult vr = fakhovyiJournalService.verifyJournal(name, null);
            if (vr.isFakhove() && vr.category() != null) {
                p.setArticleCategory(vr.category() == JournalCategory.CATEGORY_A
                        ? ArticleCategory.CATEGORY_A : ArticleCategory.CATEGORY_B);
                return true;
            }
        } catch (Exception ignore) {
            // verification is best-effort
        }
        return false;
    }

    /**
     * Перевіряє чи APPROBATION-публікація індексована в Scopus.
     * НЕ змінює тип (залишається APPROBATION, ppType=PP_12).
     * Встановлює approbationSubtype=SCOPUS_WOS (не articleCategory).
     */
    private boolean markScopusForApprobation(Publication p) {
        if (scopusApiService == null || p.getTeacher() == null) return false;

        var result = scopusApiService.verifyPublication(
                p.getTitle(), p.getDoi(),
                p.getTeacher().getScopusId(),
                p.getTeacher().getLastName(),
                p.getTeacher().getFirstName());
        if (result.isConfirmed()) {
            p.setApprobationSubtype(ApprobationSubtype.SCOPUS_WOS);
            log.info("APPROBATION marked as Scopus (API): '{}'", truncateForLog(p.getTitle(), 50));
            return true;
        }
        return false;
    }

    private String truncateForLog(String s, int max) {
        if (s == null) return "?";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // =====================================================================
    // AI-парсинг ppData (пп.5-20) — структуровані дані через ШІ
    // =====================================================================

    /**
     * Збирає тексти секцій пп.5-20 з achievements, відправляє на ШІ одним запитом
     * і зберігає структуровані ppData записи.
     *
     * @return кількість збережених ppData записів
     */
    @SuppressWarnings("unchecked")
    private int parseAndSavePpDataWithAi(Map<String, Object> achievementData, Teacher teacher) {
        List<Map<String, Object>> achievements = (List<Map<String, Object>>) achievementData.get("achievements");
        if (achievements == null) return 0;

        // Збираємо тексти секцій 5-20
        StringBuilder ppTexts = new StringBuilder();
        boolean hasPpData = false;
        for (Map<String, Object> ach : achievements) {
            Integer ppNum = getInt(ach, "ppNumber");
            String desc = getStr(ach, "description");
            if (ppNum != null && ppNum >= 5 && ppNum <= 20 && desc != null && !desc.isBlank()) {
                ppTexts.append("\n\n=== пп.").append(ppNum).append(" ===\n").append(desc);
                hasPpData = true;
            }
        }

        if (!hasPpData) return 0;

        String ppText = ppTexts.toString();
        if (ppText.length() > 10000) {
            ppText = ppText.substring(0, 10000);
        }

        // Виклик ШІ
        List<Map<String, Object>> parsed;
        try {
            parsed = parseWithAi(SYSTEM_PROMPT_PPDATA,
                    "Ось тексти секцій пп.5-20 досягнень викладача " + teacher.getLastName() + ":\n" + ppText);
        } catch (Exception e) {
            log.warn("AI ppData parse failed for {}: {}", teacher.getLastName(), e.getMessage());
            // Fallback: використовуємо regex-парсер
            return fallbackRegexPpData(achievements, teacher);
        }

        if (parsed.isEmpty()) {
            return fallbackRegexPpData(achievements, teacher);
        }

        Map<String, Object> data = parsed.get(0);
        int count = 0;

        // пп.5 — Дисертація (зберігається в Teacher)
        Map<String, Object> pp5 = (Map<String, Object>) data.get("pp5");
        if (pp5 != null) {
            count += savePp5(pp5, teacher);
        }

        // пп.6 — Наукове керівництво
        count += savePp6(data, teacher);

        // пп.7 — Атестація
        count += savePp7(data, teacher);

        // пп.8 — Редакційна діяльність
        count += savePp8(data, teacher);

        // пп.9 — Експертна рада
        count += savePp9(data, teacher);

        // пп.10 — Міжнародні проекти
        count += savePp10(data, teacher);

        // пп.11 — Наукове консультування
        count += savePp11(data, teacher);

        // пп.13 — Викладання іноземною мовою
        count += savePp13(data, teacher);

        // пп.14, пп.15 — Олімпіади
        count += savePp14_15(data, teacher);

        // пп.16 — УБД (зберігається в Teacher)
        Map<String, Object> pp16 = (Map<String, Object>) data.get("pp16");
        if (pp16 != null) {
            count += savePp16(pp16, teacher);
        }

        // пп.17, пп.18 — Миротворчі/НАТО
        count += savePp17_18(data, teacher);

        // пп.19 — Професійні об'єднання
        count += savePp19(data, teacher);

        // пп.20 — Досвід практичної роботи
        count += savePp20(data, teacher);

        return count;
    }

    /**
     * Fallback: regex-парсинг ppData якщо ШІ не спрацював.
     */
    private int fallbackRegexPpData(List<Map<String, Object>> achievements, Teacher teacher) {
        int count = 0;
        for (Map<String, Object> ach : achievements) {
            Integer ppNum = getInt(ach, "ppNumber");
            String desc = getStr(ach, "description");
            if (ppNum != null && ppNum >= 5 && ppNum <= 20 && desc != null && !desc.isBlank()) {
                try {
                    count += ppDataParser.parseSectionAndSave(ppNum, desc, teacher);
                } catch (Exception e) {
                    log.warn("Regex ppData fallback failed for pp.{}: {}", ppNum, e.getMessage());
                }
            }
        }
        return count;
    }

    private int savePp5(Map<String, Object> pp5, Teacher teacher) {
        // Доповнюємо primary AcademicDegree (або створюємо новий) полями з pp5.
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(teacher.getId());
        AcademicDegree d = AcademicDegreeRanking.primary(degrees);
        boolean isNew = false;
        if (d == null) {
            d = AcademicDegree.builder().teacher(teacher).build();
            isNew = true;
        }
        boolean changed = false;

        String topic = getStr(pp5, "dissertationTopic");
        if (topic != null && !topic.isBlank() && (d.getDissertationTopic() == null || d.getDissertationTopic().isBlank())) {
            d.setDissertationTopic(topic); changed = true;
        }
        String degree = getStr(pp5, "academicDegree");
        if (degree != null && !degree.isBlank() && (d.getDegree() == null || d.getDegree().isBlank())) {
            d.setDegree(degree); changed = true;
        }
        String diploma = getStr(pp5, "degreeDiploma");
        if (diploma != null && !diploma.isBlank() && (d.getDiploma() == null || d.getDiploma().isBlank())) {
            d.setDiploma(diploma); changed = true;
        }
        String date = getStr(pp5, "degreeDiplomaDate");
        if (date != null && d.getDiplomaDate() == null) {
            try { d.setDiplomaDate(LocalDate.parse(date)); changed = true; }
            catch (Exception ignored) {}
        }
        if (changed && (isNew || d.getId() != null)) {
            academicDegreeRepository.save(d);
        }
        return 0; // Дані у academic_degrees, не окремий ppData запис
    }

    @SuppressWarnings("unchecked")
    private int savePp6(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp6");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                ScientificSupervision ss = ScientificSupervision.builder()
                        .teacher(teacher)
                        .studentName(getStr(item, "studentName"))
                        .topic(getStr(item, "topic"))
                        .diplomaNumber(getStr(item, "diplomaNumber"))
                        .createdBy("ai-import")
                        .build();
                String dateStr = getStr(item, "defenseDate");
                if (dateStr != null) {
                    try { ss.setDefenseDate(LocalDate.parse(dateStr)); } catch (Exception ignored) {}
                }
                String degreeStr = getStr(item, "degreeType");
                if (degreeStr != null) {
                    try { ss.setDegreeType(DegreeType.valueOf(degreeStr)); } catch (Exception ignored) {}
                }
                scientificSupervisionRepo.save(ss);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.6 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp7(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp7");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                AttestationActivity aa = AttestationActivity.builder()
                        .teacher(teacher)
                        .councilName(getStr(item, "councilName"))
                        .studentName(getStr(item, "studentName"))
                        .createdBy("ai-import")
                        .build();
                String roleStr = getStr(item, "role");
                if (roleStr != null) {
                    // Підтримка legacy SPORADIC_COUNCIL → REVIEWER
                    if ("SPORADIC_COUNCIL".equals(roleStr)) roleStr = "REVIEWER";
                    try { aa.setRole(AttestationRole.valueOf(roleStr)); } catch (Exception ignored) {}
                }
                String dateStr = getStr(item, "defenseDate");
                if (dateStr != null) {
                    try { aa.setDefenseDate(LocalDate.parse(dateStr)); } catch (Exception ignored) {}
                }
                // Для COUNCIL_MEMBER (постійна рада) — період dateFrom/dateTo
                String dateFromStr = getStr(item, "dateFrom");
                if (dateFromStr != null) {
                    try { aa.setDateFrom(LocalDate.parse(dateFromStr)); } catch (Exception ignored) {}
                }
                String dateToStr = getStr(item, "dateTo");
                if (dateToStr != null) {
                    try { aa.setDateTo(LocalDate.parse(dateToStr)); } catch (Exception ignored) {}
                }
                attestationActivityRepo.save(aa);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.7 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp8(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp8");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                EditorialActivity ea = EditorialActivity.builder()
                        .teacher(teacher)
                        .journalOrProjectName(getStr(item, "journalOrProjectName"))
                        .description(getStr(item, "description"))
                        .createdBy("ai-import")
                        .build();
                String roleStr = getStr(item, "role");
                if (roleStr != null) {
                    try { ea.setRole(EditorialRole.valueOf(roleStr)); } catch (Exception ignored) {}
                }
                String df = getStr(item, "dateFrom");
                if (df != null) { try { ea.setDateFrom(LocalDate.parse(df)); } catch (Exception ignored) {} }
                String dt = getStr(item, "dateTo");
                if (dt != null) { try { ea.setDateTo(LocalDate.parse(dt)); } catch (Exception ignored) {} }
                editorialActivityRepo.save(ea);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.8 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp9(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp9");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                ExpertCouncil ec = ExpertCouncil.builder()
                        .teacher(teacher)
                        .councilName(getStr(item, "councilName"))
                        .role(getStr(item, "role"))
                        .orderNumber(getStr(item, "orderNumber"))
                        .createdBy("ai-import")
                        .build();
                String typeStr = getStr(item, "type");
                if (typeStr != null) {
                    try { ec.setType(ExpertCouncilType.valueOf(typeStr)); } catch (Exception ignored) {}
                }
                String df = getStr(item, "dateFrom");
                if (df != null) { try { ec.setDateFrom(LocalDate.parse(df)); } catch (Exception ignored) {} }
                String dt = getStr(item, "dateTo");
                if (dt != null) { try { ec.setDateTo(LocalDate.parse(dt)); } catch (Exception ignored) {} }
                expertCouncilRepo.save(ec);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.9 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp10(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp10");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                InternationalProject ip = InternationalProject.builder()
                        .teacher(teacher)
                        .projectName(getStr(item, "projectName"))
                        .role(getStr(item, "role"))
                        .description(getStr(item, "description"))
                        .createdBy("ai-import")
                        .build();
                String progStr = getStr(item, "program");
                if (progStr != null) {
                    try { ip.setProgram(InternationalProgram.valueOf(progStr)); } catch (Exception ignored) {}
                }
                String df = getStr(item, "dateFrom");
                if (df != null) { try { ip.setDateFrom(LocalDate.parse(df)); } catch (Exception ignored) {} }
                String dt = getStr(item, "dateTo");
                if (dt != null) { try { ip.setDateTo(LocalDate.parse(dt)); } catch (Exception ignored) {} }
                internationalProjectRepo.save(ip);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.10 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp11(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp11");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                ScientificConsulting sc = ScientificConsulting.builder()
                        .teacher(teacher)
                        .organizationName(getStr(item, "organizationName"))
                        .contractNumber(getStr(item, "contractNumber"))
                        .yearsCount(getInt(item, "yearsCount"))
                        .createdBy("ai-import")
                        .build();
                String df = getStr(item, "dateFrom");
                if (df != null) { try { sc.setDateFrom(LocalDate.parse(df)); } catch (Exception ignored) {} }
                String dt = getStr(item, "dateTo");
                if (dt != null) { try { sc.setDateTo(LocalDate.parse(dt)); } catch (Exception ignored) {} }
                scientificConsultingRepo.save(sc);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.11 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp13(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp13");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                ForeignLanguageTeaching flt = ForeignLanguageTeaching.builder()
                        .teacher(teacher)
                        .disciplineName(getStr(item, "disciplineName"))
                        .language(getStr(item, "language"))
                        .hours(getInt(item, "hours"))
                        .academicYear(getStr(item, "academicYear"))
                        .semester(getInt(item, "semester"))
                        .createdBy("ai-import")
                        .build();
                foreignLanguageTeachingRepo.save(flt);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.13 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp14_15(Map<String, Object> data, Teacher teacher) {
        int count = 0;
        for (String key : List.of("pp14", "pp15")) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get(key);
            if (items == null) continue;
            OlympiadLevel defaultLevel = "pp14".equals(key) ? OlympiadLevel.STUDENT : OlympiadLevel.SCHOOL;
            for (Map<String, Object> item : items) {
                try {
                    OlympiadGuidance og = OlympiadGuidance.builder()
                            .teacher(teacher)
                            .olympiadName(getStr(item, "olympiadName"))
                            .studentName(getStr(item, "studentName"))
                            .result(getStr(item, "result"))
                            .year(getInt(item, "year"))
                            .departmentName(getStr(item, "departmentName"))
                            .participantCount(getInt(item, "participantCount"))
                            .academicYear(getStr(item, "academicYear"))
                            .orderNumber(getStr(item, "orderNumber"))
                            .description(getStr(item, "description"))
                            .createdBy("ai-import")
                            .build();

                    // activityType
                    String actTypeStr = getStr(item, "activityType");
                    if (actTypeStr != null) {
                        try { og.setActivityType(Pp14ActivityType.valueOf(actTypeStr)); } catch (Exception ignored) {}
                    }

                    // competitionScope
                    String scopeStr = getStr(item, "competitionScope");
                    if (scopeStr != null) {
                        try { og.setCompetitionScope(CompetitionScope.valueOf(scopeStr)); } catch (Exception ignored) {}
                    }

                    // level: для гуртків/товариств level не потрібен; для олімпіад — default
                    if (og.getActivityType() == null ||
                            og.getActivityType() == Pp14ActivityType.OLYMPIAD ||
                            og.getActivityType() == Pp14ActivityType.SCIENTIFIC_COMPETITION ||
                            og.getActivityType() == Pp14ActivityType.COMPETITION) {
                        og.setLevel(defaultLevel);
                    }

                    // role
                    String roleStr = getStr(item, "role");
                    if (roleStr != null) {
                        try { og.setRole(OlympiadRole.valueOf(roleStr)); } catch (Exception ignored) {}
                    }

                    // orderDate
                    String orderDateStr = getStr(item, "orderDate");
                    if (orderDateStr != null) {
                        try { og.setOrderDate(LocalDate.parse(orderDateStr)); } catch (Exception ignored) {}
                    }

                    olympiadGuidanceRepo.save(og);
                    count++;
                } catch (Exception e) {
                    log.warn("AI {} save error: {}", key, e.getMessage());
                }
            }
        }
        return count;
    }

    private int savePp16(Map<String, Object> pp16, Teacher teacher) {
        boolean combatStatus = getBool(pp16, "combatVeteranStatus");
        if (combatStatus && !teacher.isCombatVeteranStatus()) {
            teacher.setCombatVeteranStatus(true);
            String doc = getStr(pp16, "combatVeteranDoc");
            if (doc != null) teacher.setCombatVeteranDoc(doc);
            String dateStr = getStr(pp16, "combatVeteranDocDate");
            if (dateStr != null) {
                try { teacher.setCombatVeteranDocDate(LocalDate.parse(dateStr)); } catch (Exception ignored) {}
            }
            String issuedBy = getStr(pp16, "combatVeteranDocIssuedBy");
            if (issuedBy != null) teacher.setCombatVeteranDocIssuedBy(issuedBy);
        }
        return 0; // Дані в Teacher
    }

    @SuppressWarnings("unchecked")
    private int savePp17_18(Map<String, Object> data, Teacher teacher) {
        int count = 0;
        for (String key : List.of("pp17", "pp18")) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get(key);
            if (items == null) continue;
            MissionType missionType = "pp17".equals(key) ? MissionType.UN_PEACEKEEPING : MissionType.NATO_EXERCISE;
            for (Map<String, Object> item : items) {
                try {
                    MilitaryMission mm = MilitaryMission.builder()
                            .teacher(teacher)
                            .missionType(missionType)
                            .missionName(getStr(item, "missionName"))
                            .country(getStr(item, "country"))
                            .createdBy("ai-import")
                            .build();
                    String df = getStr(item, "dateFrom");
                    if (df != null) { try { mm.setDateFrom(LocalDate.parse(df)); } catch (Exception ignored) {} }
                    String dt = getStr(item, "dateTo");
                    if (dt != null) { try { mm.setDateTo(LocalDate.parse(dt)); } catch (Exception ignored) {} }
                    militaryMissionRepo.save(mm);
                    count++;
                } catch (Exception e) {
                    log.warn("AI {} save error: {}", key, e.getMessage());
                }
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp19(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp19");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                ProfessionalAssociation pa = ProfessionalAssociation.builder()
                        .teacher(teacher)
                        .organizationName(getStr(item, "organizationName"))
                        .role(getStr(item, "role"))
                        .certificateNumber(getStr(item, "certificateNumber"))
                        .createdBy("ai-import")
                        .build();
                String df = getStr(item, "dateFrom");
                if (df != null) { try { pa.setDateFrom(LocalDate.parse(df)); } catch (Exception ignored) {} }
                String dt = getStr(item, "dateTo");
                if (dt != null) { try { pa.setDateTo(LocalDate.parse(dt)); } catch (Exception ignored) {} }
                professionalAssociationRepo.save(pa);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.19 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int savePp20(Map<String, Object> data, Teacher teacher) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("pp20");
        if (items == null) return 0;
        int count = 0;
        for (Map<String, Object> item : items) {
            try {
                PracticalExperience pe = PracticalExperience.builder()
                        .teacher(teacher)
                        .organizationName(getStr(item, "organizationName"))
                        .position(getStr(item, "position"))
                        .yearsCount(getInt(item, "yearsCount"))
                        .specialtyName(getStr(item, "specialtyName"))
                        .createdBy("ai-import")
                        .build();
                String df = getStr(item, "dateFrom");
                if (df != null) { try { pe.setDateFrom(LocalDate.parse(df)); } catch (Exception ignored) {} }
                String dt = getStr(item, "dateTo");
                if (dt != null) { try { pe.setDateTo(LocalDate.parse(dt)); } catch (Exception ignored) {} }
                practicalExperienceRepo.save(pe);
                count++;
            } catch (Exception e) {
                log.warn("AI pp.20 save error: {}", e.getMessage());
            }
        }
        return count;
    }

    /**
     * Дедуплікація публікацій: normalize(title) + year + normalize(journal).
     * Також перевіряє вже існуючі публікації в БД.
     */
    private List<Publication> deduplicatePublications(List<Publication> pubs, Teacher teacher) {
        List<Publication> existing = publicationRepository.findByTeacherId(teacher.getId());
        Set<String> existingKeys = new HashSet<>();
        for (Publication ep : existing) {
            existingKeys.add(normalizeDedup(ep.getTitle()) + "|" + ep.getYear() + "|" + normalizeDedup(ep.getJournalName()));
        }

        Set<String> seen = new HashSet<>();
        List<Publication> result = new ArrayList<>();
        for (Publication p : pubs) {
            String key = normalizeDedup(p.getTitle()) + "|" + p.getYear() + "|" + normalizeDedup(p.getJournalName());
            if (seen.add(key) && !existingKeys.contains(key)) {
                result.add(p);
            }
        }
        return result;
    }

    private String normalizeDedup(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^а-яіїєґa-z0-9]", "").trim();
    }

    private void saveQualificationsFromAi(Map<String, Object> data, Teacher teacher,
                                            DataImportService.ImportResult result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> qualifications = (List<Map<String, Object>>) data.get("qualifications");
        if (qualifications == null) return;

        for (Map<String, Object> q : qualifications) {
            try {
                String title = getStr(q, "title");
                if (title == null || title.trim().length() < 3) continue;

                QualificationImprovement qi = new QualificationImprovement();
                qi.setTeacher(teacher);
                qi.setTitle(title.trim());
                qi.setOrganization(getStr(q, "organization"));

                String startDateStr = getStr(q, "startDate");
                String endDateStr = getStr(q, "endDate");
                if (startDateStr != null) {
                    try { qi.setStartDate(LocalDate.parse(startDateStr)); } catch (Exception ignored) {}
                }
                if (endDateStr != null) {
                    try { qi.setEndDate(LocalDate.parse(endDateStr)); } catch (Exception ignored) {}
                }

                Integer hours = getInt(q, "hours");
                if (hours != null) qi.setHours(hours);

                Object creditsObj = q.get("credits");
                if (creditsObj instanceof Number n) {
                    qi.setCredits(n.doubleValue());
                } else if (creditsObj instanceof String s) {
                    try { qi.setCredits(Double.parseDouble(s)); } catch (NumberFormatException ignored) {}
                }

                qi.setCertificateNumber(getStr(q, "certificateNumber"));
                String certDateStr = getStr(q, "certificateDate");
                if (certDateStr != null) {
                    try { qi.setCertificateDate(LocalDate.parse(certDateStr)); } catch (Exception ignored) {}
                }
                qi.setCertificateUrl(getStr(q, "certificateUrl"));

                qualificationRepository.save(qi);
                result.qualificationsImported++;
            } catch (Exception e) {
                log.warn("Failed to save qualification for {}: {}", teacher.getLastName(), e.getMessage());
            }
        }
    }

    private void saveCareerRecordsFromAi(Map<String, Object> data, Teacher teacher,
                                           DataImportService.ImportResult result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> careerRecords = (List<Map<String, Object>>) data.get("careerRecords");
        if (careerRecords == null) return;

        for (Map<String, Object> cr : careerRecords) {
            try {
                String position = getStr(cr, "position");
                if (position == null || position.trim().length() < 3) continue;

                CareerRecord record = new CareerRecord();
                record.setTeacher(teacher);
                record.setPosition(position.trim().length() > 500
                        ? position.trim().substring(0, 500) : position.trim());
                record.setOrganization(getStr(cr, "organization"));

                String startDateStr = getStr(cr, "startDate");
                String endDateStr = getStr(cr, "endDate");
                if (startDateStr != null) {
                    try { record.setStartDate(LocalDate.parse(startDateStr)); } catch (Exception ignored) {}
                }
                if (endDateStr != null) {
                    try { record.setEndDate(LocalDate.parse(endDateStr)); } catch (Exception ignored) {}
                }

                careerRecordRepository.save(record);
                result.careerRecordsImported++;
            } catch (Exception e) {
                log.warn("Failed to save career record for {}: {}", teacher.getLastName(), e.getMessage());
            }
        }
    }

    private void saveLanguageSkillsFromAi(Map<String, Object> data, Teacher teacher,
                                            DataImportService.ImportResult result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> languages = (List<Map<String, Object>>) data.get("foreignLanguages");
        if (languages == null) return;

        for (Map<String, Object> lang : languages) {
            try {
                String language = getStr(lang, "language");
                if (language == null || language.trim().length() < 2) continue;

                LanguageSkill skill = new LanguageSkill();
                skill.setTeacher(teacher);
                skill.setLanguage(language.trim());
                skill.setLevel(getStr(lang, "level"));
                skill.setCertificateDetails(getStr(lang, "certificateDetails"));
                skill.setCertificateNumber(getStr(lang, "certificateNumber"));
                skill.setCertificateOrganization(getStr(lang, "certificateOrganization"));
                String certDateStr = getStr(lang, "certificateDate");
                if (certDateStr != null && !certDateStr.isBlank()) {
                    try { skill.setCertificateDate(LocalDate.parse(certDateStr)); }
                    catch (Exception ignored) {}
                }

                languageSkillRepository.save(skill);
                result.languageSkillsImported++;
            } catch (Exception e) {
                log.warn("Failed to save language skill for {}: {}", teacher.getLastName(), e.getMessage());
            }
        }
    }

    private String formatRowForAi(XWPFTableRow row) {
        List<XWPFTableCell> cells = row.getTableCells();

        // Визначаємо зсув: якщо перша колонка — номер рядка (№ з/п), пропускаємо її
        int offset = 0;
        if (!cells.isEmpty()) {
            String firstCellText = getCellText(cells.get(0));
            if (firstCellText.matches("^\\d{1,3}\\.?$")) {
                offset = 1;
            }
        }

        List<String> cellTexts = new ArrayList<>();
        for (int i = offset; i < cells.size(); i++) {
            String cellText = getCellText(cells.get(i));
            // Логічний номер стовпця (без №з/п)
            int logicalCol = i - offset;
            cellTexts.add("[" + logicalCol + "] " + cellText);
        }
        return String.join(" | ", cellTexts);
    }

    private List<Map<String, Object>> parseWithAi(String systemPrompt, String userText) throws Exception {
        ChatClient chatClient = chatClientBuilder.build();
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userText)
                .call()
                .content();

        if (response == null || response.isBlank()) {
            log.warn("AI returned empty response");
            return Collections.emptyList();
        }

        log.info("AI response ({} chars): {}", response.length(),
                response.length() > 300 ? response.substring(0, 300) + "..." : response);

        String json = extractJson(response);
        // Може бути масив або об'єкт
        if (json.startsWith("[")) {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } else if (json.startsWith("{")) {
            Map<String, Object> single = objectMapper.readValue(json, new TypeReference<>() {});
            return List.of(single);
        }
        log.warn("AI response did not contain JSON: {}", response.substring(0, Math.min(200, response.length())));
        return Collections.emptyList();
    }

    private String extractJson(String text) {
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');

        // Якщо об'єкт починається раніше за масив — це одиничний JSON об'єкт
        // (масив всередині — це поле, наприклад "disciplines": [...])
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            int objEnd = text.lastIndexOf('}');
            if (objEnd > objStart) {
                return text.substring(objStart, objEnd + 1);
            }
        }

        // Якщо масив починається раніше — це JSON масив об'єктів: [{...}, {...}]
        if (arrStart >= 0) {
            int arrEnd = text.lastIndexOf(']');
            if (arrEnd > arrStart) {
                return text.substring(arrStart, arrEnd + 1);
            }
        }

        return "{}";
    }

    private Teacher matchTeacherByLabel(String label, Map<String, Teacher> savedTeachers) {
        if (label == null || label.trim().isEmpty() || label.trim().length() > 100) return null;

        String[] parts = label.trim().split("[\\s,]+");
        if (parts.length == 0) return null;

        String lastName = parts[0]
                .replaceAll("[^А-ЯІЇЄҐа-яіїєґ'ʼ]", "")
                .toUpperCase();

        if (lastName.isEmpty()) return null;

        if (savedTeachers.containsKey(lastName)) {
            return savedTeachers.get(lastName);
        }

        for (Map.Entry<String, Teacher> entry : savedTeachers.entrySet()) {
            if (entry.getKey().startsWith(lastName) || lastName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Teacher mapToTeacher(Map<String, Object> data, Department department, ScientificData sci) {
        Teacher t = new Teacher();
        t.setLastName(latinToCyrillic(getStr(data, "lastName")));
        t.setFirstName(latinToCyrillic(getStr(data, "firstName")));
        t.setPatronymic(latinToCyrillic(getStr(data, "patronymic")));
        // Дата народження: спершу нове поле dateOfBirth (ISO YYYY-MM-DD),
        // потім fallback на старе birthYear (для зворотної сумісності зі старими відповідями AI).
        String dobStr = getStr(data, "dateOfBirth");
        if (dobStr != null && !dobStr.isBlank()) {
            try { t.setDateOfBirth(LocalDate.parse(dobStr)); } catch (Exception ignored) {}
        } else {
            Integer birthYearLegacy = getInt(data, "birthYear");
            if (birthYearLegacy != null) {
                t.setDateOfBirth(LocalDate.of(birthYearLegacy, 1, 1));
            }
        }
        t.setMilitaryRank(getStr(data, "militaryRank"));
        t.setPosition(ua.edu.teacherlicence.teacher.util.PositionCleaner.clean(getStr(data, "position")));
        t.setEmploymentType(getStr(data, "employmentType", "MAIN"));
        // Convert experienceYears (from AI) to experienceStartDate
        Integer expYears = getInt(data, "experienceYears");
        if (expYears != null && expYears > 0) {
            t.setExperienceStartDate(java.time.LocalDate.now().minusYears(expYears).withMonth(1).withDayOfMonth(1));
        }
        sci.degreeName = getStr(data, "academicDegree");
        sci.titleName = getStr(data, "academicTitle");
        // Set flat university fields from AI response (backward compat)
        Map<String, Object> eduData = extractLastEducation(data);
        t.setUniversity(getStr(eduData, "university"));
        t.setUniversitySpeciality(getStr(eduData, "universitySpeciality"));
        t.setUniversityDiploma(normalizeDiplomaStr(getStr(eduData, "universityDiploma")));
        t.setUniversityGraduationYear(getInt(eduData, "universityGraduationYear"));
        String uDiplomaDate = getStr(eduData, "universityDiplomaDate");
        if (uDiplomaDate != null) {
            try { t.setUniversityDiplomaDate(LocalDate.parse(uDiplomaDate)); } catch (Exception ignored) {}
        }
        sci.dissertationTopic = getStr(data, "dissertationTopic");
        sci.dissertationSpeciality = getStr(data, "dissertationSpeciality");
        sci.degreeDiploma = getStr(data, "degreeDiploma");
        String dDiplomaDate = getStr(data, "degreeDiplomaDate");
        if (dDiplomaDate != null) {
            try { sci.degreeDiplomaDate = LocalDate.parse(dDiplomaDate); } catch (Exception ignored) {}
        }
        sci.titleAttestat = getStr(data, "titleAttestat");
        String tAttestatDate = getStr(data, "titleAttestatDate");
        if (tAttestatDate != null) {
            try { sci.titleAttestatDate = LocalDate.parse(tAttestatDate); } catch (Exception ignored) {}
        }
        t.setCombatVeteranStatus(getBool(data, "combatVeteranStatus"));
        t.setCombatExperienceDates(convertIsoDatesInText(getStr(data, "combatExperienceDates")));
        String combatDocDate = getStr(data, "combatVeteranDocDate");
        if (combatDocDate != null) {
            try { t.setCombatVeteranDocDate(LocalDate.parse(combatDocDate)); } catch (Exception ignored) {}
        }
        t.setCombatVeteranDocIssuedBy(getStr(data, "combatVeteranDocIssuedBy"));
        t.setOrcidId(TeacherService.normalizeOrcid(getStr(data, "orcidId")));
        t.setEmail(getStr(data, "email"));
        t.setScopusId(TeacherService.normalizeScopusId(getStr(data, "scopusId")));
        t.setWosId(TeacherService.normalizeWosId(getStr(data, "wosId")));
        t.setGoogleScholarUrl(getStr(data, "googleScholarUrl"));
        t.setDepartment(department);

        // Детерміністична пост-обробка: виправляємо помилки AI
        postProcessTeacher(t, sci);
        return t;
    }

    /** Створює AcademicDegree та AcademicTitle сутності для викладача (після save). */
    private void persistScientific(Teacher teacher, ScientificData sci) {
        if (sci == null) return;
        if (sci.hasDegree()) {
            AcademicDegree d = AcademicDegree.builder()
                    .teacher(teacher)
                    .degree(sci.degreeName)
                    .speciality(sci.dissertationSpeciality)
                    .dissertationTopic(sci.dissertationTopic)
                    .diploma(sci.degreeDiploma)
                    .diplomaDate(sci.degreeDiplomaDate)
                    .build();
            academicDegreeRepository.save(d);
        }
        if (sci.hasTitle()) {
            AcademicTitle t = AcademicTitle.builder()
                    .teacher(teacher)
                    .titleName(sci.titleName)
                    .attestat(sci.titleAttestat)
                    .attestatDate(sci.titleAttestatDate)
                    .build();
            academicTitleRepository.save(t);
        }
    }

    /**
     * Детерміністична пост-обробка після AI-парсингу.
     * Виправляє типові помилки AI: плутання посади/звання, невірний ступінь, пропущений УБД.
     */
    private void postProcessTeacher(Teacher t, ScientificData sci) {
        // 1. Виправляємо titleName з коду атестата (детерміністично)
        String attestat = sci.titleAttestat;
        if (attestat != null && !attestat.isEmpty()) {
            String titleFromAttestat = extractTitleFromAttestat(attestat);
            if (titleFromAttestat != null) {
                if (sci.titleName == null || !sci.titleName.equals(titleFromAttestat)) {
                    log.info("POST-PROCESS: Виправлено звання '{}' → '{}' (з атестата: {})",
                            sci.titleName, titleFromAttestat, attestat);
                    sci.titleName = titleFromAttestat;
                }
            }
        }

        // 2. Якщо titleName збігається з початком посади — AI сплутав, очищуємо
        if (sci.titleName != null && t.getPosition() != null && attestat == null) {
            String posLower = t.getPosition().toLowerCase();
            String titleLower = sci.titleName.toLowerCase();
            if (posLower.startsWith(titleLower + " кафедри")
                    || posLower.startsWith(titleLower + " ")) {
                log.info("POST-PROCESS: Видалено звання '{}' — збігається з посадою '{}' і немає атестата",
                        sci.titleName, t.getPosition());
                sci.titleName = null;
            }
        }

        // 3. Виправляємо degreeName з degreeDiploma — якщо диплом є, а ступінь null
        if (sci.degreeName == null && sci.degreeDiploma != null) {
            log.info("POST-PROCESS: Є диплом '{}', але degreeName=null — потрібна ручна перевірка",
                    sci.degreeDiploma);
        }

        // 4. Розшифровка скорочення ступеня
        if (sci.degreeName != null) {
            sci.degreeName = expandDegreeAbbreviation(sci.degreeName);
        }

        // 5. Якщо є combatExperienceDates але combatVeteranStatus=false — виправляємо
        if (!t.isCombatVeteranStatus() && t.getCombatExperienceDates() != null
                && !t.getCombatExperienceDates().isEmpty()) {
            log.info("POST-PROCESS: Є дати бойового досвіду '{}' — встановлюємо combatVeteranStatus=true",
                    t.getCombatExperienceDates());
            t.setCombatVeteranStatus(true);
        }
    }

    /**
     * Витягує вчене звання з коду серії атестата.
     * ДЦ → Доцент, ПР → Професор, СД → Старший дослідник
     */
    private String extractTitleFromAttestat(String attestat) {
        if (attestat == null) return null;
        // Шукаємо двобуквений код серії: "12ДЦ", "02ПР", "01СД" тощо
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\d{2}([А-ЯІЇЄҐа-яіїєґ]{2})");
        java.util.regex.Matcher matcher = pattern.matcher(attestat);
        if (matcher.find()) {
            String code = matcher.group(1).toUpperCase();
            return switch (code) {
                case "ДЦ" -> "Доцент";
                case "ПР" -> "Професор";
                case "СД" -> "Старший дослідник";
                default -> null;
            };
        }
        // Fallback: шукаємо ключові слова
        String lower = attestat.toLowerCase();
        if (lower.contains("професор")) return "Професор";
        if (lower.contains("доцент")) return "Доцент";
        if (lower.contains("старший дослідник")) return "Старший дослідник";
        return null;
    }

    /**
     * Розшифровує скорочення ступеня: к.т.н. → Кандидат технічних наук
     */
    private static final Map<String, String> SCIENCE_ABBREV = Map.ofEntries(
            Map.entry("т", "технічних"), Map.entry("тех", "технічних"),
            Map.entry("в", "військових"), Map.entry("віс", "військових"),
            Map.entry("ф", "фізико-математичних"), Map.entry("ф-м", "фізико-математичних"),
            Map.entry("е", "економічних"), Map.entry("ек", "економічних"),
            Map.entry("п", "педагогічних"), Map.entry("пед", "педагогічних"),
            Map.entry("ю", "юридичних"), Map.entry("юр", "юридичних"),
            Map.entry("і", "історичних"), Map.entry("іст", "історичних"),
            Map.entry("х", "хімічних"), Map.entry("хім", "хімічних"),
            Map.entry("б", "біологічних"), Map.entry("біол", "біологічних"),
            Map.entry("м", "медичних"), Map.entry("мед", "медичних"),
            Map.entry("с", "соціологічних"), Map.entry("соц", "соціологічних"),
            Map.entry("г", "географічних"), Map.entry("геогр", "географічних"),
            Map.entry("філ", "філологічних"), Map.entry("філос", "філософських"),
            Map.entry("псих", "психологічних"), Map.entry("держ", "наук з державного управління"),
            Map.entry("пол", "політичних"), Map.entry("фарм", "фармацевтичних")
    );

    private String expandDegreeAbbreviation(String degree) {
        if (degree == null) return null;
        // к.т.н. / д.в.н. / к.е.н. тощо
        java.util.regex.Pattern abbrPattern = java.util.regex.Pattern.compile(
                "([кКdДд])\\.\\s?([а-яіїєґА-ЯІЇЄҐ]{1,5})\\.\\s?[нН]\\.?");
        java.util.regex.Matcher m = abbrPattern.matcher(degree);
        if (m.find()) {
            String prefix = m.group(1).toLowerCase().equals("к") ? "Кандидат" : "Доктор";
            String sciCode = m.group(2).toLowerCase();
            String sciField = SCIENCE_ABBREV.get(sciCode);
            if (sciField != null) {
                return prefix + " " + sciField + " наук";
            }
        }
        return degree;
    }

    /**
     * Fallback: витягуємо пропущені AI поля з сирих стовпців DOCX.
     * Працює детерміністично за regex — не залежить від AI.
     */
    private void postProcessFromRawColumns(Teacher teacher, XWPFTableRow row, ScientificData sci) {
        List<XWPFTableCell> cells = row.getTableCells();

        // Визначаємо зсув: якщо перша колонка — номер рядка (№ з/п), offset = 1
        int offset = 0;
        if (!cells.isEmpty()) {
            String firstCell = getCellText(cells.get(0));
            if (firstCell != null && firstCell.trim().matches("^\\d{1,3}\\.?$")) {
                offset = 1;
                log.debug("POST-PROCESS RAW: виявлено колонку №з/п, offset={}", offset);
            }
        }

        // Стовпець 3+offset: ОСВІТА
        int eduIdx = 3 + offset;
        if ((teacher.getUniversity() == null || teacher.getUniversity().isEmpty()) && cells.size() > eduIdx) {
            String eduText = getCellText(cells.get(eduIdx));
            if (eduText != null && !eduText.isEmpty()) {
                log.info("POST-PROCESS RAW: AI пропустив освіту, беремо зі стовпця 3: {}",
                        eduText.substring(0, Math.min(200, eduText.length())));
                teacher.setUniversity(eduText.length() > 500 ? eduText.substring(0, 500) : eduText);

                // Спеціальність
                if (teacher.getUniversitySpeciality() == null) {
                    java.util.regex.Matcher specM = java.util.regex.Pattern.compile(
                            "(?:[Сс]пеціальність|СПЕЦІАЛЬНІСТЬ)[:\\s]*[\"«]?([^\"»\\n]{5,150})[\"»]?"
                    ).matcher(eduText);
                    if (specM.find()) {
                        teacher.setUniversitySpeciality(specM.group(1).trim());
                    }
                }

                // Диплом
                if (teacher.getUniversityDiploma() == null) {
                    java.util.regex.Matcher diplM = java.util.regex.Pattern.compile(
                            "(?:[Дд]иплом)[^\\n]*(?:серія|№|номер)[^\\n]+"
                    ).matcher(eduText);
                    if (diplM.find()) {
                        teacher.setUniversityDiploma(diplM.group().trim());
                    }
                }
            }
        }

        // Стовпець 4+offset: НАУКОВА КВАЛІФІКАЦІЯ — ступінь, звання, дисертація
        int sciIdx = 4 + offset;
        if (cells.size() > sciIdx) {
            String sciText = getCellText(cells.get(sciIdx));
            if (sciText != null && !sciText.isEmpty()) {

                // Ступінь (якщо AI пропустив)
                if (sci.degreeName == null || sci.degreeName.isEmpty()) {
                    String degree = extractDegreeFromText(sciText);
                    if (degree != null) {
                        log.info("POST-PROCESS RAW: AI пропустив ступінь, знайдено: {}", degree);
                        sci.degreeName = degree;
                    }
                }

                // Дисертація (якщо AI пропустив)
                if (sci.dissertationTopic == null || sci.dissertationTopic.isEmpty()) {
                    java.util.regex.Matcher dissM = java.util.regex.Pattern.compile(
                            "(?:[Дд]исертація|[Тт]ема)[:\\s]+[\"«]?(.{20,500})[\"»]?"
                    ).matcher(sciText);
                    if (dissM.find()) {
                        sci.dissertationTopic = dissM.group(1).trim();
                    }
                }

                // Диплом ступеня (якщо AI пропустив)
                if (sci.degreeDiploma == null || sci.degreeDiploma.isEmpty()) {
                    java.util.regex.Matcher degDiplM = java.util.regex.Pattern.compile(
                            "(?:[Дд]иплом)\\s+(?:ДК|ДД|НК)\\s*№?[^\\n]+"
                    ).matcher(sciText);
                    if (degDiplM.find()) {
                        sci.degreeDiploma = degDiplM.group().trim();
                    }
                }

                // Атестат звання (якщо AI пропустив)
                if (sci.titleAttestat == null || sci.titleAttestat.isEmpty()) {
                    java.util.regex.Matcher attM = java.util.regex.Pattern.compile(
                            "(?:[Аа]тестат)\\s+\\d{2}[А-ЯІЇЄҐа-яіїєґ]{2}[^\\n]+"
                    ).matcher(sciText);
                    if (attM.find()) {
                        sci.titleAttestat = attM.group().trim();
                        // І відразу виправляємо звання з коду атестата
                        String titleFromAttestat = extractTitleFromAttestat(attM.group().trim());
                        if (titleFromAttestat != null) {
                            sci.titleName = titleFromAttestat;
                        }
                    }
                }
            }
        }

        // Стовпець 6+offset: БОЙОВИЙ ДОСВІД
        int combatIdx = 6 + offset;
        if (!teacher.isCombatVeteranStatus() && cells.size() > combatIdx) {
            String combatText = getCellText(cells.get(combatIdx));
            if (combatText != null && !combatText.isEmpty()) {
                String lower = combatText.toLowerCase();
                if (lower.contains("убд") || lower.contains("учасник бойових дій")
                        || lower.contains("учасник б/д") || lower.contains("ато")
                        || lower.contains("оос") || lower.contains("ссо")
                        || lower.contains("бойов") || lower.contains("антитерор")
                        || java.util.regex.Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}").matcher(combatText).find()) {
                    log.info("POST-PROCESS RAW: AI пропустив УБД, знайдено у стовпці 6: {}",
                            combatText.substring(0, Math.min(200, combatText.length())));
                    teacher.setCombatVeteranStatus(true);
                    if (teacher.getCombatExperienceDates() == null || teacher.getCombatExperienceDates().isEmpty()) {
                        teacher.setCombatExperienceDates(
                                combatText.length() > 500 ? combatText.substring(0, 500) : combatText);
                    }
                }
            }
        }
    }

    /**
     * Витягує науковий ступінь з тексту стовпця 4.
     */
    private String extractDegreeFromText(String text) {
        // 1. Повна форма: "Кандидат технічних наук", "Доктор військових наук"
        java.util.regex.Matcher fullM = java.util.regex.Pattern.compile(
                "((?:[Дд]октор|[Кк]андидат)\\s+[а-яіїєґА-ЯІЇЄҐ'ʼ]+(?:[\\-\\s][а-яіїєґА-ЯІЇЄҐ'ʼ]+)*\\s+наук)"
        ).matcher(text);
        if (fullM.find()) {
            return fullM.group(1).trim();
        }

        // 2. Скорочення: к.т.н., д.в.н. тощо
        java.util.regex.Matcher abbrM = java.util.regex.Pattern.compile(
                "([кКdДд])\\.\\s?([а-яіїєґА-ЯІЇЄҐ]{1,5})\\.\\s?[нН]\\.?"
        ).matcher(text);
        if (abbrM.find()) {
            String prefix = abbrM.group(1).toLowerCase().equals("к") ? "Кандидат" : "Доктор";
            String sciCode = abbrM.group(2).toLowerCase();
            String sciField = SCIENCE_ABBREV.get(sciCode);
            if (sciField != null) {
                return prefix + " " + sciField + " наук";
            }
            return abbrM.group(); // повернемо як є, якщо не знайшли розшифровку
        }

        // 3. PhD / Ph.D
        if (text.contains("PhD") || text.contains("Ph.D")) {
            return "PhD";
        }

        return null;
    }

    /**
     * Витягує текст з комірки, включаючи вкладені таблиці та SDT (Content Controls).
     * Ідентичний DataImportService.getCellText() — щоб не пропускати вкладені структури.
     */
    private String getCellText(XWPFTableCell cell) {
        if (cell == null) return "";
        StringBuilder sb = new StringBuilder();
        appendCellText(cell, sb);
        return sb.toString().trim();
    }

    private void appendCellText(XWPFTableCell cell, StringBuilder sb) {
        for (IBodyElement elem : cell.getBodyElements()) {
            if (elem instanceof XWPFParagraph p) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(text.trim());
                }
            } else if (elem instanceof XWPFTable nestedTable) {
                for (XWPFTableRow nestedRow : nestedTable.getRows()) {
                    for (XWPFTableCell nestedCell : nestedRow.getTableCells()) {
                        appendCellText(nestedCell, sb);
                    }
                }
            } else if (elem instanceof XWPFSDT sdt) {
                String sdtText = sdt.getContent().getText();
                if (sdtText != null && !sdtText.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(sdtText.trim());
                }
            }
        }
    }

    private String getRowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            StringBuilder cellSb = new StringBuilder();
            for (XWPFParagraph p : cell.getParagraphs()) {
                if (cellSb.length() > 0) cellSb.append("\n");
                cellSb.append(p.getText());
            }
            String cellText = cellSb.toString().trim();
            if (!cellText.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(cellText);
            }
        }
        return sb.toString().trim();
    }

    /**
     * If AI returned "university" as a List of Map objects (multiple educations),
     * extract the last one and flatten its fields to standard university* keys.
     * Otherwise return the original data map unchanged.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractLastEducation(Map<String, Object> data) {
        Object uniVal = data.get("university");
        if (uniVal instanceof List<?> list && !list.isEmpty()) {
            Object last = list.get(list.size() - 1);
            if (last instanceof Map<?, ?> eduMap) {
                Map<String, Object> result = new java.util.HashMap<>(data);
                Map<String, Object> edu = (Map<String, Object>) eduMap;
                // Map AI's alternative field names to our standard fields
                result.put("university", edu.getOrDefault("institution", edu.get("university")));
                result.put("universitySpeciality", edu.getOrDefault("speciality", edu.get("universitySpeciality")));
                result.put("universityDiploma", edu.getOrDefault("diploma", edu.get("universityDiploma")));
                result.put("universityGraduationYear", edu.getOrDefault("graduationYear", edu.get("universityGraduationYear")));
                result.put("universityDiplomaDate", edu.getOrDefault("diplomaDate", edu.get("universityDiplomaDate")));
                log.info("Multiple educations detected, using last: {}", result.get("university"));
                return result;
            }
        }
        return data;
    }

    /**
     * Save all education records from AI-parsed data.
     * Reads "educations" array; falls back to flat university* fields if array is absent.
     */
    @SuppressWarnings("unchecked")
    private void saveEducationsFromAi(Map<String, Object> data, ua.edu.teacherlicence.teacher.model.Teacher teacher) {
        Object edusVal = data.get("educations");
        if (edusVal instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> edu = (Map<String, Object>) rawMap;
                    ua.edu.teacherlicence.teacher.model.Education education =
                            ua.edu.teacherlicence.teacher.model.Education.builder()
                                    .teacher(teacher)
                                    .institution(getStr(edu, "institution"))
                                    .city(getStr(edu, "city"))
                                    .degree(getStr(edu, "degree"))
                                    .speciality(getStr(edu, "speciality"))
                                    .qualification(getStr(edu, "qualification"))
                                    .graduationYear(getInt(edu, "graduationYear"))
                                    .diploma(normalizeDiplomaStr(getStr(edu, "diploma")))
                                    .build();
                    String dateStr = getStr(edu, "diplomaDate");
                    if (dateStr != null) {
                        try { education.setDiplomaDate(LocalDate.parse(dateStr)); } catch (Exception ignored) {}
                    }
                    educationRepository.save(education);
                }
            }
            log.info("Saved {} education records for {}", list.size(), teacher.getLastName());
        } else if (teacher.getUniversity() != null && !teacher.getUniversity().isBlank()) {
            // Fallback: create single Education from flat fields
            ua.edu.teacherlicence.teacher.model.Education education =
                    ua.edu.teacherlicence.teacher.model.Education.builder()
                            .teacher(teacher)
                            .institution(teacher.getUniversity())
                            .speciality(teacher.getUniversitySpeciality())
                            .diploma(teacher.getUniversityDiploma())
                            .graduationYear(teacher.getUniversityGraduationYear())
                            .diplomaDate(teacher.getUniversityDiplomaDate())
                            .build();
            educationRepository.save(education);
            log.info("Saved 1 education record (from flat fields) for {}", teacher.getLastName());
        }
    }

    /** Remove "Диплом" / "Диплом з відзнакою" prefix from diploma string. */
    private String normalizeDiplomaStr(String diploma) {
        if (diploma == null) return null;
        return diploma
                .replaceFirst("(?iu)^[Дд]иплом(\\s+(з\\s+відзнакою|магістра|спеціаліста|бакалавра|молодшого спеціаліста))?\\s*:?\\s*", "")
                .trim();
    }

    private String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private String getStr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }

    private Integer getInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private boolean getBool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }

    /**
     * Автоматичне визначення підтипів для рейтингування пп.4 та пп.12.
     */
    private void autoAssignSubtypes(Publication p) {
        // Методичні (пп.4): Практикум=10, Навч.-метод. вказівки=3, Е-курс=3, Конспект лекцій=2
        if (p.getType() == PublicationType.METHODICAL && p.getMethodicalSubtype() == null) {
            String text = ((p.getTitle() != null ? p.getTitle() : "") + " "
                    + (p.getRawText() != null ? p.getRawText() : "")).toLowerCase();
            if (text.contains("практикум")) p.setMethodicalSubtype(MethodicalSubtype.PRACTICUM);
            else if (text.contains("електронний курс") || text.contains("електронні курси")
                    || text.contains("е-курс") || text.contains("е курс") || text.contains("екурс")
                    || text.contains("дистанційн") || text.contains("on-line курс")
                    || text.contains("online курс") || text.contains("онлайн курс")
                    || text.contains("онлайн-курс") || text.contains("moodle"))
                p.setMethodicalSubtype(MethodicalSubtype.E_COURSE);
            else if (text.contains("конспект лекцій") || text.contains("конспект лекції")
                    || text.contains("конспекти лекцій") || text.contains("курс лекцій")
                    || text.contains("курс лекції") || text.contains("тексти лекцій"))
                p.setMethodicalSubtype(MethodicalSubtype.LECTURE_NOTES);
            else if (text.contains("робоча програма") || text.contains("робочі програми")
                    || text.contains("рпнд") || text.contains("силабус") || text.contains("навчальна програма"))
                p.setMethodicalSubtype(MethodicalSubtype.WORK_PROGRAM);
            else if (text.contains("методичн") && (text.contains("вказів") || text.contains("рекоменд")
                    || text.contains("забезпечення") || text.contains("розробк")))
                p.setMethodicalSubtype(MethodicalSubtype.METHODICAL_GUIDELINES);
            else if (text.contains("метод.") && (text.contains("вказів") || text.contains("рекоменд")))
                p.setMethodicalSubtype(MethodicalSubtype.METHODICAL_GUIDELINES);
            else if (text.contains("для самостійної роботи") || text.contains("для самост. роботи")
                    || text.contains("завдання для практичн") || text.contains("завдання для лаборатор"))
                p.setMethodicalSubtype(MethodicalSubtype.METHODICAL_GUIDELINES);
            else if (text.contains("методичн") || text.contains("метод."))
                p.setMethodicalSubtype(MethodicalSubtype.METHODICAL_GUIDELINES);
            else
                p.setMethodicalSubtype(MethodicalSubtype.LECTURE_NOTES);
        }
        // Апробації / науково-популярні (пп.12): Scopus/WoS=5, Міжнародний=3, Вітчизняний=2
        if ((p.getType() == PublicationType.APPROBATION || p.getType() == PublicationType.POPULAR_SCIENTIFIC)
                && p.getApprobationSubtype() == null) {
            String text = ((p.getTitle() != null ? p.getTitle() : "") + " "
                    + (p.getRawText() != null ? p.getRawText() : "")
                    + " " + (p.getJournalName() != null ? p.getJournalName() : "")
                    + " " + (p.getConferenceInfo() != null ? p.getConferenceInfo() : "")).toLowerCase();
            if (text.contains("scopus") || text.contains("web of science") || text.contains("wos")
                    || text.contains("ceur"))
                p.setApprobationSubtype(ApprobationSubtype.SCOPUS_WOS);
            else if (text.contains("ieee") || text.contains("springer") || text.contains("elsevier")
                    || text.contains("wiley") || text.contains("acm ") || text.contains("mdpi")
                    || text.contains("taylor & francis") || text.contains("de gruyter")
                    || text.contains("cambridge university") || text.contains("oxford university")
                    || text.contains("nato "))
                p.setApprobationSubtype(ApprobationSubtype.INTERNATIONAL);
            else
                p.setApprobationSubtype(ApprobationSubtype.DOMESTIC);
        }
    }

    /**
     * Конвертує всі ISO-дати (yyyy-MM-dd) в тексті на dd.MM.yyyy.
     * "2020-06-30-2020-11-20" → "30.06.2020-20.11.2020"
     */
    private String convertIsoDatesInText(String text) {
        if (text == null) return null;
        return java.util.regex.Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})")
                .matcher(text)
                .replaceAll("$3.$2.$1");
    }

    /**
     * Конвертує латинські літери в кириличні відповідники (для ПІБ).
     * AI іноді повертає прізвище латиницею (ROMANENKO → РОМАНЕНКО).
     * Якщо рядок містить латинські літери — транслітеруємо посимвольно.
     */
    private static String latinToCyrillic(String text) {
        if (text == null || text.isEmpty()) return text;
        // Якщо немає латинських літер — повертаємо як є
        if (!text.matches(".*[A-Za-z].*")) return text;
        // Якщо є і кирилиця і латиниця — замінюємо тільки візуально схожі
        // Якщо повністю латиницею — повна транслітерація
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            sb.append(LATIN_TO_CYR.getOrDefault(c, c));
        }
        return sb.toString();
    }

    /** Посимвольна транслітерація Latin → Cyrillic (українська) */
    private static final java.util.Map<Character, Character> LATIN_TO_CYR;
    static {
        LATIN_TO_CYR = new java.util.HashMap<>();
        // Великі
        LATIN_TO_CYR.put('A', 'А'); LATIN_TO_CYR.put('B', 'Б'); LATIN_TO_CYR.put('V', 'В');
        LATIN_TO_CYR.put('H', 'Г'); LATIN_TO_CYR.put('G', 'Г'); LATIN_TO_CYR.put('D', 'Д');
        LATIN_TO_CYR.put('E', 'Е'); LATIN_TO_CYR.put('Z', 'З'); LATIN_TO_CYR.put('Y', 'И');
        LATIN_TO_CYR.put('I', 'І'); LATIN_TO_CYR.put('K', 'К'); LATIN_TO_CYR.put('L', 'Л');
        LATIN_TO_CYR.put('M', 'М'); LATIN_TO_CYR.put('N', 'Н'); LATIN_TO_CYR.put('O', 'О');
        LATIN_TO_CYR.put('P', 'П'); LATIN_TO_CYR.put('R', 'Р'); LATIN_TO_CYR.put('S', 'С');
        LATIN_TO_CYR.put('T', 'Т'); LATIN_TO_CYR.put('U', 'У'); LATIN_TO_CYR.put('F', 'Ф');
        LATIN_TO_CYR.put('C', 'С'); LATIN_TO_CYR.put('W', 'В'); LATIN_TO_CYR.put('X', 'Х');
        // Малі
        LATIN_TO_CYR.put('a', 'а'); LATIN_TO_CYR.put('b', 'б'); LATIN_TO_CYR.put('v', 'в');
        LATIN_TO_CYR.put('h', 'г'); LATIN_TO_CYR.put('g', 'г'); LATIN_TO_CYR.put('d', 'д');
        LATIN_TO_CYR.put('e', 'е'); LATIN_TO_CYR.put('z', 'з'); LATIN_TO_CYR.put('y', 'и');
        LATIN_TO_CYR.put('i', 'і'); LATIN_TO_CYR.put('k', 'к'); LATIN_TO_CYR.put('l', 'л');
        LATIN_TO_CYR.put('m', 'м'); LATIN_TO_CYR.put('n', 'н'); LATIN_TO_CYR.put('o', 'о');
        LATIN_TO_CYR.put('p', 'п'); LATIN_TO_CYR.put('r', 'р'); LATIN_TO_CYR.put('s', 'с');
        LATIN_TO_CYR.put('t', 'т'); LATIN_TO_CYR.put('u', 'у'); LATIN_TO_CYR.put('f', 'ф');
        LATIN_TO_CYR.put('c', 'с'); LATIN_TO_CYR.put('w', 'в'); LATIN_TO_CYR.put('x', 'х');
    }
}
