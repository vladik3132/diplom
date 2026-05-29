package ua.edu.teacherlicence.achievement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "achievements")
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @Enumerated(EnumType.STRING)
    private AchievementType achievementType;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate dateAchieved;

    private String documentUrl;

    @Builder.Default
    private boolean verified = false;

    private String verifiedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Кількість кваліфікованих записів (для пп.1 — фахових публікацій, що відповідають напряму) */
    private Integer qualifiedCount;

    /** Публікації, що належать до цього досягнення */
    @OneToMany(mappedBy = "achievement", fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<Publication> publications = new ArrayList<>();

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
