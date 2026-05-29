package ua.edu.teacherlicence.publication.model;

/**
 * Підтип апробаційної/науково-популярної публікації (для рейтингування пп.12).
 * Визначається рівнем видання, а не типом публікації.
 */
public enum ApprobationSubtype {
    SCOPUS_WOS,       // Scopus / Web of Science — 5 балів
    INTERNATIONAL,    // Міжнародний журнал — 3 бали
    DOMESTIC          // Вітчизняний журнал — 2 бали
}
