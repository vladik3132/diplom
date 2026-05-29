package ua.edu.teacherlicence.docx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "docx_export_templates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocxExportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Ім'я файлу на диску (UUID-prefix) */
    private String templateFileName;

    /** Оригінальне ім'я файлу при завантаженні */
    private String originalFileName;

    /** JSON масив маппінгу колонок: [{columnIndex, headerText, fieldKey}] */
    @Column(columnDefinition = "TEXT")
    private String columnMappingsJson;

    /** Індекс таблиці в документі (0-based) */
    @Builder.Default
    private Integer tableIndex = 0;

    /** Кількість рядків заголовку таблиці */
    @Builder.Default
    private Integer headerRowCount = 1;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
