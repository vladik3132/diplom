package ua.edu.teacherlicence.rating.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;
import ua.edu.teacherlicence.rating.dto.*;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Генерація протоколу рейтингової комісії у форматі DOCX.
 */
@Service
@RequiredArgsConstructor
public class RatingProtocolService {

    private static final int FONT_SIZE_BODY = 14;   // sz=28 half-points
    private static final int FONT_SIZE_TABLE = 12;   // sz=24 half-points (як в Normal)
    private static final int LINE_SPACING_SINGLE = 240; // twips — single spacing

    /** Родовий відмінок для військових звань */
    private static final Map<String, String> RANK_GENITIVE = Map.ofEntries(
            Map.entry("полковник", "полковника"),
            Map.entry("підполковник", "підполковника"),
            Map.entry("майор", "майора"),
            Map.entry("капітан", "капітана"),
            Map.entry("старший лейтенант", "старшого лейтенанта"),
            Map.entry("лейтенант", "лейтенанта"),
            Map.entry("генерал-майор", "генерал-майора"),
            Map.entry("генерал-лейтенант", "генерал-лейтенанта"),
            Map.entry("бригадний генерал", "бригадного генерала"),
            Map.entry("старший сержант", "старшого сержанта"),
            Map.entry("сержант", "сержанта"),
            Map.entry("прапорщик", "прапорщика"),
            Map.entry("старший прапорщик", "старшого прапорщика"),
            Map.entry("працівник ЗСУ", "працівника ЗСУ")
    );

    private final RatingService ratingService;
    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;

