package ua.edu.teacherlicence.fakhove.dto;

import ua.edu.teacherlicence.fakhove.model.JournalCategory;

/**
 * Результат перевірки журналу у реєстрі фахових видань та Scopus.
 */
public record VerificationResult(
        boolean isFakhove,
        JournalCategory category,
        boolean isScopus,
        String matchedFakhoveName,
        String matchedScopusName
) {
    /**
     * Створити результат "нічого не знайдено".
     */
    public static VerificationResult notFound() {
        return new VerificationResult(false, null, false, null, null);
    }
}
