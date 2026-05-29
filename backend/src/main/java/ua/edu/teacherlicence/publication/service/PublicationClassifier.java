package ua.edu.teacherlicence.publication.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Єдиний канонічний класифікатор публікацій під підпункти 38 (Постанова КМУ №1187).
 *
 * <p>Це <b>єдине джерело правди</b>, яке використовується:
 * <ul>
 *   <li>{@link PublicationService} — для встановлення ppType при збереженні</li>
 *   <li>{@code AchievementComposer} — для формування описів досягнень</li>
 *   <li>{@code AchievementValidationService} — для підрахунку прогресу пп.</li>
 *   <li>{@code RatingCalculationService} — для нарахування балів рейтингу</li>
 * </ul>
 *
 * <p>Правила (Постанова КМУ №1187, п.38):
 * <pre>
 * пп.1  — ≥5 публікацій у фахових виданнях / Scopus / WoS Core Collection
 *         (ARTICLE з categoryArticle ∈ {SCOPUS, WOS, CATEGORY_A, CATEGORY_B})
 * пп.2  — 1 patent на винахід АБО ≥5 деклараційних/корисних/свідоцтв
 *         (PATENT / DECLARATIVE_PATENT / COPYRIGHT)
 * пп.3  — підручник / посібник / монографія (≥5 авт.арк.; у співавт. ≥1.5)
 *         (TEXTBOOK / STUDY_GUIDE / MONOGRAPH — окрім методичних робіт за назвою)
 * пп.4  — ≥3 навчально-методичних праць (METHODICAL — посібники, конспекти,
 *         РПНД, ел.курси, практикуми, методичні вказівки/рекомендації)
 *         + TEXTBOOK/STUDY_GUIDE з методичними keywords (вказівки, РПНД тощо)
 * пп.12 — ≥5 апробаційних / науково-популярних / консультаційних / науково-експертних
 *         (APPROBATION + POPULAR_SCIENTIFIC).
 * </pre>
 *
 * <p>Спільні правила:
 * <ul>
 *   <li>Старіші за {@link #COMPLIANCE_PERIOD_YEARS} років не зараховуються</li>
 *   <li>Дублі за нормалізованою назвою об'єднуються (від самоплагіату).
 *       DOI НЕ використовується для дедуплікації — деякі збірники мають один
 *       спільний DOI на всі тези. Volume-dedup (журнал+випуск) ВІДКЛЮЧЕНО —
 *       публікації в одному збірнику рахуються окремо.</li>
 * </ul>
 */
@Slf4j
@Service
public class PublicationClassifier {

    /** Період відповідності: досягнення мають бути не старші за N років. */
    public static final int COMPLIANCE_PERIOD_YEARS = 5;

    // ═══════════════════════════════════════════════════════════════
    //  ppType inference
    // ═══════════════════════════════════════════════════════════════

    /**
     * Повертає номер пп. (1, 2, 3, 4, 12) куди КАНОНІЧНО належить ця публікація
     * за п.38 Постанови КМУ №1187.
     *
     * <p>Особливі випадки:
     * <ul>
     *   <li>POPULAR_SCIENTIFIC → пп.12 (за п.38(12) — "апробаційні та/або науково-популярні")</li>
     *   <li>TEXTBOOK / STUDY_GUIDE з методичними keywords у назві → пп.4 (а не пп.3)</li>
     * </ul>
     *
     * Повертає 0 якщо тип не має канонічного pp-маркера.
     */
    public int inferPpNumber(Publication p) {
        if (p == null || p.getType() == null) return 0;
        return switch (p.getType()) {
            case ARTICLE -> 1;
            case POPULAR_SCIENTIFIC -> 12;
            case PATENT, DECLARATIVE_PATENT, COPYRIGHT -> 2;
            case TEXTBOOK, STUDY_GUIDE -> isMethodicalWork(p.getTitle()) ? 4 : 3;
            case MONOGRAPH -> 3;
            case METHODICAL -> 4;
            case APPROBATION -> 12;
            case OTHER -> 0;
        };
    }

    /** Повертає AchievementType-enum (PP_1_PUBLICATIONS, PP_2_PATENTS, ...) для публікації, або null. */
    public AchievementType inferPpType(Publication p) {
        int pp = inferPpNumber(p);
        return pp > 0 ? AchievementType.fromNumber(pp) : null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Per-pp predicates (qualifies for pp.N)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Перевіряє чи публікація зараховується для пп.N.
     * Включає freshness check (5 років).
     */
    public boolean qualifiesFor(Publication p, int ppNum) {
        return qualifiesFor(p, ppNum, getCutoffDate());
    }

    public boolean qualifiesFor(Publication p, int ppNum, LocalDate cutoff) {
        if (p == null || p.getType() == null) return false;
        if (!isFresh(p, cutoff)) return false;
        return switch (ppNum) {
            case 1 -> p.getType() == PublicationType.ARTICLE
                    && p.getArticleCategory() != null
                    && isQualifiedCategory(p.getArticleCategory());
            case 2 -> p.getType() == PublicationType.PATENT
                    || p.getType() == PublicationType.DECLARATIVE_PATENT
                    || p.getType() == PublicationType.COPYRIGHT;
            case 3 -> isPp3Textbook(p);
            case 4 -> isPp4Methodical(p);
            case 12 -> p.getType() == PublicationType.APPROBATION
                    || p.getType() == PublicationType.POPULAR_SCIENTIFIC;
            default -> false;
        };
    }

    /**
     * pp.3: підручник / посібник / монографія, окрім тих що мають методичні keywords.
     */
    private boolean isPp3Textbook(Publication p) {
        if (p.getType() != PublicationType.TEXTBOOK
                && p.getType() != PublicationType.STUDY_GUIDE
                && p.getType() != PublicationType.MONOGRAPH) {
            return false;
        }
        // MONOGRAPH завжди йде в пп.3 (методичних монографій не буває).
        if (p.getType() == PublicationType.MONOGRAPH) return true;
        // TEXTBOOK/STUDY_GUIDE з методичною назвою → пп.4, а не пп.3.
        return !isMethodicalWork(p.getTitle());
    }

    /**
     * пп.4: METHODICAL завжди + TEXTBOOK/STUDY_GUIDE з методичними keywords у назві.
     */
    private boolean isPp4Methodical(Publication p) {
        if (p.getType() == PublicationType.METHODICAL) return true;
        if ((p.getType() == PublicationType.TEXTBOOK
                || p.getType() == PublicationType.STUDY_GUIDE)
                && isMethodicalWork(p.getTitle())) return true;
        return false;
    }

    /** Лише ці категорії статей зараховуються для пп.1. */
    private boolean isQualifiedCategory(ArticleCategory c) {
        return c == ArticleCategory.SCOPUS
                || c == ArticleCategory.WOS
                || c == ArticleCategory.CATEGORY_A
                || c == ArticleCategory.CATEGORY_B;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Filtering & grouping
    // ═══════════════════════════════════════════════════════════════

    /**
     * Список публікацій, що зараховуються для пп.N.
     * Послідовність: filter(qualifies) → dedupByContent (за нормалізованою назвою) → результат.
     * Volume-dedup (групування за журналом+випуском) ВИДАЛЕНО — кожна публікація
     * рахується окремо незалежно від того, чи є вона в одному збірнику з іншими.
     */
    public List<Publication> filterForPp(List<Publication> pubs, int ppNum) {
        return filterForPp(pubs, ppNum, getCutoffDate());
    }

    public List<Publication> filterForPp(List<Publication> pubs, int ppNum, LocalDate cutoff) {
        if (pubs == null) return List.of();
        List<Publication> qualified = pubs.stream()
                .filter(p -> qualifiesFor(p, ppNum, cutoff))
                .collect(java.util.stream.Collectors.toList());
        return deduplicateByContent(qualified);
    }

    /**
     * Прибирає content-дублі (та сама стаття додана двічі — наприклад спершу як Cat A,
     * потім як Scopus після індексування). Зберігає по одній публікації на унікальний
     * "контент-ключ", обираючи переможця за пріоритетами:
     *
     * <ol>
     *   <li><b>Категорія статті</b>: SCOPUS &gt; WOS &gt; CATEGORY_A &gt; CATEGORY_B &gt; none</li>
     *   <li><b>Дата публікації</b> (effectiveDate): пізніша перемагає при рівних категоріях</li>
     *   <li><b>id</b>: вищий id (новіший запис) — стабільний tiebreaker</li>
     * </ol>
     *
     * <p>Контент-ключ — ТІЛЬКИ нормалізована назва (літери+цифри тільки).
     * DOI НЕ використовується для дедуплікації, бо у деяких збірниках DOI
     * проставляється на ВЕСЬ збірник (а не на окрему тезу) — це призводило до
     * помилкового об'єднання різних тез одного автора з тим самим DOI.
     */
    public List<Publication> deduplicateByContent(List<Publication> pubs) {
        if (pubs == null || pubs.isEmpty()) return List.of();
        Map<String, Publication> bestByKey = new LinkedHashMap<>();
        for (Publication p : pubs) {
            String key = contentKey(p);
            if (key == null) {
                // Без identifier — вважаємо унікальною (ключ за id).
                bestByKey.put("__uid_" + (p.getId() != null ? p.getId() : System.nanoTime()), p);
                continue;
            }
            Publication current = bestByKey.get(key);
            if (current == null || preferOver(p, current)) {
                bestByKey.put(key, p);
            }
        }
        return new ArrayList<>(bestByKey.values());
    }

    /**
     * Контент-ключ для виявлення self-plagiarism — ТІЛЬКИ нормалізована назва.
     * DOI свідомо не використовується (див. JavaDoc deduplicateByContent).
     */
    private String contentKey(Publication p) {
        if (p == null) return null;
        if (p.getTitle() != null && !p.getTitle().isBlank()) {
            String norm = p.getTitle().trim().toLowerCase()
                    .replaceAll("[\\p{Punct}\\s]+", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (norm.length() >= 10) {  // мінімум 10 chars щоб не зливати короткі назви
                return "title:" + norm;
            }
        }
        return null;
    }

    /**
     * Чи кандидат "кращий" за поточного переможця (для дедупліки content-дублів).
     */
    private boolean preferOver(Publication candidate, Publication current) {
        int candPrio = categoryPriority(candidate);
        int currPrio = categoryPriority(current);
        if (candPrio != currPrio) return candPrio < currPrio;  // менше число = вища категорія
        java.time.LocalDate candDate = candidate.effectiveDate();
        java.time.LocalDate currDate = current.effectiveDate();
        if (candDate != null && currDate != null && !candDate.isEqual(currDate)) {
            return candDate.isAfter(currDate);
        }
        // Tie-breaker: вищий id (новіше створений запис)
        Long candId = candidate.getId();
        Long currId = current.getId();
        if (candId != null && currId != null) return candId > currId;
        return false;
    }

    /** Числовий пріоритет категорії статті (менше = краще). */
    private int categoryPriority(Publication p) {
        if (p == null || p.getArticleCategory() == null) return 999;
        return switch (p.getArticleCategory()) {
            case SCOPUS -> 0;
            case WOS -> 1;
            case CATEGORY_A -> 2;
            case CATEGORY_B -> 3;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  Volume-dedup ВИДАЛЕНО (раніше декілька тез у тому самому збірнику
    //  рахувались як 1 апробація). Тепер усі публікації рахуються окремо;
    //  єдина дедуплікація — за нормалізованою назвою (deduplicateByContent).
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    //  Freshness
    // ═══════════════════════════════════════════════════════════════

    /** Cutoff date: now - 5 років. Публікації старіші — НЕ зараховуються. */
    public LocalDate getCutoffDate() {
        return LocalDate.now().minusYears(COMPLIANCE_PERIOD_YEARS);
    }

    public boolean isFresh(Publication p) {
        return isFresh(p, getCutoffDate());
    }

    /**
     * Чи публікація достатньо свіжа.
     *
     * <p>Логіка:
     * <ul>
     *   <li>Якщо є effectiveDate → перевіряємо ≥ cutoff</li>
     *   <li>Якщо немає ні дати ні року → зараховуємо ("на користь викладача")</li>
     * </ul>
     */
    public boolean isFresh(Publication p, LocalDate cutoff) {
        if (p == null) return false;
        LocalDate effective = p.effectiveDate();
        if (effective == null) return true;  // на користь викладача
        return !effective.isBefore(cutoff);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Title-based keywords (METHODICAL detection)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Чи назва публікації виглядає як методична робота (а не підручник).
     * Використовується щоб TEXTBOOK/STUDY_GUIDE з методичною назвою
     * автоматично йшли у пп.4 замість пп.3.
     */
    public boolean isMethodicalWork(String title) {
        if (title == null || title.isBlank()) return false;
        String lower = title.toLowerCase();
        // Сильні методичні маркери
        if (lower.contains("методичн") || lower.contains("метод.")) return true;
        if (lower.contains("рпнд") || lower.contains("робоча програма") || lower.contains("силабус")) return true;
        if (lower.contains("конспект") && lower.contains("лекц")) return true;
        if (lower.contains("практикум")) return true;
        if (lower.contains("е-курс") || lower.contains("екурс")
                || lower.contains("електронний курс") || lower.contains("дистанційн")) return true;
        if (lower.contains("самостійн") || lower.contains("для самост")) return true;
        return false;
    }

    /**
     * Чи назва є справжнім підручником/посібником/монографією (НЕ методичним).
     * Використовується щоб не плутати з пп.4.
     */
    public boolean isRealTextbook(String title) {
        if (title == null || title.isBlank()) return false;
        String lower = title.toLowerCase();
        boolean hasTextbookWord = lower.contains("підручник")
                || lower.contains("посібник")
                || lower.contains("монограф");
        return hasTextbookWord && !isMethodicalWork(title);
    }
}
