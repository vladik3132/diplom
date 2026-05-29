package ua.edu.teacherlicence.ppdata.model;

/**
 * Масштаб (рівень) заходу — олімпіади, конкурсу, змагань тощо.
 * Визначає бали в рейтингу:
 * - INTERNATIONAL: 20 балів (досягли результатів)
 * - NATIONAL: 10 балів (досягли результатів)
 */
public enum CompetitionScope {
    INTERNATIONAL,  // Міжнародний
    NATIONAL        // Всеукраїнський / національний
}
