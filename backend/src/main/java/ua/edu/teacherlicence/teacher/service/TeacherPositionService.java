package ua.edu.teacherlicence.teacher.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.common.PositionSeniority;
import ua.edu.teacherlicence.department.model.StaffPosition;
import ua.edu.teacherlicence.department.repository.StaffPositionRepository;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Канонічне джерело правди для "поточної посади викладача".
 *
 * <p>Логіка пріоритету:
 * <ol>
 *   <li>Якщо у викладача є записи у {@code staff_positions} — обираємо
 *       primary за {@link PositionSeniority} (Начальник кафедри > Доцент > ...)</li>
 *   <li>Інакше — fallback на застаріле поле {@code Teacher.position} (буде видалено
 *       у Стадії 5 рефакторингу)</li>
 * </ol>
 *
 * <p>Для batch-операцій (DTO збагачення списків) є метод
 * {@link #getEffectivePositions(List)} — один SQL замість N запитів.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherPositionService {

    private final StaffPositionRepository staffPositionRepository;

    /**
     * Повертає primary назву посади для одного викладача (через одиничний SQL).
     * Якщо у викладача декілька штатних позицій — обирає ту з найвищим
     * рангом старшинства. У разі рівних рангів — найменший orderNumber.
     *
     * @return назва primary посади або {@code null} якщо у викладача немає
     *         жодної штатної позиції
     */
    @Transactional(readOnly = true)
    public String getEffectivePosition(Teacher teacher) {
        if (teacher == null) return null;
        List<StaffPosition> positions = staffPositionRepository.findByTeacherId(teacher.getId());
        return chooseBest(positions);
    }

    /**
     * Batch-варіант: для списку викладачів — один SQL, повертає
     * мапу teacherId → effectivePosition. Викладачі без штатних позицій
     * не потраплять у мапу — викликач отримує {@code null}.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getEffectivePositions(List<Teacher> teachers) {
        if (teachers == null || teachers.isEmpty()) return Collections.emptyMap();
        List<Long> ids = teachers.stream().map(Teacher::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        List<StaffPosition> all = staffPositionRepository.findByTeacherIdIn(ids);
        // Групуємо за teacherId
        Map<Long, List<StaffPosition>> byTeacher = new HashMap<>();
        for (StaffPosition sp : all) {
            if (sp.getTeacher() != null && sp.getTeacher().getId() != null) {
                byTeacher.computeIfAbsent(sp.getTeacher().getId(), k -> new java.util.ArrayList<>()).add(sp);
            }
        }
        Map<Long, String> result = new HashMap<>();
        for (Teacher t : teachers) {
            List<StaffPosition> list = byTeacher.get(t.getId());
            String best = chooseBest(list);
            if (best != null && !best.isBlank()) {
                result.put(t.getId(), best);
            }
        }
        return result;
    }

    /**
     * Сума ставок усіх штатних позицій викладача. Викладач може мати декілька
     * рядків (наприклад 0.5+0.5) — повертаємо суму. Якщо штатних позицій
     * немає — {@code null}.
     */
    @Transactional(readOnly = true)
    public Map<Long, Double> getTotalRates(List<Teacher> teachers) {
        if (teachers == null || teachers.isEmpty()) return Collections.emptyMap();
        List<Long> ids = teachers.stream().map(Teacher::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        List<StaffPosition> all = staffPositionRepository.findByTeacherIdIn(ids);
        Map<Long, Double> result = new HashMap<>();
        for (StaffPosition sp : all) {
            if (sp.getTeacher() == null || sp.getTeacher().getId() == null) continue;
            double rate = sp.getRate() != null ? sp.getRate() : 1.0;
            result.merge(sp.getTeacher().getId(), rate, Double::sum);
        }
        return result;
    }

    /**
     * Batch: повертає primary {@link StaffPosition} для кожного викладача
     * (одна штатна одиниця, на якій він займає найвищу посаду за seniority).
     * Використовується коли потрібен ШПК / тариф / ставка primary позиції,
     * а не лише назва посади.
     */
    @Transactional(readOnly = true)
    public Map<Long, StaffPosition> getPrimaryStaffPositions(List<Teacher> teachers) {
        if (teachers == null || teachers.isEmpty()) return Collections.emptyMap();
        List<Long> ids = teachers.stream().map(Teacher::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        List<StaffPosition> all = staffPositionRepository.findByTeacherIdIn(ids);
        Map<Long, List<StaffPosition>> byTeacher = new HashMap<>();
        for (StaffPosition sp : all) {
            if (sp.getTeacher() != null && sp.getTeacher().getId() != null) {
                byTeacher.computeIfAbsent(sp.getTeacher().getId(), k -> new java.util.ArrayList<>()).add(sp);
            }
        }
        Map<Long, StaffPosition> result = new HashMap<>();
        for (Map.Entry<Long, List<StaffPosition>> e : byTeacher.entrySet()) {
            StaffPosition primary = pickPrimary(e.getValue());
            if (primary != null) {
                result.put(e.getKey(), primary);
            }
        }
        return result;
    }

    /**
     * Прапорець "primary посада викладача — це bootstrapped запис".
     * UI підсвічує таких викладачів попередженням ⚠️.
     */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> getBootstrappedPositionFlags(List<Teacher> teachers) {
        if (teachers == null || teachers.isEmpty()) return Collections.emptyMap();
        List<Long> ids = teachers.stream().map(Teacher::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        List<StaffPosition> all = staffPositionRepository.findByTeacherIdIn(ids);
        Map<Long, List<StaffPosition>> byTeacher = new HashMap<>();
        for (StaffPosition sp : all) {
            if (sp.getTeacher() != null && sp.getTeacher().getId() != null) {
                byTeacher.computeIfAbsent(sp.getTeacher().getId(), k -> new java.util.ArrayList<>()).add(sp);
            }
        }
        Map<Long, Boolean> result = new HashMap<>();
        for (Map.Entry<Long, List<StaffPosition>> e : byTeacher.entrySet()) {
            StaffPosition primary = pickPrimary(e.getValue());
            if (primary != null) {
                result.put(e.getKey(), Boolean.TRUE.equals(primary.getBootstrapped()));
            }
        }
        return result;
    }

    /**
     * Гарантує що для викладача існує принаймні одна штатна позиція.
     * Якщо немає — створює bootstrap-запис (rate=1.0, bootstrapped=true).
     * Використовується імпортерами при створенні нових teacher-записів.
     */
    @Transactional
    public void ensureStaffPosition(Teacher teacher) {
        if (teacher == null || teacher.getId() == null) return;
        if (teacher.getPosition() == null || teacher.getPosition().isBlank()) return;
        if (teacher.getDepartment() == null || teacher.getDepartment().getId() == null) return;
        List<StaffPosition> existing = staffPositionRepository.findByTeacherId(teacher.getId());
        if (!existing.isEmpty()) return;

        // Знайти max orderNumber по кафедрі
        List<StaffPosition> deptPositions =
                staffPositionRepository.findByDepartmentIdOrderByOrderNumber(teacher.getDepartment().getId());
        int nextOrder = deptPositions.stream()
                .map(StaffPosition::getOrderNumber)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        StaffPosition sp = StaffPosition.builder()
                .department(teacher.getDepartment())
                .orderNumber(nextOrder)
                .positionTitle(teacher.getPosition())
                .rate(1.0)
                .teacher(teacher)
                .bootstrapped(true)
                .build();
        staffPositionRepository.save(sp);
        log.info("Auto-created bootstrap StaffPosition for teacher {} ({})",
                teacher.getId(), teacher.getPosition());
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /**
     * Обирає primary посаду зі списку StaffPosition.
     * Pravidla: найменший seniority rank, при рівних — найменший orderNumber.
     */
    private StaffPosition pickPrimary(List<StaffPosition> positions) {
        if (positions == null || positions.isEmpty()) return null;
        return positions.stream()
                .min(Comparator
                        .comparingInt((StaffPosition sp) -> PositionSeniority.rankOf(sp.getPositionTitle()))
                        .thenComparingInt(sp -> sp.getOrderNumber() != null ? sp.getOrderNumber() : Integer.MAX_VALUE))
                .orElse(null);
    }

    /**
     * Зі списку штатних позицій вибирає primary positionTitle.
     * Повертає {@code null} якщо список порожній або у primary немає title.
     */
    private String chooseBest(List<StaffPosition> positions) {
        StaffPosition primary = pickPrimary(positions);
        if (primary != null && primary.getPositionTitle() != null && !primary.getPositionTitle().isBlank()) {
            return primary.getPositionTitle();
        }
        return null;
    }
}
