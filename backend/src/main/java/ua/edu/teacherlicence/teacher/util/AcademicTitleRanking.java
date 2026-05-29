package ua.edu.teacherlicence.teacher.util;

import ua.edu.teacherlicence.teacher.model.AcademicTitle;

import java.util.Comparator;
import java.util.List;

public final class AcademicTitleRanking {

    private AcademicTitleRanking() {}

    /** Ранг звання: 3 = Професор, 2 = Доцент, 1 = СНС/СНД/старший дослідник, 0 = інше або відсутня назва. */
    public static int rank(AcademicTitle t) {
        String s = t == null || t.getTitleName() == null ? "" : t.getTitleName().toLowerCase();
        if (s.isEmpty()) return 0;
        if (s.contains("професор")) return 3;
        if (s.contains("доцент")) return 2;
        if (s.contains("снс") || s.contains("снд")
                || s.contains("старший науковий співробітник")
                || s.contains("старший дослідник")) return 1;
        return 0;
    }

    /** Найвище за рангом звання. Повертає {@code null} для порожнього списку. */
    public static AcademicTitle primary(List<AcademicTitle> titles) {
        if (titles == null || titles.isEmpty()) return null;
        return titles.stream()
                .max(Comparator.comparingInt(AcademicTitleRanking::rank))
                .orElse(titles.get(titles.size() - 1));
    }
}
