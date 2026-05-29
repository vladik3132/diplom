package ua.edu.teacherlicence.publication.service;

import org.springframework.stereotype.Component;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Генерує бібліографічний опис публікації за ДСТУ 8302:2015.
 * Приклади: https://www.grafiati.com/uk/info/dstu-8302-2015/examples/
 *
 * Стаття:  Автори. Назва. Журнал. Рік. Т. X, № Y. С. xx—xx. DOI: xxx
 * Тези:    Автори. Назва. Конференція : тези доп. Місто, Рік. С. xx—xx.
 * Книга:   Автори. Назва : тип. Місто : Видавництво, Рік. XXX с.
 * Патент:  Назва : пат. Україна / Автори ; опубл. Рік.
 */
@Component
public class DstuCitationGenerator {

    private static final String EM_DASH = "\u2014"; // —

    public String generate(Publication p) {
        if (p.getTitle() == null || p.getTitle().isBlank()) return null;

        PublicationType type = p.getType();
        if (type == null) type = PublicationType.OTHER;

        return switch (type) {
            case ARTICLE -> generateArticle(p);
            case TEXTBOOK -> generateTextbook(p);
            case STUDY_GUIDE -> generateStudyGuide(p);
            case MONOGRAPH -> generateMonograph(p);
            case METHODICAL -> generateMethodical(p);
            case PATENT, DECLARATIVE_PATENT -> generatePatent(p);
            case COPYRIGHT -> generateCopyright(p);
            case APPROBATION -> generateApprobation(p);
            default -> generateGeneric(p);
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // СТАТТЯ У ЖУРНАЛІ / ЗБІРНИКУ
    // Кольцова Я. І., Нікітін С. В. Одержання пористих склокристалічних
    // матеріалів. Питання хімії та хімічної технології. 2020. № 1.
    // С. 33—38. DOI: 10.32434/0321-4095-2020-128-1-33-38
    // ═══════════════════════════════════════════════════════════════
    private String generateArticle(Publication p) {
        StringBuilder sb = new StringBuilder();
        appendAuthors(sb, p);
        sb.append(safe(p.getTitle())).append(".");

        // Назва журналу/збірника
        if (has(p.getJournalName())) {
            sb.append(" ").append(p.getJournalName().trim()).append(".");
        }

        // Рік
        if (p.getYear() != null) {
            sb.append(" ").append(p.getYear()).append(".");
        }

        // Том / Випуск / №
        if (has(p.getVolume())) {
            sb.append(" ").append(formatVolume(p.getVolume(), p.getJournalName())).append(".");
        }

        // Сторінки
        if (has(p.getPages())) {
            sb.append(" ").append(formatPages(p.getPages())).append(".");
        }

        appendDoi(sb, p);
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // ТЕЗИ / МАТЕРІАЛИ КОНФЕРЕНЦІЙ (АПРОБАЦІЯ)
    // Святецька А. В. Діалектизми у повісті. Стратегії розвитку :
    // матеріали Всеукр. конф., м. Запоріжжя, 2018. С. 19—23.
    //
    // Івченко В. О. Проблема правового регулювання імпічменту.
    // Актуальні проблеми : тези доп. учасників XXV конф. Харків, 2018. С. 45—47.
    // ═══════════════════════════════════════════════════════════════
    private String generateApprobation(Publication p) {
        StringBuilder sb = new StringBuilder();
        appendAuthors(sb, p);
        sb.append(safe(p.getTitle())).append(".");

        // Визначаємо назву конференції/джерела
        // Якщо conferenceInfo містить дату — обрізаємо до дати, залишаємо тільки назву
        String source = null;
        if (has(p.getConferenceInfo())) {
            source = trimConferenceDate(p.getConferenceInfo().trim());
        } else if (has(p.getJournalName())) {
            source = p.getJournalName().trim();
        }

        if (source != null) {
            sb.append(" ").append(source);
            if (!containsConferenceType(source)) {
                sb.append(" : тези доп.");
            }
            if (!sb.toString().endsWith(".")) sb.append(".");
        }

        // Місто, Видавництво, Рік
        boolean hasCity = has(p.getCity());
        boolean hasPublisher = has(p.getPublisher());
        boolean hasYear = p.getYear() != null;

        if (hasCity || hasPublisher || hasYear) {
            sb.append(" ");
            if (hasCity) {
                sb.append(p.getCity().trim());
                if (hasPublisher) sb.append(" : ");
                else if (hasYear) sb.append(", ");
            }
            if (hasPublisher) {
                sb.append(p.getPublisher().trim());
                if (hasYear) sb.append(", ");
            }
            if (hasYear) sb.append(p.getYear());
            sb.append(".");
        }

        // Сторінки
        if (has(p.getPages())) {
            sb.append(" ").append(formatPages(p.getPages())).append(".");
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // ПІДРУЧНИК
    // Автори. Назва : підручник. Місто : Видавництво, Рік. ХХХ с.
    // ═══════════════════════════════════════════════════════════════
    private String generateTextbook(Publication p) {
        StringBuilder sb = new StringBuilder();
        appendAuthors(sb, p);
        sb.append(safe(p.getTitle())).append(" : підручник.");
        appendPublisherInfo(sb, p);
        appendTotalPages(sb, p);
        appendIsbn(sb, p);
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // НАВЧАЛЬНИЙ ПОСІБНИК
    // Боярин М. В. Основи гідроекології : навч. посіб.
    // Луцьк : Вежа-Друк, 2016. 365 с.
    // ═══════════════════════════════════════════════════════════════
    private String generateStudyGuide(Publication p) {
        StringBuilder sb = new StringBuilder();
        appendAuthors(sb, p);
        sb.append(safe(p.getTitle())).append(" : навч. посіб.");
        appendPublisherInfo(sb, p);
        appendTotalPages(sb, p);
        appendIsbn(sb, p);
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // МОНОГРАФІЯ
    // Автори. Назва : монографія. Місто : Видавництво, Рік. ХХХ с.
    // ═══════════════════════════════════════════════════════════════
    private String generateMonograph(Publication p) {
        StringBuilder sb = new StringBuilder();
        appendAuthors(sb, p);
        sb.append(safe(p.getTitle())).append(" : монографія.");
        appendPublisherInfo(sb, p);
        appendTotalPages(sb, p);
        appendIsbn(sb, p);
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // НАВЧАЛЬНО-МЕТОДИЧНЕ ВИДАННЯ
    // Автори. Назва. Місто : Видавництво, Рік. ХХХ с.
    // ═══════════════════════════════════════════════════════════════
    private String generateMethodical(Publication p) {
        StringBuilder sb = new StringBuilder();
        appendAuthors(sb, p);
        sb.append(safe(p.getTitle())).append(".");
        appendPublisherInfo(sb, p);
        appendTotalPages(sb, p);
        appendIsbn(sb, p);
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // ПАТЕНТ
    // Верстат для розпилювання : пат. 123197 Україна / Автори ; опубл. 2018.
    // ═══════════════════════════════════════════════════════════════
    private String generatePatent(Publication p) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(p.getTitle())).append(" : ");
        if (p.getType() == PublicationType.DECLARATIVE_PATENT) {
            sb.append("деклараційний пат.");
        } else {
            sb.append("пат.");
        }
        sb.append(" Україна");
        if (has(p.getAuthors())) {
            sb.append(" / ").append(p.getAuthors().trim());
        }
        if (p.getYear() != null) {
            sb.append(" ; опубл. ").append(p.getYear());
        }
        sb.append(".");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // АВТОРСЬКЕ СВІДОЦТВО
    // ═══════════════════════════════════════════════════════════════
    private String generateCopyright(Publication p) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(p.getTitle()));
        sb.append(" : свідоцтво про реєстрацію авторського права");
        if (has(p.getAuthors())) {
            sb.append(" / ").append(p.getAuthors().trim());
        }
        sb.append(".");
        if (p.getYear() != null) {
            sb.append(" ").append(p.getYear()).append(".");
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // ЗАГАЛЬНИЙ ФОРМАТ (OTHER)
    // ═══════════════════════════════════════════════════════════════
    private String generateGeneric(Publication p) {
        StringBuilder sb = new StringBuilder();
        appendAuthors(sb, p);
        sb.append(safe(p.getTitle())).append(".");
        if (has(p.getJournalName())) {
            sb.append(" ").append(p.getJournalName().trim()).append(".");
        }
        if (p.getYear() != null) {
            sb.append(" ").append(p.getYear()).append(".");
        }
        if (has(p.getPages())) {
            sb.append(" ").append(formatPages(p.getPages())).append(".");
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // ДОПОМІЖНІ МЕТОДИ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Автори: "Шевченко О. І., Козак В. М. "
     */
    private void appendAuthors(StringBuilder sb, Publication p) {
        if (has(p.getAuthors())) {
            String authors = p.getAuthors().trim();
            if (authors.endsWith(".")) {
                // Не прибираємо крапку після ініціалів — це частина ПІБ
                sb.append(authors).append(" ");
            } else {
                sb.append(authors).append(". ");
            }
        }
    }

    /**
     * Видавнича інформація: " Місто : Видавництво, Рік."
     */
    private void appendPublisherInfo(StringBuilder sb, Publication p) {
        boolean hasCity = has(p.getCity());
        boolean hasPublisher = has(p.getPublisher());
        boolean hasYear = p.getYear() != null;

        if (hasCity || hasPublisher || hasYear) {
            sb.append(" ");
            if (hasCity) {
                sb.append(p.getCity().trim());
                if (hasPublisher) sb.append(" : ");
                else if (hasYear) sb.append(", ");
            }
            if (hasPublisher) {
                sb.append(p.getPublisher().trim());
                if (hasYear) sb.append(", ");
            }
            if (hasYear) sb.append(p.getYear());
            sb.append(".");
        }
    }

    /**
     * Форматує том/випуск за ДСТУ:
     * - "42"       -> "№ 42" (для журналів), "Вип. 42" (для збірників)
     * - "21(1)"    -> "Т. 21, № 1"
     * - "Vol. 2"   -> "Vol. 2" (залишити англійський)
     * - "Вип. 25"  -> "Вип. 25" (вже відформатовано)
     */
    private String formatVolume(String volume, String journalName) {
        String v = volume.trim();

        // Вже відформатовано — залишити як є
        if (v.startsWith("Т.") || v.startsWith("Вип.") || v.startsWith("№")
                || v.startsWith("Vol.") || v.startsWith("No.") || v.startsWith("T.")) {
            return v;
        }

        // "21(1)" або "21 (1)" -> "Т. 21, № 1"
        Matcher m1 = Pattern.compile("^(\\d+)\\s*\\(\\s*(\\d+)\\s*\\)$").matcher(v);
        if (m1.matches()) {
            return "Т. " + m1.group(1) + ", № " + m1.group(2);
        }

        // "6(4 (126))" -> "Т. 6, № 4 (126)"
        Matcher m2 = Pattern.compile("^(\\d+)\\((.+)\\)$").matcher(v);
        if (m2.matches()) {
            return "Т. " + m2.group(1) + ", № " + m2.group(2).trim();
        }

        // Просте число
        if (v.matches("\\d+")) {
            // "Збірник" -> "Вип.", журнал -> "№"
            boolean isCollection = journalName != null &&
                    journalName.toLowerCase().matches(".*(збірник|зб\\.|вісник|праці|записки|proceedings|colins|ittap|icsit|ceur).*");
            return isCollection ? "Вип. " + v : "№ " + v;
        }

        return v;
    }

    /**
     * Сторінки за ДСТУ: "С. 45—56" (з тире, не дефіс).
     * Англомовні: "P. 89—91".
     */
    private String formatPages(String pages) {
        if (pages == null) return "";
        String p = pages.trim().replaceAll("(\\d)\\s*-\\s*(\\d)", "$1" + EM_DASH + "$2");
        // Додати "С." якщо ще не має
        if (p.startsWith("С.") || p.startsWith("P.") || p.startsWith("с.") || p.startsWith("p.")) {
            return p;
        }
        return "С. " + p;
    }

    private void appendTotalPages(StringBuilder sb, Publication p) {
        if (p.getTotalPages() != null) {
            sb.append(" ").append(p.getTotalPages()).append(" с.");
        }
    }

    private void appendDoi(StringBuilder sb, Publication p) {
        if (has(p.getDoi())) {
            String doi = p.getDoi().trim();
            if (doi.startsWith("http")) {
                Matcher m = Pattern.compile("10\\.\\d{4,}/[^\\s]+").matcher(doi);
                if (m.find()) {
                    sb.append(" DOI: ").append(m.group());
                } else {
                    sb.append(" ").append(doi);
                }
            } else if (doi.toUpperCase().startsWith("DOI:")) {
                sb.append(" ").append(doi);
            } else {
                sb.append(" DOI: ").append(doi);
            }
        }
    }

    private void appendIsbn(StringBuilder sb, Publication p) {
        if (has(p.getIsbn())) {
            sb.append(" ISBN ").append(p.getIsbn().trim()).append(".");
        }
    }

    /**
     * Обрізає conferenceInfo від дати/місця проведення, залишаючи лише назву конференції.
     * Наприклад:
     * "III Міжнародна конференція \"Тема\", 30 листопада 2023 року, Київ, Україна"
     *   → "III Міжнародна конференція \"Тема\""
     * "Конференція, м. Харків, 12-13 травня 2024 р."
     *   → "Конференція"
     */
    private String trimConferenceDate(String conferenceInfo) {
        if (conferenceInfo == null) return null;
        String s = conferenceInfo.trim();

        // Патерн 1: ", DD month YYYY" або ", DD—DD month YYYY" або ", DD-DD month YYYY"
        // Місяці: січня, лютого, березня, квітня, травня, червня, липня, серпня, вересня, жовтня, листопада, грудня
        // Також: January, February, etc.
        Matcher m = Pattern.compile(
                ",\\s*\\d{1,2}\\s*[—–-]?\\s*\\d{0,2}\\s*" +
                "(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня" +
                "|january|february|march|april|may|june|july|august|september|october|november|december)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(s);
        if (m.find()) {
            return s.substring(0, m.start()).trim();
        }

        // Патерн 2: ", м. Місто" або ", м.Місто" (місто перед датою)
        Matcher m2 = Pattern.compile(",\\s*м\\.\\s*[А-ЯІЇЄҐA-Z]").matcher(s);
        if (m2.find()) {
            return s.substring(0, m2.start()).trim();
        }

        // Патерн 3: ", DD.MM.YYYY" (числова дата)
        Matcher m3 = Pattern.compile(",\\s*\\d{1,2}\\.\\d{1,2}\\.\\d{4}").matcher(s);
        if (m3.find()) {
            return s.substring(0, m3.start()).trim();
        }

        // Патерн 4: "(DD month YYYY)" або "(м. Місто, YYYY)"
        Matcher m4 = Pattern.compile("\\(\\s*\\d{1,2}\\s*(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(s);
        if (m4.find()) {
            return s.substring(0, m4.start()).trim();
        }

        Matcher m5 = Pattern.compile("\\(\\s*м\\.\\s*[А-ЯІЇЄҐA-Z]").matcher(s);
        if (m5.find()) {
            return s.substring(0, m5.start()).trim();
        }

        return s;
    }

    /**
     * Перевіряє чи назва джерела вже містить вказівку на тип конференції.
     */
    private boolean containsConferenceType(String source) {
        String lower = source.toLowerCase();
        return lower.contains("тези") || lower.contains("тез ")
                || lower.contains("матеріали") || lower.contains("доповід")
                || lower.contains("proceedings") || lower.contains("abstracts")
                || lower.contains("збірник");
    }

    /**
     * Перевіряє чи текст містить рік (20XX) — ознака що conferenceInfo
     * вже включає дату та місце проведення.
     */
    private boolean containsYearPattern(String text) {
        return text != null && Pattern.compile("20[12]\\d").matcher(text).find();
    }

    private boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String s) {
        return s != null ? s.trim() : "";
    }
}
