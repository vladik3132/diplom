package ua.edu.teacherlicence.teacher.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер текстових позначень мовного рівня → ціле число (0..4) у шкалі СМР/STANAG 6001.
 *
 * Підтримувані формати поля {@code level}:
 * <ul>
 *   <li><b>"СМР 2"</b>, "СМР-2", "смр2", "СМР рівень 2" → 2</li>
 *   <li><b>STANAG код</b>: "2222", "3322", "1+1+11", "2+221" → min(цифр)</li>
 *   <li><b>STANAG явно</b>: "STANAG 2", "STANAG 6001 рівень 3" → число</li>
 *   <li><b>CEFR</b>: A1/A2 → 0, B1 → 1, B2 → 2, C1/C2 → 3</li>
 *   <li><b>Просто цифра</b>: "2" → 2, "3" → 3</li>
 * </ul>
 *
 * Використовується як fallback у {@link ua.edu.teacherlicence.teacher.model.LanguageSkill#getSmrLevel()}
 * коли smr1..smr4 не заповнені (часто буває коли імпортували сертифікат із PDF
 * або користувач не розписав за компонентами).
 */
public final class LanguageLevelParser {

    private LanguageLevelParser() {}

    /** Регулярки для різних варіантів запису рівня. */
    private static final Pattern P_SMR_EXPLICIT = Pattern.compile("смр\\s*[-:]?\\s*(\\d)");
    private static final Pattern P_STANAG_EXPLICIT = Pattern.compile("(?:stanag|6001)\\D*(\\d)");
    private static final Pattern P_STANAG_CODE = Pattern.compile("(\\d)\\+?(\\d)\\+?(\\d)\\+?(\\d)\\+?");
    private static final Pattern P_LEVEL_KEYWORD = Pattern.compile("рівень\\s+(\\d)");
    private static final Pattern P_BARE_DIGIT = Pattern.compile("^\\s*(\\d)\\s*$");

    /**
     * Витягнути ціле число (0..4) з текстового поля level, якщо воно у форматі СМР/STANAG/CEFR.
     * Повертає null якщо нічого не розпізналося.
     */
    public static Integer parseSmr(String level) {
        if (level == null || level.isBlank()) return null;
        String norm = level.trim().toLowerCase().replaceAll("\\s+", " ");

        // 1. "СМР 2", "СМР-2", "смр2"
        Matcher m = P_SMR_EXPLICIT.matcher(norm);
        if (m.find()) {
            return clampLevel(parseInt(m.group(1)));
        }

        // 2. "STANAG 2", "STANAG 6001 рівень 3"
        m = P_STANAG_EXPLICIT.matcher(norm);
        if (m.find()) {
            return clampLevel(parseInt(m.group(1)));
        }

        // 3. "рівень 2", "level 2"
        m = P_LEVEL_KEYWORD.matcher(norm);
        if (m.find()) {
            return clampLevel(parseInt(m.group(1)));
        }

        // 4. CEFR (A1, A2, B1, B2, C1, C2) — приблизне співставлення з STANAG 6001
        Integer cefr = parseCefr(norm);
        if (cefr != null) return cefr;

        // 5. STANAG-код у вигляді 4 цифр: "2222", "3322", "1+1+11", "2+221"
        m = P_STANAG_CODE.matcher(norm);
        if (m.find()) {
            int a = parseInt(m.group(1));
            int b = parseInt(m.group(2));
            int c = parseInt(m.group(3));
            int d = parseInt(m.group(4));
            return clampLevel(Math.min(Math.min(a, b), Math.min(c, d)));
        }

        // 6. Просто одна цифра ("2", "3")
        m = P_BARE_DIGIT.matcher(norm);
        if (m.find()) {
            return clampLevel(parseInt(m.group(1)));
        }

        return null;
    }

    /**
     * CEFR (Common European Framework) → STANAG 6001 (приблизне співставлення):
     *   A1, A2 → 0  (initial / elementary)
     *   B1     → 1  (limited working)
     *   B2     → 2  (general professional — мінімум для "СМР 2")
     *   C1, C2 → 3  (advanced / native)
     */
    private static Integer parseCefr(String norm) {
        // Перевіряємо у порядку від вищого до нижчого, щоб "C1" не плутати з "B2 C1".
        if (norm.matches(".*\\bc[12]\\b.*")) return 3;
        if (norm.matches(".*\\bb2\\b.*")) return 2;
        if (norm.matches(".*\\bb1\\b.*")) return 1;
        if (norm.matches(".*\\ba[12]\\b.*")) return 0;
        return null;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    /** Обрізати значення в діапазоні 0..4 (фактичний діапазон STANAG 6001). */
    private static Integer clampLevel(int v) {
        if (v < 0) return 0;
        if (v > 4) return 4;
        return v;
    }
}
