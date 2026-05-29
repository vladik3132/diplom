package ua.edu.teacherlicence.teacher.util;

/**
 * Утиліта для очищення поля Teacher.position від сміття після імпорту:
 * - "Викладач кафедри комп'ютерних наук та ІТ (основна)" → "Викладач"
 * - "Доцент кафедри X" → "Доцент"
 * - "Старший викладач кафедри Y (сумісник)" → "Старший викладач"
 * - "Професор (основна)" → "Професор"
 *
 * Зберігає без змін керівні посади, де "кафедри" — частина самої назви:
 * - "Начальник кафедри ..." → "Начальник кафедри"
 * - "Заступник начальника кафедри ..." → "Заступник начальника кафедри"
 * - "Завідувач кафедри ..." → "Завідувач кафедри"
 *
 * Логіка дзеркальна Liquibase-міграції 009 (cleanup-position-strings).
 */
public final class PositionCleaner {

    private PositionCleaner() {}

    public static String clean(String position) {
        if (position == null) return null;
        String trimmed = position.trim();
        if (trimmed.isEmpty()) return trimmed;

        String lower = trimmed.toLowerCase();

        // Керівні посади — лишаємо канонічну форму без назви кафедри.
        if (lower.startsWith("начальник кафедри")) return "Начальник кафедри";
        if (lower.startsWith("заступник начальника кафедри")) return "Заступник начальника кафедри";
        if (lower.startsWith("завідувач кафедри")) return "Завідувач кафедри";

        // Інші посади — обрізаємо все від " кафедри" і далі.
        int idx = lower.indexOf(" кафедри");
        if (idx > 0) {
            trimmed = trimmed.substring(0, idx).trim();
        }

        // Обрізаємо трейлінгові дужки: "(основна)", "(сумісник)", "(за сумісництвом)" тощо.
        int parenIdx = trimmed.indexOf(" (");
        if (parenIdx > 0) {
            trimmed = trimmed.substring(0, parenIdx).trim();
        }

        // Сколапсити множинні пробіли в один.
        trimmed = trimmed.replaceAll("\\s{2,}", " ").trim();

        return trimmed;
    }
}
