package ua.edu.teacherlicence.fakhove.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.fakhove.dto.VerificationResult;
import ua.edu.teacherlicence.fakhove.model.FakhovyiJournal;
import ua.edu.teacherlicence.fakhove.model.JournalCategory;
import ua.edu.teacherlicence.fakhove.model.ScopusJournal;
import ua.edu.teacherlicence.fakhove.repository.FakhovyiJournalRepository;
import ua.edu.teacherlicence.fakhove.repository.ScopusJournalRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FakhovyiJournalService {

    private final FakhovyiJournalRepository fakhovyiJournalRepository;
    private final ScopusJournalRepository scopusJournalRepository;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),  // ISO — першим, щоб не плутати з dd.MM
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d.MM.yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy")
    };

    // ── Import Fakhovi ──

    /**
     * Імпорт фахових видань з Excel-файлу (реєстр МОН).
     * Очікувані колонки: НазваВидання, ЗасновникСпівзасновники, КодСпеціальності, ДатаВключення, Категорія.
     * Видаляє всі існуючі записи перед імпортом.
     *
     * @param is InputStream Excel-файлу (.xlsx)
     * @return кількість імпортованих записів
     */
    @Transactional
    public int importFakhoviFromExcel(InputStream is) throws IOException {
        log.info("Starting fakhovi journals import from Excel...");

        try (Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Detect column indices from header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file has no header row");
            }

            Map<String, Integer> columnMap = detectFakhoviColumns(headerRow);
            log.info("Detected fakhovi columns: {}", columnMap);

            int nameCol = columnMap.getOrDefault("name", -1);
            int foundersCol = columnMap.getOrDefault("founders", -1);
            int specialtyCol = columnMap.getOrDefault("specialty", -1);
            int dateCol = columnMap.getOrDefault("date", -1);
            int categoryCol = columnMap.getOrDefault("category", -1);

            if (nameCol == -1) {
                throw new IllegalArgumentException("Could not find 'НазваВидання' or similar column in Excel");
            }

            // Delete existing records
            fakhovyiJournalRepository.deleteAllInBatch();
            log.info("Deleted all existing fakhovi journal records");

            List<FakhovyiJournal> journals = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getCellString(row, nameCol);
                if (name == null || name.isBlank()) continue;

                FakhovyiJournal journal = FakhovyiJournal.builder()
                        .name(name.trim())
                        .founders(foundersCol >= 0 ? getCellString(row, foundersCol) : null)
                        .specialtyCodes(specialtyCol >= 0 ? getCellString(row, specialtyCol) : null)
                        .inclusionDate(dateCol >= 0 ? parseDateCell(row, dateCol) : null)
                        .category(categoryCol >= 0 ? parseCategoryCell(row, categoryCol) : null)
                        .nameNormalized(normalizeName(name))
                        .build();

                journals.add(journal);
            }

            List<FakhovyiJournal> saved = fakhovyiJournalRepository.saveAll(journals);
            log.info("Imported {} fakhovi journals", saved.size());
            return saved.size();
        }
    }

    // ── Import Scopus ──

    /**
     * Імпорт журналів Scopus з Excel-файлу (Scopus Source List).
     * Шукає колонки: Source Title (або Title), Print ISSN (або ISSN), E-ISSN.
     * Видаляє всі існуючі записи перед імпортом.
     *
     * @param is InputStream Excel-файлу (.xlsx)
     * @return кількість імпортованих записів
     */
    @Transactional
    public int importScopusFromExcel(InputStream is) throws IOException {
        log.info("Starting Scopus journals import from Excel...");

        try (Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file has no header row");
            }

            Map<String, Integer> columnMap = detectScopusColumns(headerRow);
            log.info("Detected Scopus columns: {}", columnMap);

            int nameCol = columnMap.getOrDefault("name", -1);
            int issnCol = columnMap.getOrDefault("issn", -1);
            int eissnCol = columnMap.getOrDefault("eissn", -1);

            if (nameCol == -1) {
                throw new IllegalArgumentException("Could not find 'Source Title' or 'Title' column in Excel");
            }

            // Delete existing records
            scopusJournalRepository.deleteAllInBatch();
            log.info("Deleted all existing Scopus journal records");

            List<ScopusJournal> journals = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getCellString(row, nameCol);
                if (name == null || name.isBlank()) continue;

                ScopusJournal journal = ScopusJournal.builder()
                        .name(name.trim())
                        .issn(issnCol >= 0 ? cleanIssn(getCellString(row, issnCol)) : null)
                        .eissn(eissnCol >= 0 ? cleanIssn(getCellString(row, eissnCol)) : null)
                        .nameNormalized(normalizeName(name))
                        .build();

                journals.add(journal);
            }

            List<ScopusJournal> saved = scopusJournalRepository.saveAll(journals);
            log.info("Imported {} Scopus journals", saved.size());
            return saved.size();
        }
    }

    // ── Verification ──

    /**
     * Перевірити журнал у реєстрі фахових видань та Scopus.
     * Спочатку шукає за ISSN (точний збіг), потім за назвою (нечіткий пошук).
     *
     * @param name назва журналу
     * @param issn ISSN (може бути null)
     * @return результат перевірки
     */
    public VerificationResult verifyJournal(String name, String issn) {
        log.debug("Verifying journal: name='{}', issn='{}'", name, issn);

        boolean isFakhove = false;
        JournalCategory category = null;
        String matchedFakhoveName = null;

        boolean isScopus = false;
        String matchedScopusName = null;

        // 1. Check Fakhovi by name (bidirectional normalized search)
        if (name != null && !name.isBlank()) {
            String normalized = normalizeName(name);
            // Forward: DB name contains publication name
            List<FakhovyiJournal> fakhoviMatches = fakhovyiJournalRepository
                    .findByNameNormalizedContaining(normalized);
            // Reverse: publication name contains DB name
            if (fakhoviMatches.isEmpty()) {
                fakhoviMatches = fakhovyiJournalRepository
                        .findWhereQueryContainsName(normalized);
            }

            if (!fakhoviMatches.isEmpty()) {
                // Pick best match (longest name = most specific)
                FakhovyiJournal match = fakhoviMatches.stream()
                        .max((a, b) -> Integer.compare(
                                a.getNameNormalized().length(),
                                b.getNameNormalized().length()))
                        .orElse(fakhoviMatches.get(0));
                isFakhove = true;
                category = match.getCategory();
                matchedFakhoveName = match.getName();
            }
        }

        // 2. Check Scopus by ISSN first, then by name (bidirectional)
        if (issn != null && !issn.isBlank()) {
            String cleanedIssn = cleanIssn(issn);
            List<ScopusJournal> scopusByIssn = scopusJournalRepository.findByAnyIssn(cleanedIssn);
            if (!scopusByIssn.isEmpty()) {
                isScopus = true;
                matchedScopusName = scopusByIssn.get(0).getName();
            }
        }

        if (!isScopus && name != null && !name.isBlank()) {
            String normalized = normalizeName(name);
            // Forward: DB name contains publication name
            List<ScopusJournal> scopusByName = scopusJournalRepository
                    .findByNameNormalizedContaining(normalized);
            // Reverse: publication name contains DB name
            if (scopusByName.isEmpty()) {
                scopusByName = scopusJournalRepository
                        .findWhereQueryContainsName(normalized);
            }
            if (!scopusByName.isEmpty()) {
                isScopus = true;
                matchedScopusName = scopusByName.stream()
                        .max((a, b) -> Integer.compare(
                                a.getNameNormalized().length(),
                                b.getNameNormalized().length()))
                        .map(ScopusJournal::getName)
                        .orElse(scopusByName.get(0).getName());
            }
        }

        VerificationResult result = new VerificationResult(
                isFakhove, category, isScopus, matchedFakhoveName, matchedScopusName
        );
        log.debug("Verification result: {}", result);
        return result;
    }

    // ── Search / List ──

    public List<FakhovyiJournal> searchByName(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return fakhovyiJournalRepository.searchByName(query.trim());
    }

    public List<FakhovyiJournal> findAll() {
        return fakhovyiJournalRepository.findAll();
    }

    public long countFakhovi() {
        return fakhovyiJournalRepository.count();
    }

    public long countScopus() {
        return scopusJournalRepository.count();
    }

    public List<ScopusJournal> findAllScopus() {
        return scopusJournalRepository.findAll();
    }

    // ── Private helpers ──

    /**
     * Нормалізація назви: lowercase, видалення лапок, типових префіксів/суфіксів, trim.
     */
    static String normalizeName(String name) {
        if (name == null) return "";
        String n = name.toLowerCase()
                .replaceAll("[«»\"\u201C\u201D\u201E\u201F''`]", "")
                .trim();
        // Видаляємо типові префікси видань
        n = n.replaceAll("^(електронн(ий|е|а)\\s+(наукове?\\s+)?)?((міжнародн(ий|е|а)\\s+)?(науков(ий|е|а)\\s+)?)?(фахове?\\s+)?(журнал|видання|збірник(\\s+наукових\\s+праць)?)\\s+", "");
        // Видаляємо суфікс ". Серія ..." або "(серія ...)"
        n = n.replaceAll("\\s*[.(]\\s*серія\\s+.*$", "");
        return n.trim();
    }

    /**
     * Очистка ISSN: залишає тільки цифри та дефіс.
     */
    private String cleanIssn(String issn) {
        if (issn == null || issn.isBlank()) return null;
        return issn.trim().replaceAll("[^0-9Xx-]", "");
    }

    /**
     * Detect column indices for fakhovi journal Excel by header names.
     */
    private Map<String, Integer> detectFakhoviColumns(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String header = cell.getStringCellValue().trim().toLowerCase();

            if (header.contains("назва") || header.contains("видання") || header.contains("name")) {
                map.putIfAbsent("name", i);
            } else if (header.contains("засновник") || header.contains("співзасновник") || header.contains("founder")) {
                map.putIfAbsent("founders", i);
            } else if (header.contains("спеціальн") || header.contains("код") || header.contains("specialty")) {
                map.putIfAbsent("specialty", i);
            } else if (header.contains("дата") || header.contains("включення") || header.contains("date")) {
                map.putIfAbsent("date", i);
            } else if (header.contains("категорі") || header.contains("category")) {
                map.putIfAbsent("category", i);
            }
        }
        return map;
    }

    /**
     * Detect column indices for Scopus Source List Excel by header names.
     */
    private Map<String, Integer> detectScopusColumns(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String header = cell.getStringCellValue().trim().toLowerCase();

            if (header.contains("source title") || header.equals("title") || header.contains("назва")) {
                map.putIfAbsent("name", i);
            } else if (header.contains("e-issn") || header.contains("eissn")) {
                map.putIfAbsent("eissn", i);
            } else if (header.contains("issn") || header.contains("print issn")) {
                // Check eissn first (above), then issn
                map.putIfAbsent("issn", i);
            }
        }
        return map;
    }

    /**
     * Get cell value as String, handling numeric and other cell types.
     */
    private String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                // Avoid scientific notation for large numbers
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    /**
     * Parse date from cell: handles both Excel date format and text date formats.
     */
    private LocalDate parseDateCell(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String dateStr = getCellString(row, colIndex);
        if (dateStr == null || dateStr.isBlank()) return null;

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr.trim(), fmt);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        log.warn("Could not parse date: '{}'", dateStr);
        return null;
    }

    /**
     * Parse category from cell value (А/A/Б/B -> CATEGORY_A / CATEGORY_B).
     */
    private JournalCategory parseCategoryCell(Row row, int colIndex) {
        String value = getCellString(row, colIndex);
        if (value == null || value.isBlank()) return null;

        String upper = value.trim().toUpperCase();
        // Handle both Ukrainian (А, Б) and Latin (A, B) letters
        if (upper.contains("\u0410") || upper.contains("A") || upper.equals("CATEGORY_A")) {
            // Ukrainian А (U+0410) or Latin A
            // Need to distinguish: if it contains Б (Ukrainian) -> B, else check for A
            if (upper.contains("\u0411") || upper.contains("B")) {
                return JournalCategory.CATEGORY_B;
            }
            return JournalCategory.CATEGORY_A;
        }
        if (upper.contains("\u0411") || upper.contains("B") || upper.equals("CATEGORY_B")) {
            return JournalCategory.CATEGORY_B;
        }

        log.warn("Unknown journal category: '{}'", value);
        return null;
    }
}
