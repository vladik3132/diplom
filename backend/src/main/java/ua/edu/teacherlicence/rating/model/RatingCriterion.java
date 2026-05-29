package ua.edu.teacherlicence.rating.model;

/**
 * Перелік критеріїв рейтингу НПП (Додаток 1).
 * Кожен критерій має базові бали, які множаться на кількість.
 */
public enum RatingCriterion {

    // пп.1 — Наукові публікації
    SCOPUS_ARTICLE("Стаття Scopus", 20),
    WOS_ARTICLE("Стаття WoS", 20),
    CATEGORY_A_ARTICLE("Стаття Кат. А", 15),
    CATEGORY_B_ARTICLE("Стаття Кат. Б", 10),

    // пп.2 — Патенти
    PATENT("Патент на винахід", 10),
    DECLARATIVE_PATENT("Деклараційний патент", 2),
    COPYRIGHT("Авторське свідоцтво", 2),

    // пп.3 — Підручники / монографії
    TEXTBOOK("Підручник", 20),
    MONOGRAPH("Монографія", 20),
    STUDY_GUIDE("Навчальний посібник", 15),

    // пп.4 — Методичні праці
    PRACTICUM("Практикум", 10),
    METHODICAL_GUIDELINES("Навч.-методичні вказівки", 3),
    E_COURSE("Електронний курс", 3),
    LECTURE_NOTES("Конспект лекцій", 2),

    // пп.5 — Захист дисертації
    DOCTORAL_DEFENSE("Захист докторської", 100),
    PHD_DEFENSE("Захист кандидатської/PhD", 50),

    // пп.6 — Наукове керівництво
    DOCTORAL_SUPERVISION("Керівництво доктором", 30),
    PHD_SUPERVISION("Керівництво кандидатом/PhD", 20),

    // пп.7 — Атестація
    // (голова разової спецради — НЕ враховується в рейтингу згідно з керівними документами)
    OFFICIAL_OPPONENT("Офіційний опонент", 20),
    REVIEWER("Рецензент", 10),
    COUNCIL_MEMBER("Член постійної спецради", 5),

    // пп.8
    EDITORIAL_BOARD("Редколегія/експерт", 5),

    // пп.9
    EXPERT_COUNCIL("Експертна рада", 5),

    // пп.10
    INTERNATIONAL_PROJECT("Міжнародний проєкт", 15),

    // пп.11
    SCIENTIFIC_CONSULTING("Наукове консультування", 10),

    // пп.12 — За рівнем видання (апробаційні, науково-популярні, консультаційні, науково-експертні)
    APPROBATION_SCOPUS("Апробація Scopus/WoS", 5),
    APPROBATION_INTERNATIONAL("Апробація міжнар. журнал", 3),
    APPROBATION_DOMESTIC("Апробація вітчизн. журнал", 2),

    // пп.13
    FOREIGN_LANGUAGE_TEACHING("Іноземна мова", 5),

    // пп.14-15 — Керівництво здобувачами, які досягли результатів
    OLYMPIAD_INTERNATIONAL_PRIZE("Результат міжнар. рівня", 20),
    OLYMPIAD_NATIONAL_PRIZE("Результат всеукр. рівня", 10),
    SCIENCE_GROUP_LEADER("Науковий гурток", 2),

    // пп.16
    COMBAT_VETERAN("Учасник бойових дій", 5),
    COMBAT_EXPERIENCE("Бойовий досвід", 5),

    // пп.17
    UN_PEACEKEEPING("Миротворчі операції ООН", 10),

    // пп.18
    NATO_EXERCISES("Навчання НАТО", 10),

    // пп.19
    PROFESSIONAL_ASSOCIATION("Професійне об'єднання", 5),

    // Вчене звання
    PROFESSOR_TITLE("Звання Професор", 50),
    DOCENT_TITLE("Звання Доцент", 30),

    // Підвищення кваліфікації
    QUALIFICATION_CREDIT("Кредит ЄКТС (ПК)", 2),

    // Стажування за кордоном
    FOREIGN_INTERNSHIP("Стажування за кордоном", 10),

    // Міжкафедральне заняття
    OPEN_LESSON("Відкрите заняття", 3),

    // Методичний експеримент
    METHODOLOGICAL_EXPERIMENT("Методичний експеримент", 20),

    // Академічна мобільність
    ACADEMIC_MOBILITY("Академічна мобільність", 10),

    // Робоча група ОПП
    WORKING_GROUP_CHAIR("Голова робочої групи", 5),
    WORKING_GROUP_MEMBER("Член робочої групи", 1),

    // СМР — мовний сертифікат
    SMR_LEVEL_1("СМР-1", 5),
    SMR_LEVEL_2("СМР-2", 10),
    SMR_LEVEL_3("СМР-3", 15),

    // Курси ВО (рівні L2, L3, L4)
    MILITARY_COURSE_3_6("Курси ВО L2", 5),
    MILITARY_COURSE_6_10("Курси ВО L3", 10),
    MILITARY_COURSE_10_PLUS("Курси ВО L4", 15),

    // Рівень воєнної освіти
    MILITARY_ED_OPERATIONAL("Оперативний рівень ВО", 15),
    MILITARY_ED_STRATEGIC("Стратегічний рівень ВО", 20);

    private final String label;
    private final int points;

    RatingCriterion(String label, int points) {
        this.label = label;
        this.points = points;
    }

    public String getLabel() { return label; }
    public int getPoints() { return points; }
}
