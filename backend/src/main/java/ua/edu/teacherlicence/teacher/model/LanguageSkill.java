package ua.edu.teacherlicence.teacher.model;

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

import java.time.LocalDate;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "language_skills")
public class LanguageSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    private String language;

    private String level;

    private String certificateDetails;

    @Column(length = 200)
    private String certificateNumber;

    private LocalDate certificateDate;

    @Column(length = 300)
    private String certificateOrganization;

    @Column(length = 500)
    private String certificateUrl;

    // ── СМР (Стандартизований мовний рівень) ──

    /** СМР компонент 1 */
    private Integer smr1;

    /** СМР компонент 2 */
    private Integer smr2;

    /** СМР компонент 3 */
    private Integer smr3;

    /** СМР компонент 4 */
    private Integer smr4;

    /**
     * Обчислений рівень СМР (0..4 у шкалі STANAG 6001).
     * <ol>
     *   <li>Якщо всі 4 компоненти smr1..smr4 заповнені → min(smr1..smr4).</li>
     *   <li>Інакше — fallback: парсимо текстове поле {@link #level}
     *       (підтримує "СМР 2", "STANAG 2222", CEFR "B2" тощо).</li>
     * </ol>
     * Повертає null якщо нічого не розпізналося.
     */
    public Integer getSmrLevel() {
        if (smr1 != null && smr2 != null && smr3 != null && smr4 != null) {
            return Math.min(Math.min(smr1, smr2), Math.min(smr3, smr4));
        }
        return ua.edu.teacherlicence.teacher.util.LanguageLevelParser.parseSmr(level);
    }
}
