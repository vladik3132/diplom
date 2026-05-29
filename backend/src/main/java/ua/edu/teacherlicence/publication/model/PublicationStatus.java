package ua.edu.teacherlicence.publication.model;

/**
 * Статус валідації публікації.
 */
public enum PublicationStatus {
    NOT_VALIDATED,    // Щойно імпортовано, не перевірено
    AI_VALIDATED,     // Перевірено ШІ, все ок
    NEEDS_ATTENTION,  // Потребує уваги (невідомий журнал, скинута категорія тощо)
    HEAD_VALIDATED,   // Затверджено начальником кафедри
    OUTDATED          // Старше 5 років (не актуальне)
}
