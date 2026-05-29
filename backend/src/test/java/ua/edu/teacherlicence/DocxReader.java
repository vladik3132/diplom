package ua.edu.teacherlicence;

import org.apache.poi.xwpf.usermodel.*;
import java.io.*;
import java.util.List;

public class DocxReader {
    public static void main(String[] args) throws Exception {
        String path = "C:/Users/sachu/Downloads/таблиця кадрове забезпечення кафедра 22.docx";
        PrintStream out = new PrintStream(System.out, true, "UTF-8");

        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path))) {
            List<XWPFTable> tables = doc.getTables();
            out.println("=== TOTAL TABLES: " + tables.size() + " ===\n");

            for (int t = 0; t < tables.size(); t++) {
                XWPFTable table = tables.get(t);
                List<XWPFTableRow> rows = table.getRows();
                out.println("=== TABLE " + t + " (" + rows.size() + " rows) ===");

                for (int r = 0; r < rows.size(); r++) {
                    XWPFTableRow row = rows.get(r);
                    List<XWPFTableCell> cells = row.getTableCells();
                    out.println("  ROW " + r + " (" + cells.size() + " cells):");
                    for (int c = 0; c < cells.size(); c++) {
                        StringBuilder sb = new StringBuilder();
                        for (XWPFParagraph p : cells.get(c).getParagraphs()) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(p.getText());
                        }
                        String text = sb.toString().trim();
                        if (!text.isEmpty()) {
                            out.println("    [Col " + c + "]: " + text);
                        }
                    }
                    out.println("---");
                }
                out.println();
            }
        }
    }
}
