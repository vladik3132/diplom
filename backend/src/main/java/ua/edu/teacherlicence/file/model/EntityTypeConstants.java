package ua.edu.teacherlicence.file.model;

import java.util.Map;

/**
 * Constants for entity types used in file_attachments table.
 * Also provides Ukrainian labels for Google Drive folder naming.
 */
public final class EntityTypeConstants {

    private EntityTypeConstants() {}

    public static final String PUBLICATION = "PUBLICATION";
    public static final String QUALIFICATION = "QUALIFICATION_IMPROVEMENT";
    public static final String LANGUAGE_SKILL = "LANGUAGE_SKILL";
    public static final String TEACHER_PHOTO = "TEACHER_PHOTO";
    public static final String DISCIPLINE_DOCUMENT = "DISCIPLINE_DOCUMENT";
    public static final String EDITORIAL_PLAN = "EDITORIAL_PLAN";

    public static final String EDUCATION = "EDUCATION";
    public static final String ACADEMIC_DEGREE = "ACADEMIC_DEGREE";
    public static final String ACADEMIC_TITLE = "ACADEMIC_TITLE";

    // Teacher document types
    public static final String TEACHER_UNIVERSITY_DIPLOMA = "TEACHER_UNIVERSITY_DIPLOMA";
    public static final String TEACHER_DEGREE_DIPLOMA = "TEACHER_DEGREE_DIPLOMA";
    public static final String TEACHER_TITLE_ATTESTAT = "TEACHER_TITLE_ATTESTAT";
    public static final String TEACHER_COMBAT_VETERAN_DOC = "TEACHER_COMBAT_VETERAN_DOC";

    // ppData entity types
    public static final String PP_SCIENTIFIC_SUPERVISION = "PP_SCIENTIFIC_SUPERVISION";
    public static final String PP_ATTESTATION_ACTIVITY = "PP_ATTESTATION_ACTIVITY";
    public static final String PP_EDITORIAL_ACTIVITY = "PP_EDITORIAL_ACTIVITY";
    public static final String PP_EXPERT_COUNCIL = "PP_EXPERT_COUNCIL";
    public static final String PP_INTERNATIONAL_PROJECT = "PP_INTERNATIONAL_PROJECT";
    public static final String PP_SCIENTIFIC_CONSULTING = "PP_SCIENTIFIC_CONSULTING";
    public static final String PP_FOREIGN_LANGUAGE_TEACHING = "PP_FOREIGN_LANGUAGE_TEACHING";
    public static final String PP_OLYMPIAD_GUIDANCE = "PP_OLYMPIAD_GUIDANCE";
    public static final String PP_MILITARY_MISSION = "PP_MILITARY_MISSION";
    public static final String PP_PROFESSIONAL_ASSOCIATION = "PP_PROFESSIONAL_ASSOCIATION";
    public static final String PP_PRACTICAL_EXPERIENCE = "PP_PRACTICAL_EXPERIENCE";

    // Rating-related entity types
    public static final String TEACHER_MILITARY_EDUCATION = "TEACHER_MILITARY_EDUCATION";
    public static final String OPEN_LESSON = "OPEN_LESSON";
    public static final String METHODOLOGICAL_EXPERIMENT = "METHODOLOGICAL_EXPERIMENT";
    public static final String ACADEMIC_MOBILITY = "ACADEMIC_MOBILITY";
    public static final String FOREIGN_INTERNSHIP = "FOREIGN_INTERNSHIP";

    /** Ukrainian folder names for Google Drive structure */
    public static final Map<String, String> FOLDER_LABELS = Map.ofEntries(
            Map.entry(PUBLICATION, "Публікації"),
            Map.entry(QUALIFICATION, "Підвищення_кваліфікації"),
            Map.entry(LANGUAGE_SKILL, "Мовні_сертифікати"),
            Map.entry(TEACHER_PHOTO, "Фото"),
            Map.entry(DISCIPLINE_DOCUMENT, "Документи_дисциплін"),
            Map.entry(EDITORIAL_PLAN, "Видавничий_план"),
            Map.entry(EDUCATION, "Дипломи_освіти"),
            Map.entry(ACADEMIC_DEGREE, "Дипломи_наукових_ступенів"),
            Map.entry(ACADEMIC_TITLE, "Атестати_вчених_звань"),
            Map.entry(TEACHER_UNIVERSITY_DIPLOMA, "Диплом_освіти"),
            Map.entry(TEACHER_DEGREE_DIPLOMA, "Диплом_ступеня"),
            Map.entry(TEACHER_TITLE_ATTESTAT, "Атестат_звання"),
            Map.entry(TEACHER_COMBAT_VETERAN_DOC, "Посвідчення_УБД"),
            Map.entry(PP_SCIENTIFIC_SUPERVISION, "Наукове_керівництво"),
            Map.entry(PP_ATTESTATION_ACTIVITY, "Атестаційна_діяльність"),
            Map.entry(PP_EDITORIAL_ACTIVITY, "Редакційна_діяльність"),
            Map.entry(PP_EXPERT_COUNCIL, "Експертні_ради"),
            Map.entry(PP_INTERNATIONAL_PROJECT, "Міжнародні_проєкти"),
            Map.entry(PP_SCIENTIFIC_CONSULTING, "Наукове_консультування"),
            Map.entry(PP_FOREIGN_LANGUAGE_TEACHING, "Іноземна_мова_викладання"),
            Map.entry(PP_OLYMPIAD_GUIDANCE, "Олімпіади_конкурси"),
            Map.entry(PP_MILITARY_MISSION, "Миротворчі_місії"),
            Map.entry(PP_PROFESSIONAL_ASSOCIATION, "Професійні_об'єднання"),
            Map.entry(PP_PRACTICAL_EXPERIENCE, "Практичний_досвід"),
            Map.entry(TEACHER_MILITARY_EDUCATION, "Диплом_військової_освіти"),
            Map.entry(OPEN_LESSON, "Відкриті_заняття"),
            Map.entry(METHODOLOGICAL_EXPERIMENT, "Методичні_експерименти"),
            Map.entry(ACADEMIC_MOBILITY, "Академічна_мобільність"),
            Map.entry(FOREIGN_INTERNSHIP, "Міжнародне_стажування")
    );

    public static String getFolderLabel(String entityType) {
        return FOLDER_LABELS.getOrDefault(entityType, entityType);
    }
}