    public byte[] generateProtocol(Long periodId, ProtocolRequest req) throws IOException {
        List<TeacherRatingSummaryDto> teachers = ratingService.getTeacherRankings(periodId, null);
        List<DepartmentRatingSummaryDto> departments = ratingService.getDepartmentRankings(periodId);
        List<FacultyRatingSummaryDto> faculties = ratingService.getFacultyRankings(periodId);

        try (XWPFDocument doc = new XWPFDocument()) {
            setupPage(doc);

            // ── Заголовок ──
            addCenteredParagraph(doc, "ПРОТОКОЛ", FONT_SIZE_BODY, false);

            // ── Підзаголовок ──
            String subtitle = String.format(
                    "засідання рейтингової комісії %s від %s № %s",
                    orPlaceholder(req.getInstitutionName(), "___"),
                    orPlaceholder(req.getProtocolDate(), "___.___.20___"),
                    orPlaceholder(req.getProtocolNumber(), "___")
            );
            addCenteredParagraph(doc, subtitle, FONT_SIZE_BODY, false);

            // ── Пустий рядок ──
            addEmptyParagraph(doc);

            // ── Текст про комісію ──
            String commissionText = buildCommissionText(req);
            addJustifiedParagraph(doc, commissionText, FONT_SIZE_BODY, 709);

            // ── Пустий рядок (8pt) ──
            addEmptyParagraph(doc);

            // ── Таблиця 1: Рейтинг НПП ──
            addCenteredParagraph(doc, "Рейтинг науково-педагогічних працівників Інституту", FONT_SIZE_BODY, false);
            buildTeacherTable(doc, teachers);

            addEmptyParagraph(doc);

            // ── Таблиця 2: Рейтинг кафедр ──
            addCenteredParagraph(doc, "Рейтинг кафедр Інституту", FONT_SIZE_BODY, false);
            buildDepartmentTable(doc, departments);

            addEmptyParagraph(doc);

            // ── Таблиця 3: Рейтинг факультетів ──
            addCenteredParagraph(doc, "Рейтинг факультетів Інституту", FONT_SIZE_BODY, false);
            buildFacultyTable(doc, faculties);

            addEmptyParagraph(doc);

            // ── Підписи ──
            buildSignaturesTable(doc, req.getCommissionMembers());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // ── Налаштування сторінки ──

    private void setupPage(XWPFDocument doc) {
        CTDocument1 ctDoc = doc.getDocument();
        CTBody body = ctDoc.getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

        // A4
        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(BigInteger.valueOf(11906));
        pgSz.setH(BigInteger.valueOf(16838));

        // Поля
        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setTop(BigInteger.valueOf(1134));
        pgMar.setBottom(BigInteger.valueOf(1134));
        pgMar.setLeft(BigInteger.valueOf(1701));
        pgMar.setRight(BigInteger.valueOf(707));
    }

    // ── Побудова тексту комісії (звання — родовий відмінок) ──

    private String buildCommissionText(ProtocolRequest req) {
        List<ProtocolRequest.CommissionMember> members = req.getCommissionMembers();
        StringBuilder sb = new StringBuilder();
        sb.append("Комісія ");
        sb.append(orPlaceholder(req.getInstitutionName(), "___"));
        sb.append(" для проведення рейтингового оцінювання науково-педагогічних працівників, ");
        sb.append("яка призначена наказом начальника Інституту");

        String orderNum = req.getOrderNumber();
        sb.append(" № ").append(orderNum != null && !orderNum.isBlank() ? orderNum : "___/нагд");

        String orderDate = req.getOrderDate();
        sb.append(" від ").append(orderDate != null && !orderDate.isBlank() ? orderDate : "___.___ 20___");
        sb.append(", у складі: ");

        if (members != null && !members.isEmpty()) {
            boolean membersPrefixAdded = false;
            for (int i = 0; i < members.size(); i++) {
                ProtocolRequest.CommissionMember m = members.get(i);
                String role = m.getRole();

                // Роль
                switch (role) {
                    case "CHAIR" -> sb.append("голови комісії – ");
                    case "VICE_CHAIR" -> sb.append("заступника голови комісії – ");
                    case "SECRETARY" -> sb.append("секретаря комісії – ");
                    case "MEMBER" -> {
                        if (!membersPrefixAdded) {
                            sb.append("членів комісії: ");
                            membersPrefixAdded = true;
                        }
                    }
                }

                // Звання в родовому відмінку
                String rank = m.getRank();
                if (rank != null && !rank.isBlank()) {
                    sb.append(toGenitive(rank)).append(" ");
                }

                // Скорочене ПІБ (Симоненка О.А.)
                sb.append(orPlaceholder(m.getShortName(), m.getName()));

                // Розділювач
                if (i < members.size() - 1) {
                    sb.append(", ");
                } else {
                    sb.append(" ");
                }
            }
        }

        sb.append("розглянула і перевірила надані відомості та рейтинговий бал ");
        sb.append("науково-педагогічних працівників Інституту ");
        sb.append("та затвердила такі рейтинги:");

        return sb.toString();
    }

    /** Перетворення звання до родового відмінку */
    private String toGenitive(String rank) {
        String lower = rank.trim().toLowerCase();
        return RANK_GENITIVE.getOrDefault(lower, rank);
    }

    // ── Таблиця НПП ──

    private void buildTeacherTable(XWPFDocument doc, List<TeacherRatingSummaryDto> teachers) {
        String[] headers = {"МІСЦЕ в рейтингу", "Кафедра", "Посада", "Військове звання",
                "Прізвище, ім\u2019я по батькові", "Рейтинговий бал"};
        int[] widths = {1271, 1134, 1418, 1701, 2409, 1559};

        XWPFTable table = doc.createTable(1 + teachers.size(), headers.length);
        setTableWidth(table, 9492);
        setFixedLayout(table);
        setBorders(table);

        // Header row
        XWPFTableRow headerRow = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            setCell(headerRow.getCell(i), headers[i], widths[i]);
        }

        // Data rows
        for (int i = 0; i < teachers.size(); i++) {
            TeacherRatingSummaryDto ts = teachers.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            Teacher teacher = teacherRepository.findById(ts.getTeacherId()).orElse(null);

            String deptNumber = "";
            String position = "";
            String milRank = "";
            String fullName = ts.getTeacherName();

            if (teacher != null) {
                deptNumber = teacher.getDepartment() != null
                        ? teacher.getDepartment().getNumber() : "";
                position = cleanPosition(teacherPositionService.getEffectivePosition(teacher));
                milRank = teacher.getMilitaryRank() != null ? teacher.getMilitaryRank() : "";
            }

            setCell(row.getCell(0), String.valueOf(ts.getRank()), widths[0]);
            setCell(row.getCell(1), deptNumber, widths[1]);
            setCell(row.getCell(2), position, widths[2]);
            setCell(row.getCell(3), milRank, widths[3]);
            setCell(row.getCell(4), fullName, widths[4]);
            setCell(row.getCell(5), String.valueOf(ts.getTotalScore()), widths[5]);
        }
    }

    // ── Таблиця кафедр ──

    private void buildDepartmentTable(XWPFDocument doc, List<DepartmentRatingSummaryDto> departments) {
        String[] headers = {"МІСЦЕ в рейтингу", "Кафедра", "Середній рейтинговий бал НПП кафедри"};
        int[] widths = {1271, 6662, 1560};

        XWPFTable table = doc.createTable(1 + departments.size(), headers.length);
        setTableWidth(table, 9493);
        setFixedLayout(table);
        setBorders(table);

        XWPFTableRow headerRow = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            setCell(headerRow.getCell(i), headers[i], widths[i]);
        }

        for (int i = 0; i < departments.size(); i++) {
            DepartmentRatingSummaryDto d = departments.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCell(row.getCell(0), String.valueOf(d.getRank()), widths[0]);
            setCell(row.getCell(1), d.getDepartmentName(), widths[1]);
            setCell(row.getCell(2), String.valueOf(d.getAverageScore()), widths[2]);
        }
    }

