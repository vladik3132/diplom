package ua.edu.teacherlicence.file.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "file_attachments",
        indexes = @Index(name = "idx_file_entity", columnList = "entityType, entityId"))
public class FileAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    /** Google Drive file ID (null if local storage) */
    private String driveFileId;

    /** Local file path for dev fallback (null if Google Drive) */
    @Column(length = 500)
    private String localPath;

    @Column(nullable = false, length = 500)
    private String originalName;

    @Column(nullable = false, length = 100)
    private String mimeType;

    private Long fileSize;

    private String uploadedBy;

    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
