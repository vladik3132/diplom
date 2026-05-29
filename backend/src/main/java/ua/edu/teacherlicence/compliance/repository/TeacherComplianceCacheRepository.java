package ua.edu.teacherlicence.compliance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.achievement.dto.ComplianceReportDto.ComplianceStatus;
import ua.edu.teacherlicence.compliance.model.TeacherComplianceCache;

import java.util.List;

@Repository
public interface TeacherComplianceCacheRepository extends JpaRepository<TeacherComplianceCache, Long> {
    List<TeacherComplianceCache> findByStatusIn(List<ComplianceStatus> statuses);
    List<TeacherComplianceCache> findByStatus(ComplianceStatus status);
    long countByStatus(ComplianceStatus status);
    void deleteByTeacherId(Long teacherId);
}
