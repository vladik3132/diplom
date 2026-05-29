package ua.edu.teacherlicence.docx.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.docx.model.DocxTemplate;
import ua.edu.teacherlicence.docx.service.DocxGeneratorService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/docx")
@RequiredArgsConstructor
public class DocxController {

    private final DocxGeneratorService docxService;
    private final CurrentUserProvider currentUser;

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/templates")
    public List<DocxTemplate> getTemplates() {
        return docxService.findAllTemplates();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @GetMapping("/templates/{id}")
    public DocxTemplate getTemplate(@PathVariable Long id) {
        return docxService.findTemplateById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public DocxTemplate createTemplate(@RequestBody DocxTemplate template) {
        return docxService.saveTemplate(template);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PutMapping("/templates/{id}")
    public DocxTemplate updateTemplate(@PathVariable Long id, @RequestBody DocxTemplate template) {
        DocxTemplate existing = docxService.findTemplateById(id);
        existing.setName(template.getName());
        existing.setDescription(template.getDescription());
        existing.setTemplateConfig(template.getTemplateConfig());
        return docxService.saveTemplate(existing);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable Long id) {
        docxService.deleteTemplate(id);
    }

    /** ADMIN/HEAD can generate for any teacher in scope. TEACHER can generate own. */
    @GetMapping("/generate/{templateId}/teacher/{teacherId}")
    public ResponseEntity<byte[]> generateDocument(
            @PathVariable Long templateId,
            @PathVariable Long teacherId) throws IOException, AccessDeniedException {
        currentUser.checkTeacherAccess(teacherId);

        byte[] docxBytes = docxService.generateDocument(templateId, teacherId);

        String filename = URLEncoder.encode("document.docx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(docxBytes.length);

        return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);
    }
}
