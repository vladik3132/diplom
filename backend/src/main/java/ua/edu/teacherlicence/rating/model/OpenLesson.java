package ua.edu.teacherlicence.rating.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ua.edu.teacherlicence.common.model.BaseAuditEntity;

import java.time.LocalDate;

/**
 * Міжкафедральне (показове/відкрите) заняття.
 * Рейтинг: 3 бали за заняття.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "open_lessons")
public class OpenLesson extends BaseAuditEntity {

    /** Тема заняття */
    @Column(columnDefinition = "TEXT")
    private String topic;

    /** Дата проведення */
    private LocalDate date;

    /** На якій кафедрі проведено */
    private String hostDepartment;

    /** Тип: показове, відкрите, міжкафедральне */
    private String lessonType;

    /** Номер наказу */
    private String orderNumber;

    /** Дата наказу */
    private LocalDate orderDate;

    /** Примітки */
    @Column(columnDefinition = "TEXT")
    private String notes;
}
