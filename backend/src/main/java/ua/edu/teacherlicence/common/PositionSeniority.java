package ua.edu.teacherlicence.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Канонічний словник старшинства академічних посад.
 *
 * <p>Менший ранг = вища посада. Використовується:
 * <ul>
 *   <li>сортуванням таблиць (штат, кафедри);</li>
 *   <li>для вибору primary-посади викладача, що займає декілька штатних
 *       одиниць (Начальник кафедри > Доцент > Викладач ...).</li>
 * </ul>
 *
 * <p>Збігається з {@code frontend/src/utils/positionSeniority.ts}.
 *
 * <p><b>Порядок ключів важливий:</b> ітерація проводиться у порядку вставки
 * (LinkedHashMap). Тому довші ключі-префікси («Старший науковий співробітник»)
 * мають йти ПЕРЕД коротшими («Науковий співробітник»), інакше у методі
 * {@link #rankOf(String)} коротший ключ матчиться першим і повертає
 * неправильний ранг.
 */
public final class PositionSeniority {

    private PositionSeniority() {}

    /** Невідома посада — кладемо в кінець. */
    public static final int UNKNOWN_RANK = 99;

    /**
     * Словник «частина-назви-посади → ранг». Перевірка через
     * {@code title.toLowerCase().contains(key.toLowerCase())}.
     * Порядок ключів — від ширших/більш специфічних до загальних.
     */
    private static final Map<String, Integer> SENIORITY;
    static {
        SENIORITY = new LinkedHashMap<>();
        SENIORITY.put("Начальник кафедри", 1);
        SENIORITY.put("Заступник начальника кафедри", 2);
        SENIORITY.put("Завідувач кафедри", 1);
        SENIORITY.put("Декан", 1);
        SENIORITY.put("Заступник декана", 2);
        SENIORITY.put("Професор", 3);
        // ВАЖЛИВО: "Старший науковий співробітник" має йти ПЕРЕД "Науковий співробітник",
        // інакше "включає" матчиться на коротший ключ.
        SENIORITY.put("Старший науковий співробітник", 4);
        SENIORITY.put("Доцент", 4);
        SENIORITY.put("Старший викладач", 5);
        SENIORITY.put("Викладач", 6);
        SENIORITY.put("Науковий співробітник", 7);
        SENIORITY.put("Асистент", 8);
    }

    /**
     * Повертає ранг посади за її назвою. Case-insensitive includes-match.
     * Невідома посада → {@link #UNKNOWN_RANK}.
     */
    public static int rankOf(String positionTitle) {
        if (positionTitle == null || positionTitle.isBlank()) return UNKNOWN_RANK;
        String lower = positionTitle.toLowerCase();
        for (Map.Entry<String, Integer> entry : SENIORITY.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return UNKNOWN_RANK;
    }
}
