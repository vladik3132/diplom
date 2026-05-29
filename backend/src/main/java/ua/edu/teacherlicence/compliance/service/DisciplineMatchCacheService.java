package ua.edu.teacherlicence.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;
import ua.edu.teacherlicence.compliance.model.TeacherComplianceCache;
import ua.edu.teacherlicence.compliance.model.TeacherDisciplineMatchCache;
import ua.edu.teacherlicence.compliance.repository.TeacherComplianceCacheRepository;
import ua.edu.teacherlicence.compliance.repository.TeacherDisciplineMatchCacheRepository;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.opp.service.EducationalProgramService;
import ua.edu.teacherlicence.opp.service.EducationalProgramService.Point37Result;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Кеш п.36+п.37 для пар (teacher × discipline).
 * Refresh викликається listeners на зміни:
 *  - Achievement/Publication/Education/Qualification CUD → refresh для всіх дисциплін teacher
 *  - TeacherDiscipline assign → створити рядок і обчислити
 *  - TeacherDiscipline remove → видалити рядок
 *  - Discipline changed → refresh для всіх teachers цієї дисципліни
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisciplineMatchCacheService {

    private final TeacherDisciplineMatchCacheRepository cacheRepo;
    private final TeacherComplianceCacheRepository complianceCacheRepo;
    private final TeacherDisciplineRepository teacherDisciplineRepo;

    @Autowired @Lazy
    private DisciplineMatchCacheService self;

    @Autowired
    private EducationalProgramService educationalProgramService;

    // ═══ READ ═══

    @Transactional(readOnly = true)
    public Optional<TeacherDisciplineMatchCache> get(Long teacherId, Long disciplineId) {
        return cacheRepo.findByTeacherIdAndDisciplineId(teacherId, disciplineId);
    }

    @Transactional(readOnly = true)
    public List<TeacherDisciplineMatchCache> findByDisciplineId(Long disciplineId) {
        return cacheRepo.findByDisciplineId(disciplineId);
    }

    @Transactional(readOnly = true)
    public List<TeacherDisciplineMatchCache> findByDisciplineIds(List<Long> disciplineIds) {
        if (disciplineIds == null || disciplineIds.isEmpty()) return List.of();
        return cacheRepo.findByDisciplineIdIn(disciplineIds);
    }

    // ═══ REFRESH ═══

    /** Refresh одну пару (teacher × discipline). Викликає AI. */
    @Transactional
    public void refresh(Long teacherId, Long disciplineId) {
        try {
            Point37Result p37 = educationalProgramService.computePoint37ForDiscipline(teacherId, disciplineId);
            if (p37 == null) {
                // Teacher або Discipline зник — видаляємо рядок
                cacheRepo.deleteByTeacherAndDiscipline(teacherId, disciplineId);
                return;
            }
            // point36 беремо з teacher_compliance_cache (unique_type_count ≥ 4, не EXEMPT)
            boolean point36 = complianceCacheRepo.findById(teacherId)
                    .map(c -> c.getStatus() == ComplianceStatus.COMPLIANT
                           || c.getStatus() == ComplianceStatus.EXEMPT)
                    .orElse(false);

            TeacherDisciplineMatchCache existing = cacheRepo
                    .findByTeacherIdAndDisciplineId(teacherId, disciplineId)
                    .orElse(TeacherDisciplineMatchCache.builder()
                            .teacherId(teacherId)
                            .disciplineId(disciplineId)
                            .build());

            existing.setHasMatchingDiploma(p37.hasMatchingDiploma());
            existing.setHasMatchingDegree(p37.hasMatchingDegree());
            existing.setHasPracticalExperience(p37.hasPracticalExperience());
            existing.setHasDissertationSupervision(p37.hasDissertationSupervision());
            existing.setPoint37aCompliant(p37.point37aCompliant());
            existing.setQualifiedPublicationsCount(p37.qualifiedPublicationsCount());
            existing.setPoint37bCompliant(p37.point37bCompliant());
            existing.setPoint37Compliant(p37.point37Compliant());
            existing.setPoint36Compliant(point36);
            existing.setFullyCompliant(point36 && p37.point37Compliant());
            existing.setAiLastComputedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            cacheRepo.save(existing);
        } catch (Exception e) {
            log.error("DisciplineMatch refresh failed for t={} d={}: {}", teacherId, disciplineId, e.getMessage(), e);
        }
    }

    /** Обновити всі дисципліни, які викладає teacher. */
    @Transactional
    public void refreshAllForTeacher(Long teacherId) {
        Set<Long> disciplineIds = teacherDisciplineRepo.findAll().stream()
                .filter(td -> td.getTeacher() != null && teacherId.equals(td.getTeacher().getId()))
                .map(td -> td.getDiscipline() != null ? td.getDiscipline().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long did : disciplineIds) {
            self.refresh(teacherId, did);
        }
    }

    /** Обновити всіх викладачів цієї дисципліни. */
    @Transactional
    public void refreshAllForDiscipline(Long disciplineId) {
        List<TeacherDiscipline> assignments = teacherDisciplineRepo.findAll().stream()
                .filter(td -> td.getDiscipline() != null && disciplineId.equals(td.getDiscipline().getId()))
                .toList();
        for (TeacherDiscipline td : assignments) {
            if (td.getTeacher() != null) {
                self.refresh(td.getTeacher().getId(), disciplineId);
            }
        }
    }

    /** Видалити рядок (коли TeacherDiscipline знято). */
    @Transactional
    public void remove(Long teacherId, Long disciplineId) {
        cacheRepo.deleteByTeacherAndDiscipline(teacherId, disciplineId);
    }

    @Transactional
    public void removeAllForTeacher(Long teacherId) {
        cacheRepo.deleteByTeacherId(teacherId);
    }

    @Transactional
    public void removeAllForDiscipline(Long disciplineId) {
        cacheRepo.deleteByDisciplineId(disciplineId);
    }
}
