package ua.edu.teacherlicence.docx.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.docx.dto.ColumnHeaderDto;
import ua.edu.teacherlicence.docx.dto.DataFieldDto;
import ua.edu.teacherlicence.docx.dto.TemplateUploadResponse;
import ua.edu.teacherlicence.docx.model.DataFieldKey;
import ua.edu.teacherlicence.docx.model.DocxExportTemplate;
import ua.edu.teacherlicence.docx.service.DepartmentExportService;
import ua.edu.teacherlicence.docx.service.TemplateParserService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/docx-export")
@RequiredArgsConstructor
public class DocxExportController {

    private final TemplateParserService templateParserService;
    private final DepartmentExportService departmentExportService;
    private final CurrentUserProvider currentUser;

    // ── Upload & parse ────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping(value = "/upload-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateUploadResponse uploadTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tableIndex", defaultValue = "0") int tableIndex,
            @RequestParam(value = "headerRowCount", defaultValue = "1") int headerRowCount
    ) throws IOException {
        return templateParserService.uploadAndParse(file, tableIndex, headerRowCount);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/parse-headers")
    public List<ColumnHeaderDto> parseHeaders(@RequestBody ParseHeadersRequest request) throws IOException {
        return templateParserService.parseHeaders(
                request.templateFileName(),
                request.tableIndex() != null ? request.tableIndex() : 0,
                request.headerRowCount() != null ? request.headerRowCount() : 1
        );
    }

    // ── Data fields catalog ───────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/data-fields")
    public List<DataFieldDto> getDataFields() {
        return Arrays.stream(DataFieldKey.values())
                .map(k -> new DataFieldDto(k.name(), k.getLabel(), k.getDescriptionHint(), k.getGroup()))
                .toList();
    }

    // ── CRUD for export template mappings ─────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/mappings")
    public List<DocxExportTemplate> getMappings() {
        return departmentExportService.findAllTemplates();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/mappings/{id}")
    public DocxExportTemplate getMapping(@PathVariable Long id) {
        return departmentExportService.findTemplateById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/mappings")
    @ResponseStatus(HttpStatus.CREATED)
    public DocxExportTemplate createMapping(@RequestBody DocxExportTemplate template) {
        template.setId(null);
        return departmentExportService.saveTemplate(template);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/mappings/{id}")
    public DocxExportTemplate updateMapping(@PathVariable Long id, @RequestBody DocxExportTemplate template) {
        DocxExportTemplate existing = departmentExportService.findTemplateById(id);
        existing.setName(template.getName());
        existing.setColumnMappingsJson(template.getColumnMappingsJson());
        existing.setTableIndex(template.getTableIndex());
        existing.setHeaderRowCount(template.getHeaderRowCount());
        return departmentExportService.saveTemplate(existing);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/mappings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMapping(@PathVariable Long id) {
        departmentExportService.deleteTemplate(id);
    }

    // ── Generate & download ───────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/generate/{templateId}/department/{departmentId}")
    public ResponseEntity<byte[]> generateExport(
            @PathVariable Long templateId,
            @PathVariable Long departmentId
    ) throws IOException, AccessDeniedException {
        if (!currentUser.isAdmin()) currentUser.checkDepartmentAccess(departmentId);

        byte[] docxBytes = departmentExportService.exportDepartment(templateId, departmentId);

        String filename = "export_department_" + departmentId + ".docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
        headers.setContentLength(docxBytes.length);

        return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/generate-multi/{templateId}")
    public ResponseEntity<byte[]> generateExportMultiple(
            @PathVariable Long templateId,
            @RequestBody(required = false) List<Long> departmentIds
    ) throws IOException, AccessDeniedException {
        if (!currentUser.isAdmin()) {
            if (departmentIds == null || departmentIds.isEmpty()) {
                throw new AccessDeniedException("Тільки адміністратор може експортувати всі кафедри");
            }
            for (Long deptId : departmentIds) {
                currentUser.checkDepartmentAccess(deptId);
            }
        }

        byte[] docxBytes = departmentExportService.exportDepartments(templateId, departmentIds);

        String filename = (departmentIds == null || departmentIds.isEmpty())
                ? "export_all.docx"
                : "export_departments_" + String.join("_", departmentIds.stream().map(String::valueOf).toList()) + ".docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
        headers.setContentLength(docxBytes.length);

        return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/generate/{templateId}/program/{programId}")
    public ResponseEntity<byte[]> generateExportByProgram(
            @PathVariable Long templateId,
            @PathVariable Long programId
    ) throws IOException {
        byte[] docxBytes = departmentExportService.exportByProgram(templateId, programId);

        String filename = "export_program_" + programId + ".docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
        headers.setContentLength(docxBytes.length);

        return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/generate-teachers/{templateId}")
    public ResponseEntity<byte[]> generateExportByTeachers(
            @PathVariable Long templateId,
            @RequestBody List<Long> teacherIds
    ) throws IOException {
        byte[] docxBytes = departmentExportService.exportByTeacherIds(templateId, teacherIds);

        String filename = "export_teachers_" + teacherIds.size() + ".docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
        headers.setContentLength(docxBytes.length);

        return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record ParseHeadersRequest(String templateFileName, Integer tableIndex, Integer headerRowCount) {
    }
}
