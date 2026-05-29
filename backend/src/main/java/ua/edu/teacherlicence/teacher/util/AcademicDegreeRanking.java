package ua.edu.teacherlicence.teacher.util;

import ua.edu.teacherlicence.teacher.model.AcademicDegree;

import java.util.Comparator;
import java.util.List;

public final class AcademicDegreeRanking {

    private AcademicDegreeRanking() {}

    /** Ранг ступеня: 3 = Доктор наук, 2 = Доктор філософії / Кандидат / PhD, 1 = інше, 0 = відсутня назва. */
    public static int rank(AcademicDegree d) {
        String s = d == null || d.getDegree() == null ? "" : d.getDegree().toLowerCase();
        if (s.isEmpty()) return 0;
        if (s.contains("доктор") && !s.contains("філософ")) return 3;
        if (s.contains("phd") || s.contains("філософ") || s.contains("кандидат")) return 2;
        return 1;
    }

    /** Найвищий за рангом ступінь зі списку. Повертає {@code null} якщо список порожній. */
    public static AcademicDegree primary(List<AcademicDegree> degrees) {
        if (degrees == null || degrees.isEmpty()) return null;
        return degrees.stream()
                .max(Comparator.comparingInt(AcademicDegreeRanking::rank))
                .orElse(degrees.get(degrees.size() - 1));
    }

    public static boolean isDoctorOfScience(String degreeName) {
        if (degreeName == null) return false;
        String s = degreeName.toLowerCase();
        return s.contains("доктор") && !s.contains("філософ");
    }
}
