package ua.edu.teacherlicence.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDateTime;

/**
 * Базовий клас для всіх сутностей з аудитом та прив'язкою до викладача.
 * Надає: id, teacher, documentUrl, createdAt/updatedAt, createdBy/updatedBy.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    /** Посилання на підтверджуючий PDF (Google Drive) або сайт */
    @Column(columnDefinition = "TEXT")
    private String documentUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Email або ім'я користувача, який створив запис */
    private String createdBy;

    /** Email або ім'я користувача, який останній редагував */
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
