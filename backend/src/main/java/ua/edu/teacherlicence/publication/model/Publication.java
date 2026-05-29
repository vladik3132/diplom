package ua.edu.teacherlicence.publication.model;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "publications")
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Enumerated(EnumType.STRING)
    private PublicationType type;

    /** Категорія — тільки для ARTICLE (Scopus/WoS/Категорія А/Б). Для APPROBATION → approbationSubtype */
    @Enumerated(EnumType.STRING)
    private ArticleCategory articleCategory;

    /** Статус валідації публікації */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PublicationStatus status = PublicationStatus.NOT_VALIDATED;

    /** До якого пп належить публікація (PP_1, PP_2, PP_3, PP_4, PP_12) */
    @Enumerated(EnumType.STRING)
    private AchievementType ppType;

    /** Зв'язок з досягненням (агрегатором) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id")
    @JsonIgnore
    private Achievement achievement;

    @Column(columnDefinition = "TEXT")
    private String journalName;

    @Column(name = "publication_year")
    private Integer year;

    /**
     * Повна дата публікації (день/місяць/рік).
     * При парсингу з тільки роком автоматично виставляється на YYYY-01-01.
     * Використовується для точного 5-річного фільтра compliance і
     * фільтра за rating-period.
     */
    @Column(name = "publication_date")
    private LocalDate publicationDate;

    /**
     * Повертає ефективну дату публікації:
     * publicationDate якщо вказана, інакше year-01-01, інакше null.
     */
    public LocalDate effectiveDate() {
        if (publicationDate != null) return publicationDate;
        if (year != null) return LocalDate.of(year, 1, 1);
        return null;
    }

    private String volume;

    private String pages;

    private String doi;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(columnDefinition = "TEXT")
    private String authors;

    // ── ДСТУ 8302:2015 ──

    /** Повний бібліографічний опис за ДСТУ 8302:2015 */
    @Column(columnDefinition = "TEXT")
    private String dstuCitation;

    /** Видавництво */
    @Column(length = 500)
    private String publisher;

    /** Місто видання */
    @Column(length = 255)
    private String city;

    /** Загальна кількість сторінок */
    private Integer totalPages;

    /** ISBN */
    @Column(length = 30)
    private String isbn;

    /** ISSN */
    @Column(length = 30)
    private String issn;

    /** Інформація про конференцію: назва, дата, місце */
    @Column(columnDefinition = "TEXT")
    private String conferenceInfo;

    /** Авторських аркушів */
    private Double authorSheetCount;

    /** Чи відповідає публікація напряму діяльності кафедри (null = не перевірено) */
    private Boolean fieldRelevant;

    /** Підтип методичної праці (для рейтингування пп.4) */
    @Enumerated(EnumType.STRING)
    private MethodicalSubtype methodicalSubtype;

    /** Підтип апробаційної публікації (для рейтингування пп.12) */
    @Enumerated(EnumType.STRING)
    private ApprobationSubtype approbationSubtype;

    // ── Трекінг джерела ──

    /** Оригінальний текст з DOCX (для дедуплікації і аудиту) */
    @Column(columnDefinition = "TEXT")
    private String rawText;

    /** Звідки імпортовано: "pp.1", "pp.3", "pp.12" */
    @Column(length = 20)
    private String sourceSection;

    // ── Підтверджуючий документ ──

    /** Посилання на PDF (Google Drive) або сайт */
    @Column(columnDefinition = "TEXT")
    private String documentUrl;

    // ── Аудит ──

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
