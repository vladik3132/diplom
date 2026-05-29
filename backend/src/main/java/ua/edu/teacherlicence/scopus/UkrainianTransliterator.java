package ua.edu.teacherlicence.scopus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ukrainian Cyrillic to Latin transliteration (passport standard / DSTU 9112).
 * Generates primary + alternative variants for ambiguous letters.
 */
public final class UkrainianTransliterator {

    private UkrainianTransliterator() {}

    // Primary transliteration map (passport standard)
    private static final Map<Character, String> PRIMARY = Map.ofEntries(
            Map.entry('А', "A"), Map.entry('а', "a"),
            Map.entry('Б', "B"), Map.entry('б', "b"),
            Map.entry('В', "V"), Map.entry('в', "v"),
            Map.entry('Г', "H"), Map.entry('г', "h"),
            Map.entry('Ґ', "G"), Map.entry('ґ', "g"),
            Map.entry('Д', "D"), Map.entry('д', "d"),
            Map.entry('Е', "E"), Map.entry('е', "e"),
            Map.entry('Є', "Ye"), Map.entry('є', "ie"),
            Map.entry('Ж', "Zh"), Map.entry('ж', "zh"),
            Map.entry('З', "Z"), Map.entry('з', "z"),
            Map.entry('И', "Y"), Map.entry('и', "y"),
            Map.entry('І', "I"), Map.entry('і', "i"),
            Map.entry('Ї', "Yi"), Map.entry('ї', "i"),
            Map.entry('Й', "Y"), Map.entry('й', "i"),
            Map.entry('К', "K"), Map.entry('к', "k"),
            Map.entry('Л', "L"), Map.entry('л', "l"),
            Map.entry('М', "M"), Map.entry('м', "m"),
            Map.entry('Н', "N"), Map.entry('н', "n"),
            Map.entry('О', "O"), Map.entry('о', "o"),
            Map.entry('П', "P"), Map.entry('п', "p"),
            Map.entry('Р', "R"), Map.entry('р', "r"),
            Map.entry('С', "S"), Map.entry('с', "s"),
            Map.entry('Т', "T"), Map.entry('т', "t"),
            Map.entry('У', "U"), Map.entry('у', "u"),
            Map.entry('Ф', "F"), Map.entry('ф', "f"),
            Map.entry('Х', "Kh"), Map.entry('х', "kh"),
            Map.entry('Ц', "Ts"), Map.entry('ц', "ts"),
            Map.entry('Ч', "Ch"), Map.entry('ч', "ch"),
            Map.entry('Ш', "Sh"), Map.entry('ш', "sh"),
            Map.entry('Щ', "Shch"), Map.entry('щ', "shch"),
            Map.entry('Ю', "Yu"), Map.entry('ю', "iu"),
            Map.entry('Я', "Ya"), Map.entry('я', "ia"),
            Map.entry('ь', ""), Map.entry('Ь', ""),
            Map.entry('\'', ""), Map.entry('\u2019', "") // apostrophe
    );

    /**
     * Primary transliteration (passport standard).
     */
    public static String transliterate(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            String mapped = PRIMARY.get(c);
            sb.append(mapped != null ? mapped : c);
        }
        return sb.toString();
    }

    /**
     * Generate multiple transliteration variants for fuzzy matching.
     * Returns list with primary variant + alternatives for ambiguous letters.
     */
    public static List<String> transliterateVariants(String text) {
        if (text == null || text.isEmpty()) return List.of("");

        String primary = transliterate(text);
        List<String> variants = new ArrayList<>();
        variants.add(primary);

        String lower = text.toLowerCase();

        // Alternative: Г → G (instead of H) — common in older transliterations
        if (lower.contains("г")) {
            variants.add(transliterate(text.replace('г', 'ґ').replace('Г', 'Ґ')));
        }
        // Alternative: Щ → Sch (German-style, sometimes used)
        if (lower.contains("щ")) {
            variants.add(primary.replace("shch", "sch").replace("Shch", "Sch"));
        }
        // Alternative: Ю → Yu (instead of Iu)
        if (lower.contains("ю")) {
            variants.add(primary.replace("iu", "yu").replace("Iu", "Yu"));
        }
        // Alternative: Я → Ya (instead of Ia)
        if (lower.contains("я")) {
            variants.add(primary.replace("ia", "ya").replace("Ia", "Ya"));
        }
        // Alternative: Є → Ye (instead of Ie)
        if (lower.contains("є")) {
            variants.add(primary.replace("ie", "ye").replace("Ie", "Ye"));
        }
        // Alternative: И → I (instead of Y) — Russian-influenced
        if (lower.contains("и")) {
            variants.add(primary.replace("y", "i").replace("Y", "I"));
        }
        // Alternative: Кс → X (Олексій → Oleksii / Olexiy)
        if (lower.contains("кс")) {
            String alt = text.replace("кс", "x").replace("Кс", "X");
            variants.add(transliterate(alt));
        }

        return variants.stream().distinct().toList();
    }

    /**
     * Levenshtein distance between two strings (case-insensitive).
     */
    public static int levenshtein(String a, String b) {
        a = a.toLowerCase();
        b = b.toLowerCase();
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}
