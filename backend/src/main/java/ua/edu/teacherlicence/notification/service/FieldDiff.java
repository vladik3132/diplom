package ua.edu.teacherlicence.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Утиліта для порівняння полів і формування людиночитного опису змін.
 */
public class FieldDiff {

    private final List<String> changes = new ArrayList<>();

    /**
     * Порівняти два значення і додати опис зміни, якщо вони відрізняються.
     */
    public FieldDiff compare(String fieldLabel, Object oldVal, Object newVal) {
        String oldStr = normalize(oldVal);
        String newStr = normalize(newVal);
        if (!Objects.equals(oldStr, newStr)) {
            if (oldStr.isEmpty() && !newStr.isEmpty()) {
                changes.add(fieldLabel + ": +" + truncate(newStr));
            } else if (!oldStr.isEmpty() && newStr.isEmpty()) {
                changes.add(fieldLabel + ": видалено");
            } else {
                changes.add(fieldLabel + ": " + truncate(oldStr) + " → " + truncate(newStr));
            }
        }
        return this;
    }

    /**
     * Чи є зміни.
     */
    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    /**
     * Сформувати рядок зі змінами.
     */
    public String build() {
        if (changes.isEmpty()) return "без змін";
        return String.join("; ", changes);
    }

    private static String normalize(Object val) {
        if (val == null) return "";
        return val.toString().trim();
    }

    private static String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