    // ── Таблиця факультетів ──

    private void buildFacultyTable(XWPFDocument doc, List<FacultyRatingSummaryDto> faculties) {
        String[] headers = {"МІСЦЕ в рейтингу", "Факультет", "Середній рейтинговий бал кафедр факультету"};
        int[] widths = {1271, 6662, 1560};

        XWPFTable table = doc.createTable(1 + faculties.size(), headers.length);
        setTableWidth(table, 9493);
        setFixedLayout(table);
        setBorders(table);

        XWPFTableRow headerRow = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            setCell(headerRow.getCell(i), headers[i], widths[i]);
        }

        for (int i = 0; i < faculties.size(); i++) {
            FacultyRatingSummaryDto f = faculties.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCell(row.getCell(0), String.valueOf(f.getRank()), widths[0]);
            setCell(row.getCell(1), f.getFacultyName(), widths[1]);
            setCell(row.getCell(2), String.valueOf(f.getAverageScore()), widths[2]);
        }
    }

    // ── Таблиця підписів (без рамок) ──

    private void buildSignaturesTable(XWPFDocument doc, List<ProtocolRequest.CommissionMember> members) {
        if (members == null || members.isEmpty()) return;

        XWPFTable table = doc.createTable(members.size(), 4);
        setTableWidth(table, 9498);

        // Без рамок
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        CTTblBorders borders = tblPr.isSetTblBorders()
                ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        borders.addNewTop().setVal(STBorder.NONE);
        borders.addNewBottom().setVal(STBorder.NONE);
        borders.addNewLeft().setVal(STBorder.NONE);
        borders.addNewRight().setVal(STBorder.NONE);
        borders.addNewInsideH().setVal(STBorder.NONE);
        borders.addNewInsideV().setVal(STBorder.NONE);

        int[] widths = {2372, 2372, 1068, 3686};

        for (int i = 0; i < members.size(); i++) {
            ProtocolRequest.CommissionMember m = members.get(i);
            XWPFTableRow row = table.getRow(i);

            String roleLabel = switch (m.getRole()) {
                case "CHAIR" -> "Голова комісії:";
                case "VICE_CHAIR" -> "Заступник голови";
                case "SECRETARY" -> "Секретар комісії";
                case "MEMBER" -> {
                    boolean prevIsMember = i > 0 && "MEMBER".equals(members.get(i - 1).getRole());
                    yield prevIsMember ? "" : "Члени комісії:";
                }
                default -> "";
            };

            setSignatureCell(row.getCell(0), roleLabel, widths[0]);
            setSignatureCell(row.getCell(1), orEmpty(m.getRank()), widths[1]);
            setSignatureCell(row.getCell(2), "", widths[2]); // Для підпису
            setSignatureCell(row.getCell(3), orEmpty(m.getName()), widths[3]);
        }
    }

    // ══════════════════════════════════════
    // ══  Helper methods  ══
    // ══════════════════════════════════════

    /** Абзац по центру, 14pt, single spacing, no before/after */
    private void addCenteredParagraph(XWPFDocument doc, String text, int fontSize, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        setSingleSpacing(p);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
        run.setFontFamily("Times New Roman");
        if (bold) run.setBold(true);
    }

    /** Абзац по ширині, 14pt, single spacing, з відступом першого рядка */
    private void addJustifiedParagraph(XWPFDocument doc, String text, int fontSize, int firstLineIndent) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        setSingleSpacing(p);
        if (firstLineIndent > 0) {
            p.setFirstLineIndent(firstLineIndent);
        }
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
        run.setFontFamily("Times New Roman");
    }

    /** Пустий розділювальний рядок (8pt font, single spacing) */
    private void addEmptyParagraph(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        setSingleSpacing(p);
        XWPFRun run = p.createRun();
        run.setFontSize(8);
    }

    /** Встановити single spacing без before/after */
    private void setSingleSpacing(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setLine(BigInteger.valueOf(LINE_SPACING_SINGLE));
        spacing.setLineRule(STLineSpacingRule.AUTO);
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
    }

    /** Комірка таблиці: 12pt, centered, single spacing, vAlign center */
    private void setCell(XWPFTableCell cell, String text, int width) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        setSingleSpacing(p);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(FONT_SIZE_TABLE);
        run.setFontFamily("Times New Roman");

        // Cell width
        CTTcPr tcPr = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        tcW.setW(BigInteger.valueOf(width));
        tcW.setType(STTblWidth.DXA);
        tcPr.addNewVAlign().setVal(STVerticalJc.CENTER);
    }

    /** Комірка підписів: 14pt, без рамки, single spacing */
    private void setSignatureCell(XWPFTableCell cell, String text, int width) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        setSingleSpacing(p);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(FONT_SIZE_BODY);
        run.setFontFamily("Times New Roman");

        CTTcPr tcPr = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        tcW.setW(BigInteger.valueOf(width));
        tcW.setType(STTblWidth.DXA);
    }

    private void setTableWidth(XWPFTable table, int width) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        CTTblWidth tblW = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setW(BigInteger.valueOf(width));
        tblW.setType(STTblWidth.DXA);
    }

    private void setFixedLayout(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        CTTblLayoutType layout = tblPr.isSetTblLayout()
                ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
    }

    private void setBorders(XWPFTable table) {
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
    }

    private String orPlaceholder(String val, String placeholder) {
        return val != null && !val.isBlank() ? val : placeholder;
    }

    private String orEmpty(String val) {
        return val != null ? val : "";
    }

    /**
     * Обрізає назву кафедри з посади.
     * "Викладач кафедри комп'ютерних наук" → "Викладач"
     * "Доцент кафедри ..." → "Доцент"
     * Залишає повне: "Начальник кафедри", "Заступник начальника кафедри"
     */
    /**
     * Обрізає назву кафедри з посади:
     * "Викладач кафедри комп'ютерних наук" → "Викладач"
     * "Начальник кафедри комп'ютерних наук" → "Начальник кафедри"
     * "Заступник начальника кафедри комп'ютерних наук" → "Заступник начальника кафедри"
     */
    private String cleanPosition(String position) {
        if (position == null || position.isBlank()) return "";
        String trimmed = position.trim();
        String lower = trimmed.toLowerCase();

        // Керівні посади — лишаємо тільки "Начальник кафедри" без назви
        if (lower.startsWith("начальник кафедри")) return "Начальник кафедри";
        if (lower.startsWith("заступник начальника кафедри")) return "Заступник начальника кафедри";
        if (lower.startsWith("завідувач кафедри")) return "Завідувач кафедри";

        // Інші посади — обрізаємо " кафедри ..."
        int idx = lower.indexOf(" кафедри");
        if (idx > 0) {
            return trimmed.substring(0, idx);
        }

        return trimmed;
    }
}
