package ua.edu.teacherlicence.editorial.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDate;

@Entity
@Table(name = "editorial_plan_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditorialPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private EditorialPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    private String title;

    private String type;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Enumerated(EnumType.STRING)
    private EditorialItemStatus status;

    public enum EditorialItemStatus {
        PLANNED, IN_PROGRESS, COMPLETED, OVERDUE
    }
}
