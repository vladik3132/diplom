package ua.edu.teacherlicence.rating.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.rating.dto.*;
import ua.edu.teacherlicence.rating.model.RatingPeriod;
import ua.edu.teacherlicence.rating.model.TeacherRating;
import ua.edu.teacherlicence.rating.repository.RatingPeriodRepository;
import ua.edu.teacherlicence.rating.repository.TeacherRatingRepository;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingPeriodRepository periodRepository;
    private final TeacherRatingRepository ratingRepository;
    private final TeacherRepository teacherRepository;
    private final DepartmentRepository departmentRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;

    public List<RatingPeriodDto> getAllPeriods() {
        return periodRepository.findAll().stream()
                .map(RatingPeriodDto::fromEntity)
                .toList();
    }

    // ── Налаштування "які кафедри беруть участь у рейтингу" ──

    /**
     * Список усіх кафедр з прапорцем чи виключені вони з рейтингу.
     * Сортує: спершу включені (за номером), потім виключені.
     */
    public List<RatingDepartmentSettingDto> getDepartmentSettings() {
        return departmentRepository.findAll().stream()
                .map(d -> RatingDepartmentSettingDto.builder()
                        .departmentId(d.getId())
                        .number(d.getNumber())
                        .name(d.getName())
                        .facultyName(d.getFaculty() != null ? d.getFaculty().getName() : null)
                        .ratingExcluded(Boolean.TRUE.equals(d.getRatingExcluded()))
                        .build())
                .sorted((a, b) -> {
                    if (a.isRatingExcluded() != b.isRatingExcluded()) {
                        return Boolean.compare(a.isRatingExcluded(), b.isRatingExcluded());
                    }
                    String an = a.getNumber() != null ? a.getNumber() : "";
                    String bn = b.getNumber() != null ? b.getNumber() : "";
                    return an.compareTo(bn);
                })
                .toList();
    }

    /**
     * Bulk-оновлення: задано список ID кафедр, що мають бути ВИКЛЮЧЕНІ з рейтингу.
     * Усі інші — автоматично включаються (ratingExcluded=false).
     */
    @Transactional
    public List<RatingDepartmentSettingDto> updateDepartmentSettings(List<Long> excludedDepartmentIds) {
        java.util.Set<Long> excludedSet = excludedDepartmentIds != null
                ? new java.util.HashSet<>(excludedDepartmentIds)
                : java.util.Set.of();

        List<Department> allDepartments = departmentRepository.findAll();
        for (Department d : allDepartments) {
            boolean shouldBeExcluded = excludedSet.contains(d.getId());
            if (Boolean.TRUE.equals(d.getRatingExcluded()) != shouldBeExcluded) {
                d.setRatingExcluded(shouldBeExcluded);
                departmentRepository.save(d);
            }
        }
        return getDepartmentSettings();
    }

    @Transactional
    public RatingPeriodDto createPeriod(RatingPeriodDto dto) {
        // Деактивуємо попередні
        periodRepository.findByActiveTrue().ifPresent(p -> {
            p.setActive(false);
            periodRepository.save(p);
        });
        RatingPeriod period = RatingPeriod.builder()
                .name(dto.getName())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .active(true)
                .build();
        return RatingPeriodDto.fromEntity(periodRepository.save(period));
    }

    @Transactional
    public RatingPeriodDto createPeriodForYear(int year) {
        String name = (year - 1) + "-" + year;
        Optional<RatingPeriod> existing = periodRepository.findByName(name);
        if (existing.isPresent()) {
            return RatingPeriodDto.fromEntity(existing.get());
        }
        periodRepository.findByActiveTrue().ifPresent(p -> {
            p.setActive(false);
            periodRepository.save(p);
        });
        return RatingPeriodDto.fromEntity(periodRepository.save(RatingPeriod.forYear(year)));
    }

    /**
     * Зведений рейтинг всіх викладачів за період.
     */
    public List<TeacherRatingSummaryDto> getTeacherRankings(Long periodId, Long departmentId) {
        // Збираємо бали з teacher_ratings
        List<Object[]> scores;
        if (departmentId != null) {
            scores = ratingRepository.findTotalScoresByPeriodIdAndDepartmentId(periodId, departmentId);
        } else {
            scores = ratingRepository.findTotalScoresByPeriodId(periodId);
        }

        // Map teacherId -> total score
        Map<Long, Integer> scoreMap = new LinkedHashMap<>();
        for (Object[] row : scores) {
            scoreMap.put((Long) row[0], ((Number) row[1]).intValue());
        }

        // Отримуємо ВСІХ викладачів (або по кафедрі) — включно з тими, хто має 0 балів.
        // Виключаються викладачі кафедр з ratingExcluded=true (віртуальні 0/888 тощо).
        List<Teacher> allTeachers;
        if (departmentId != null) {
            allTeachers = teacherRepository.findByDepartmentId(departmentId);
        } else {
            allTeachers = teacherRepository.findByDepartmentRatingExcludedFalse();
        }

        // Будуємо список з усіма викладачами
        List<TeacherRatingSummaryDto> result = new ArrayList<>();
        for (Teacher t : allTeachers) {
            int total = scoreMap.getOrDefault(t.getId(), 0);

            String name = t.getLastName() + " " + t.getFirstName()
                    + (t.getPatronymic() != null ? " " + t.getPatronymic() : "");
            String deptName = t.getDepartment() != null ? t.getDepartment().getName() : "—";
            String deptNumber = t.getDepartment() != null ? t.getDepartment().getNumber() : "";

            String effPos = teacherPositionService.getEffectivePosition(t);
            result.add(TeacherRatingSummaryDto.builder()
                    .teacherId(t.getId())
                    .teacherName(name.trim())
                    .departmentName(deptName)
                    .departmentNumber(deptNumber != null ? deptNumber : "")
                    .position(effPos != null ? effPos : "")
                    .militaryRank(t.getMilitaryRank() != null ? t.getMilitaryRank() : "")
                    .totalScore(total)
                    .rank(0) // буде перераховано нижче
                    .build());
        }

        // Сортуємо за балами (від більшого) і присвоюємо ранг
        result.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setRank(i + 1);
        }

        return result;
    }

    /**
     * Деталізація рейтингу одного викладача за період.
     */
    public TeacherRatingSummaryDto getTeacherRatingDetails(Long periodId, Long teacherId) {
        Teacher t = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Викладача не знайдено: " + teacherId));

        List<TeacherRating> ratings = ratingRepository.findByPeriodIdAndTeacherId(periodId, teacherId);
        int total = ratings.stream().mapToInt(TeacherRating::getScore).sum();

        String name = t.getLastName() + " " + t.getFirstName()
                + (t.getPatronymic() != null ? " " + t.getPatronymic() : "");

        String effPos = teacherPositionService.getEffectivePosition(t);
        return TeacherRatingSummaryDto.builder()
                .teacherId(teacherId)
                .teacherName(name.trim())
                .departmentName(t.getDepartment() != null ? t.getDepartment().getName() : "—")
                .departmentNumber(t.getDepartment() != null && t.getDepartment().getNumber() != null
                        ? t.getDepartment().getNumber() : "")
                .position(effPos != null ? effPos : "")
                .militaryRank(t.getMilitaryRank() != null ? t.getMilitaryRank() : "")
                .totalScore(total)
                .details(ratings.stream().map(TeacherRatingDetailDto::fromEntity).toList())
                .build();
    }

    /**
     * Рейтинг кафедр за період.
     */
    public List<DepartmentRatingSummaryDto> getDepartmentRankings(Long periodId) {
        // Отримуємо всіх викладачів з рейтингом
        List<TeacherRatingSummaryDto> allTeachers = getTeacherRankings(periodId, null);

        // Групуємо по кафедрі
        Map<String, List<TeacherRatingSummaryDto>> byDept = allTeachers.stream()
                .collect(Collectors.groupingBy(TeacherRatingSummaryDto::getDepartmentName));

        List<DepartmentRatingSummaryDto> result = new ArrayList<>();
        for (Map.Entry<String, List<TeacherRatingSummaryDto>> entry : byDept.entrySet()) {
            List<TeacherRatingSummaryDto> teachers = entry.getValue();
            int totalScore = teachers.stream().mapToInt(TeacherRatingSummaryDto::getTotalScore).sum();
            double avg = teachers.isEmpty() ? 0 : (double) totalScore / teachers.size();

            // Знайдемо кафедру
            Long deptId = teachers.stream()
                    .map(TeacherRatingSummaryDto::getTeacherId)
                    .map(tid -> teacherRepository.findById(tid).orElse(null))
                    .filter(Objects::nonNull)
                    .map(Teacher::getDepartment)
                    .filter(Objects::nonNull)
                    .map(Department::getId)
                    .findFirst().orElse(null);

            String facultyName = teachers.stream()
                    .map(TeacherRatingSummaryDto::getTeacherId)
                    .map(tid -> teacherRepository.findById(tid).orElse(null))
                    .filter(Objects::nonNull)
                    .map(Teacher::getDepartment)
                    .filter(Objects::nonNull)
                    .map(d -> d.getFaculty() != null ? d.getFaculty().getName() : null)
                    .filter(Objects::nonNull)
                    .findFirst().orElse("—");

            result.add(DepartmentRatingSummaryDto.builder()
                    .departmentId(deptId)
                    .departmentName(entry.getKey())
                    .facultyName(facultyName)
                    .teacherCount(teachers.size())
                    .totalScore(totalScore)
                    .averageScore(Math.round(avg * 100.0) / 100.0)
                    .teachers(teachers)
                    .build());
        }

        // Сортуємо по середньому балу desc
        result.sort((a, b) -> Double.compare(b.getAverageScore(), a.getAverageScore()));
        int rank = 1;
        for (DepartmentRatingSummaryDto dto : result) {
            dto.setRank(rank++);
        }
        return result;
    }

    /**
     * Рейтинг факультетів за період (агрегація по кафедрах).
     */
    public List<FacultyRatingSummaryDto> getFacultyRankings(Long periodId) {
        List<DepartmentRatingSummaryDto> deptRankings = getDepartmentRankings(periodId);

        // Групуємо кафедри по факультету
        Map<String, List<DepartmentRatingSummaryDto>> byFaculty = deptRankings.stream()
                .collect(Collectors.groupingBy(d -> d.getFacultyName() != null ? d.getFacultyName() : "—"));

        List<FacultyRatingSummaryDto> result = new ArrayList<>();
        for (Map.Entry<String, List<DepartmentRatingSummaryDto>> entry : byFaculty.entrySet()) {
            List<DepartmentRatingSummaryDto> depts = entry.getValue();
            int totalTeachers = depts.stream().mapToInt(DepartmentRatingSummaryDto::getTeacherCount).sum();
            int totalScore = depts.stream().mapToInt(DepartmentRatingSummaryDto::getTotalScore).sum();
            double avg = totalTeachers > 0 ? (double) totalScore / totalTeachers : 0;

            // Знайдемо facultyId
            Long facultyId = depts.stream()
                    .flatMap(d -> d.getTeachers() != null ? d.getTeachers().stream() : java.util.stream.Stream.empty())
                    .map(TeacherRatingSummaryDto::getTeacherId)
                    .map(tid -> teacherRepository.findById(tid).orElse(null))
                    .filter(Objects::nonNull)
                    .map(Teacher::getDepartment)
                    .filter(Objects::nonNull)
                    .map(dept -> dept.getFaculty() != null ? dept.getFaculty().getId() : null)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);

            result.add(FacultyRatingSummaryDto.builder()
                    .facultyId(facultyId)
                    .facultyName(entry.getKey())
                    .departmentCount(depts.size())
                    .teacherCount(totalTeachers)
                    .totalScore(totalScore)
                    .averageScore(Math.round(avg * 100.0) / 100.0)
                    .departments(depts)
                    .build());
        }

        result.sort((a, b) -> Double.compare(b.getAverageScore(), a.getAverageScore()));
        int rank = 1;
        for (FacultyRatingSummaryDto dto : result) {
            dto.setRank(rank++);
        }
        return result;
    }
}
