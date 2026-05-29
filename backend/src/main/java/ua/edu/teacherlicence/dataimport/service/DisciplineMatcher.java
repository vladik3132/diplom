package ua.edu.teacherlicence.dataimport.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua.edu.teacherlicence.discipline.model.Discipline;

import java.util.*;

/**
 * Нечіткий пошук дисциплін за назвою при імпорті.
 * Використовується обома парсерами (regex та AI).
 */
@Slf4j
@Component
public class DisciplineMatcher {

    /**
     * Знаходить найбільш відповідну дисципліну за назвою.
     * Пріоритет: 1) точний збіг, 2) нормалізований збіг, 3) одна містить іншу,
     * 4) подібність за словами (Jaccard ≥ 60%).
     *
     * @return знайдена дисципліна або null
     */
    public Discipline findBestMatch(String importedName, List<Discipline> allDisciplines) {
        if (importedName == null || importedName.isBlank()) return null;

        String normalizedImport = normalize(importedName);

        // 1) Точний збіг (case-insensitive)
        for (Discipline d : allDisciplines) {
            if (d.getName().equalsIgnoreCase(importedName)) return d;
        }

        // 2) Нормалізований збіг
        for (Discipline d : allDisciplines) {
            if (normalize(d.getName()).equals(normalizedImport)) return d;
        }

        // 3) Одна назва містить іншу (для скорочених варіантів)
        for (Discipline d : allDisciplines) {
            String normalizedDb = normalize(d.getName());
            if (normalizedDb.contains(normalizedImport) || normalizedImport.contains(normalizedDb)) {
                return d;
            }
        }

        // 4) Збіг за словами (Jaccard ≥ 60%)
        Set<String> importWords = extractWords(normalizedImport);
        if (importWords.size() < 2) return null; // занадто коротка назва для нечіткого пошуку

        Discipline bestMatch = null;
        double bestScore = 0;

        for (Discipline d : allDisciplines) {
            Set<String> dbWords = extractWords(normalize(d.getName()));
            double score = jaccardSimilarity(importWords, dbWords);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = d;
            }
        }

        if (bestScore >= 0.6) {
            log.debug("Fuzzy matched discipline '{}' -> '{}' (score={})",
                    importedName, bestMatch.getName(), String.format("%.2f", bestScore));
            return bestMatch;
        }

        return null;
    }

    /**
     * Нормалізує назву: lowercase, прибирає зайві символи та стоп-слова.
     */
    String normalize(String name) {
        return name.toLowerCase()
                .replaceAll("[«»\"'()\\[\\]]", "")  // лапки, дужки
                .replaceAll("\\s*-\\s*", " ")         // дефіси → пробіли
                .replaceAll("\\s+", " ")              // подвійні пробіли
                .replaceAll("^(навчальна дисципліна|дисципліна)\\s*", "") // стоп-префікси
                .trim();
    }

    /**
     * Витягує значущі слова (≥ 3 літери).
     */
    private Set<String> extractWords(String normalized) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : normalized.split("\\s+")) {
            if (w.length() >= 3) words.add(w);
        }
        return words;
    }

    /**
     * Jaccard similarity: |A ∩ B| / |A ∪ B|.
     */
    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
