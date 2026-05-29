package ua.edu.teacherlicence.docx.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.teacherlicence.docx.dto.ColumnHeaderDto;
import ua.edu.teacherlicence.docx.dto.TemplateUploadResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class TemplateParserService {

    @Value("${app.docx.templates-dir:./templates}")
    private String templatesDir;

    private Path resolvedDir;

    @PostConstruct
    public void init() throws IOException {
        resolvedDir = Paths.get(templatesDir).toAbsolutePath().normalize();
        if (!Files.exists(resolvedDir)) {
            Files.createDirectories(resolvedDir);
            log.info("Created templates directory: {}", resolvedDir);
        } else {
            log.info("Using templates directory: {}", resolvedDir);
        }
    }

    /**
     * Зберегти завантажений файл на диску та розпарсити заголовки таблиці.
     *
     * <p>На Linux-контейнерах filesystem encoding може не співпадати з JVM file.encoding
     * (наприклад LANG=POSIX замість UTF-8) → нелатинські символи в іменах файлів
     * корупляться між запис/читання. Тому stored filename — повний UUID без оригінальної назви;
     * оригінал повертаємо окремим полем для UI.
     */
    public TemplateUploadResponse uploadAndParse(MultipartFile file, int tableIndex, int headerRowCount)
            throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("Файл має бути у форматі .docx");
        }

        // ASCII-only stored filename (не залежить від filesystem encoding).
        String storedName = UUID.randomUUID().toString() + ".docx";
        Path filePath = resolvedDir.resolve(storedName);
        Files.copy(file.getInputStream(), filePath);
        log.info("Template saved: {} (original: {})", filePath, originalName);

        // Парсити заголовки
        List<ColumnHeaderDto> columns = parseHeaders(storedName, tableIndex, headerRowCount);

        return new TemplateUploadResponse(storedName, originalName, columns);
    }

    /**
     * Розпарсити заголовки таблиці з уже збереженого шаблону.
     */
    public List<ColumnHeaderDto> parseHeaders(String templateFileName, int tableIndex, int headerRowCount)
            throws IOException {
        Path filePath = resolvedDir.resolve(templateFileName);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Файл шаблону не знайдено: " + templateFileName);
        }

        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(filePath.toFile()))) {
            List<XWPFTable> tables = doc.getTables();
            if (tables.isEmpty()) {
                throw new IllegalStateException("Шаблон не містить таблиць");
            }
            if (tableIndex >= tables.size()) {
                throw new IllegalArgumentException("Таблиця з індексом " + tableIndex
                        + " не знайдена (документ має " + tables.size() + " таблиць)");
            }

            XWPFTable table = tables.get(tableIndex);
            List<ColumnHeaderDto> headers = new ArrayList<>();

            // Визначити кількість колонок з першого рядка
            XWPFTableRow firstRow = table.getRow(0);
            if (firstRow == null) {
                throw new IllegalStateException("Таблиця порожня");
            }

            int colCount = firstRow.getTableCells().size();

            for (int col = 0; col < colCount; col++) {
                StringBuilder headerText = new StringBuilder();
                // Зібрати текст з усіх header-рядків для цієї колонки
                for (int row = 0; row < headerRowCount && row < table.getNumberOfRows(); row++) {
                    XWPFTableRow tableRow = table.getRow(row);
                    if (tableRow != null && col < tableRow.getTableCells().size()) {
                        XWPFTableCell cell = tableRow.getCell(col);
                        String cellText = cell.getText().trim();
                        if (!cellText.isEmpty()) {
                            if (!headerText.isEmpty()) headerText.append(" ");
                            headerText.append(cellText);
                        }
                    }
                }
                headers.add(new ColumnHeaderDto(col, headerText.toString()));
            }

            log.info("Parsed {} columns from template {}", headers.size(), templateFileName);
            return headers;
        }
    }

    /**
     * Отримати повний шлях до файлу шаблону.
     */
    public Path getTemplatePath(String templateFileName) {
        return resolvedDir.resolve(templateFileName);
    }
}
