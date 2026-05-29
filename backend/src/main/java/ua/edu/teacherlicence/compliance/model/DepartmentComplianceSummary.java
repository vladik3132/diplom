package ua.edu.teacherlicence.compliance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.time.LocalDateTime;

/**
 * READ-ONLY view-entity, мапиться на materialized view {@code department_compliance_summary}.
 *
 * MV оновлюється через {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} —
 * це технічне оновлення, що займає < 1 сек; виконується scheduled-ом у
 * {@code DepartmentComplianceSummaryService} + подіями зміни teacher_compliance_cache.
 *
 * @Subselect + @Synchronize каже Hibernate: НЕ створювати цю "таблицю" через
 * ddl-auto — це вже існуюча MV (створена Liquibase). Коли змінюються teachers
 * або teacher_compliance_cache — Hibernate скине 2-nd level cache на цю entity.
 */
@Entity
@Immutable
@Subselect("SELECT * FROM department_compliance_summary")
@Synchronize({"teachers", "teacher_compliance_cache"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentComplianceSummary {

    @Id
    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_number")
    private String departmentNumber;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "faculty_name")
    private String facultyName;

    private Integer totalTeachers;
    private Integer mainEmploymentTeachers;
    private Integer partTimeTeachers;

    private Integer withDegreeAndMainCount;
    private Double  withDegreeAndMainPercent;

    private Integer doctorsOrProfessorsCount;
    private Double  doctorsOrProfessorsPercent;

    @Column(name = "point38_compliant")
    private Integer point38Compliant;

    @Column(name = "point38_warning")
    private Integer point38Warning;

    @Column(name = "point38_non_compliant")
    private Integer point38NonCompliant;

    @Column(name = "point38_exempt")
    private Integer point38Exempt;

    private LocalDateTime refreshedAt;

    /** Чи п.35 виконано (≥ 50% MAIN зі ступенем/званням). */
    public boolean isPoint35Compliant() {
        return withDegreeAndMainPercent != null && withDegreeAndMainPercent >= 50.0;
    }

    /** GOOD / WARNING / CRITICAL — згідно бізнес-логіки. */
    public String overallStatus() {
        boolean p35 = isPoint35Compliant();
        int nonComp = point38NonCompliant != null ? point38NonCompliant : 0;
        double p35pct = withDegreeAndMainPercent != null ? withDegreeAndMainPercent : 0.0;

        if (p35 && nonComp == 0) return "GOOD";
        if (p35pct >= 40.0 && nonComp <= 1) return "WARNING";
        return "CRITICAL";
    }
}
