package ua.edu.teacherlicence.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;
import ua.edu.teacherlicence.achievement.service.ComplianceService;
import ua.edu.teacherlicence.compliance.model.TeacherComplianceCache;
import ua.edu.teacherlicence.compliance.repository.TeacherComplianceCacheRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Кеш-обгортка над {@link ComplianceService} — обчислює compliance-репорт один раз
 * (при зміні даних викладача) і зберігає у {@code teacher_compliance_cache}.
 *
 * Читання — через getByTeacherId/getAll/getByStatus → 1 SELECT замість 10+.
 *
 * Refresh тригериться {@link ua.edu.teacherlicence.compliance.events.ComplianceEvents}
 * через ComplianceEventListener.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceCacheService {

    private final TeacherComplianceCacheRepository cacheRepo;
    private final TeacherRepository teacherRepo;
    private final ObjectMapper objectMapper;

    /** Self-proxy для @Transactional на @Async методах. */
    @Autowired @Lazy
    private ComplianceCacheService self;

    /** Compute-сервіс (існуючий). ComplianceService.checkCompliance робить всю роботу. */
    @Autowired
    private ComplianceService complianceService;

    /**
     * Composer для пересборки Achievement-записів. Викликається перед перерахунком
     * compliance, щоб гарантувати наявність Achievement.PP_20 для викладачів,
     * у яких заповнено лише career_records (Послужний список).
     */
    @Autowired
    private ua.edu.teacherlicence.achievement.service.AchievementComposer achievementComposer;

    // ═══════════════════════════════════════════════
    //  READ
    // ═══════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Optional<ComplianceReportDto> getByTeacherId(Long teacherId) {
        return cacheRepo.findById(teacherId).map(this::toDto);
    }

    /**
     * Повертає compliance-репорти ВСІХ викладачів із кешу.
     * Якщо запис для якогось викладача відсутній (нова реєстрація) — на льоту обчислює
     * і зберігає (lazy warmup).
     */
    @Transactional
    public List<ComplianceReportDto> getAll() {
        Map<Long, TeacherComplianceCache> byId = cacheRepo.findAll().stream()
                .collect(Collectors.toMap(TeacherComplianceCache::getTeacherId, c -> c));

        List<ComplianceReportDto> result = new ArrayList<>();
        for (var teacher : teacherRepo.findAll()) {
            var cached = byId.get(teacher.getId());
            if (cached != null) {
                result.add(toDto(cached));
            } else {
                // Lazy warmup: обчислюємо і одразу кешуємо
                var dto = refreshTeacherSync(teacher.getId());
                if (dto != null) result.add(dto);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ComplianceReportDto> getByStatus(ComplianceStatus status) {
        return cacheRepo.findByStatus(status).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplianceReportDto> getNonCompliant() {
        return cacheRepo.findByStatusIn(List.of(ComplianceStatus.NON_COMPLIANT, ComplianceStatus.WARNING))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplianceReportDto> getByDepartmentId(Long departmentId) {
        return teacherRepo.findByDepartmentId(departmentId).stream()
                .map(t -> cacheRepo.findById(t.getId()).map(this::toDto).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ═══════════════════════════════════════════════
    //  REFRESH (write)
    // ═══════════════════════════════════════════════

    /** Синхронний refresh — використовується з async-listener і з manual endpoint. */
    @Transactional
    public ComplianceReportDto refreshTeacherSync(Long teacherId) {
        try {
            var teacherOpt = teacherRepo.findById(teacherId);
            if (teacherOpt.isEmpty()) {
                cacheRepo.deleteByTeacherId(teacherId);
                return null;
            }
            // Перед обчисленням compliance — оновлюємо Achievement-записи,
            // щоб тригерити автостворення PP_20 для тих викладачів, у яких є
            // career_records (Послужний список) але немає practical_experience.
            // Без цього прогрес п.20 буде порожнім → A3 у п.37 = false.
            try {
                achievementComposer.recomposeForTeacher(teacherOpt.get());
            } catch (Exception e) {
                log.warn("recomposeForTeacher failed for teacher {} during compliance refresh: {}",
                        teacherId, e.getMessage());
            }
            ComplianceReportDto dto = complianceService.checkCompliance(teacherId);
            TeacherComplianceCache entity = fromDto(dto);
            cacheRepo.save(entity);
            return dto;
        } catch (Exception e) {
            log.error("Failed to refresh compliance cache for teacher {}: {}", teacherId, e.getMessage(), e);
            return null;
        }
    }

    /** Повна перебудова кешу (manual/admin endpoint). */
    @Transactional
    public int refreshAll() {
        int count = 0;
        for (var t : teacherRepo.findAll()) {
            if (self.refreshTeacherSync(t.getId()) != null) count++;
        }
        log.info("Compliance cache fully rebuilt: {} teachers", count);
        return count;
    }

    /** Перебудова кешу для викладачів конкретної кафедри. */
    @Transactional
    public int refreshByDepartmentId(Long departmentId) {
        int count = 0;
        for (var t : teacherRepo.findByDepartmentId(departmentId)) {
            if (self.refreshTeacherSync(t.getId()) != null) count++;
        }
        log.info("Compliance cache rebuilt for department {}: {} teachers", departmentId, count);
        return count;
    }

    /** Видалити з кешу (при видаленні викладача). Cascade ON DELETE спрацює автоматично, але можна явно. */
    @Transactional
    public void evict(Long teacherId) {
        cacheRepo.deleteByTeacherId(teacherId);
    }

    // ═══════════════════════════════════════════════
    //  Mapping
    // ═══════════════════════════════════════════════

    private ComplianceReportDto toDto(TeacherComplianceCache c) {
        return ComplianceReportDto.builder()
                .teacherId(c.getTeacherId())
                .teacherName(teacherName(c.getTeacherId()))
                .status(c.getStatus())
                .exemptionReason(c.getExemptionReason())
                .achievementCount(nvl(c.getAchievementCount()))
                .uniqueTypeCount(nvl(c.getUniqueTypeCount()))
                .achievementTypes(fromJsonList(c.getAchievementTypes()))
                .missingInfo(fromJsonList(c.getMissingInfo()))
                .publicationsCount(nvl(c.getPublicationsCount()))
                .relevantPublicationsCount(nvl(c.getRelevantPublicationsCount()))
                .diplomaMatchesDepartment(c.isDiplomaMatchesDepartment())
                .degreeMatchesDepartment(c.isDegreeMatchesDepartment())
                .qualificationMatchesDepartment(c.isQualificationMatchesDepartment())
                .titleMatchesDepartment(c.isTitleMatchesDepartment())
                .build();
    }

    private TeacherComplianceCache fromDto(ComplianceReportDto dto) {
        return TeacherComplianceCache.builder()
                .teacherId(dto.getTeacherId())
                .status(dto.getStatus())
                .exemptionReason(dto.getExemptionReason())
                .uniqueTypeCount(dto.getUniqueTypeCount())
                .achievementCount(dto.getAchievementCount())
                .publicationsCount(dto.getPublicationsCount())
                .relevantPublicationsCount(dto.getRelevantPublicationsCount())
                .achievementTypes(toJsonList(dto.getAchievementTypes()))
                .missingInfo(toJsonList(dto.getMissingInfo()))
                .diplomaMatchesDepartment(dto.isDiplomaMatchesDepartment())
                .degreeMatchesDepartment(dto.isDegreeMatchesDepartment())
                .qualificationMatchesDepartment(dto.isQualificationMatchesDepartment())
                .titleMatchesDepartment(dto.isTitleMatchesDepartment())
                .aiLastComputedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String teacherName(Long teacherId) {
        return teacherRepo.findById(teacherId).map(t -> {
            String ln = t.getLastName() != null ? t.getLastName() : "";
            String fn = t.getFirstName() != null ? " " + t.getFirstName() : "";
            String pn = t.getPatronymic() != null ? " " + t.getPatronymic() : "";
            return (ln + fn + pn).trim();
        }).orElse("");
    }

    private String toJsonList(List<String> list) {
        if (list == null) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private int nvl(Integer i) { return i != null ? i : 0; }
}
