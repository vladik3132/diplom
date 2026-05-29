package ua.edu.teacherlicence.department.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Номер кафедри (напр. 11, 22, 3) */
    private String number;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    /**
     * Чи виключена кафедра з рейтингування.
     * Використовується для "віртуальних" кафедр управління (number=0, 888)
     * — викладачі цих кафедр ведуть пари, але рейтинг їм не нараховується.
     * За замовчуванням false. Колонка NOT NULL DEFAULT FALSE — Boolean wrapper
     * для безпеки при serialize/deserialize (null → серіалізація може падати).
     */
    @Column(name = "rating_excluded", nullable = false)
    @Builder.Default
    private Boolean ratingExcluded = false;
}
