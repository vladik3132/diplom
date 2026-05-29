package ua.edu.teacherlicence.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.compliance.model.TeacherProgramMatchCache;
import ua.edu.teacherlicence.compliance.repository.TeacherProgramMatchCacheRepository;
import ua.edu.teacherlicence.discipline.repository.DisciplineRepository;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.opp.service.EducationalProgramService;
import ua.edu.teacherlicence.opp.service.EducationalProgramService.Point37Result;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Кеш п.37 для пар (teacher × educational_program).
 * Викладач пов'язаний з ОПП через дисципліни:
 *  - якщо хоча б одна дисципліна ОПП викладається цим вчителем → оновлюємо
 *  - якщо викладач більше не веде жодної дисципліни ОПП → видаляємо з кешу
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramMatchCacheService {

    private final TeacherProgramMatchCacheRepository cacheRepo;
    private final TeacherDisciplineRepository teacherDisciplineRepo;
    private final DisciplineRepository disciplineRepo;
    private final EducationalProgramRepository programRepo;

    @Autowired @Lazy
    private ProgramMatchCacheService self;

    @Autowired
    private EducationalProgramService educationalProgramService;

    // ═══ READ ═══

    @Transactional(readOnly = true)
    public List<TeacherProgramMatchCache> findByProgramId(Long programId) {
        return cacheRepo.findByProgramId(programId);
    }

    // ═══ REFRESH ═══

    @Transactional
    public void refresh(Long teacherId, Long programId) {
        try {
            // Перевірка: чи веде ще викладач хоч одну дисципліну цієї ОПП?
            boolean stillTeaches = teacherDisciplineRepo.findAll().stream()
                    .anyMatch(td -> td.getTeacher() != null
                            && teacherId.equals(td.getTeacher().getId())
                            && td.getDiscipline() != null
                            && td.getDiscipline().getEducationalProgram() != null
                            && programId.equals(td.getDiscipline().getEducationalProgram().getId()));
            if (!stillTeaches) {
                cacheRepo.deleteByTeacherAndProgram(teacherId, programId);
                return;
            }

            Point37Result p37 = educationalProgramService.computePoint37ForProgram(teacherId, programId);
            if (p37 == null) {
                cacheRepo.deleteByTeacherAndProgram(teacherId, programId);
                return;
            }

            TeacherProgramMatchCache existing = cacheRepo
                    .findByTeacherIdAndProgramId(teacherId, programId)
                    .orElse(TeacherProgramMatchCache.builder()
                            .teacherId(teacherId)
                            .programId(programId)
                            .build());

            existing.setHasMatchingDiploma(p37.hasMatchingDiploma());
            existing.setHasMatchingDegree(p37.hasMatchingDegree());
            existing.setHasPracticalExperience(p37.hasPracticalExperience());
            existing.setHasDissertationSupervision(p37.hasDissertationSupervision());
            existing.setPoint37aCompliant(p37.point37aCompliant());
            existing.setQualifiedPublicationsCount(p37.qualifiedPublicationsCount());
            existing.setPoint37bCompliant(p37.point37bCompliant());
            existing.setPoint37Compliant(p37.point37Compliant());
            existing.setAiLastComputedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            cacheRepo.save(existing);
        } catch (Exception e) {
            log.error("ProgramMatch refresh failed t={} p={}: {}", teacherId, programId, e.getMessage(), e);
        }
    }

    /** Коли викладача змінили (Education, Achievement тощо) — refresh всі його програми. */
    @Transactional
    public void refreshAllForTeacher(Long teacherId) {
        Set<Long> programIds = teacherDisciplineRepo.findAll().stream()
                .filter(td -> td.getTeacher() != null && teacherId.equals(td.getTeacher().getId()))
                .map(td -> {
                    var d = td.getDiscipline();
                    if (d == null || d.getEducationalProgram() == null) return null;
                    return d.getEducationalProgram().getId();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long pid : programIds) {
            self.refresh(teacherId, pid);
        }
    }

    /** Коли ОПП змінилась — refresh всі пари (teacher × program). */
    @Transactional
    public void refreshAllForProgram(Long programId) {
        // Знаходимо всіх teachers через дисципліни цієї ОПП
        Set<Long> teacherIds = disciplineRepo.findAll().stream()
                .filter(d -> d.getEducationalProgram() != null && programId.equals(d.getEducationalProgram().getId()))
                .flatMap(d -> teacherDisciplineRepo.findAll().stream()
                        .filter(td -> td.getDiscipline() != null && d.getId().equals(td.getDiscipline().getId())))
                .map(td -> td.getTeacher() != null ? td.getTeacher().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long tid : teacherIds) {
            self.refresh(tid, programId);
        }
    }

    @Transactional
    public void removeAllForTeacher(Long teacherId) {
        cacheRepo.deleteByTeacherId(teacherId);
    }

    @Transactional
    public void removeAllForProgram(Long programId) {
        cacheRepo.deleteByProgramId(programId);
    }
}
