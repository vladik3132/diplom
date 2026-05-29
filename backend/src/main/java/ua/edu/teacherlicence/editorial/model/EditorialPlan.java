package ua.edu.teacherlicence.editorial.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.department.model.Department;

import java.time.LocalDateTime;

@Entity
@Table(name = "editorial_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditorialPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "academic_year")
    private String academicYear;

    private String title;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
