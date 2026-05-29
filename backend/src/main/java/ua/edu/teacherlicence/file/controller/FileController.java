package ua.edu.teacherlicence.file.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.file.model.FileAttachment;
import ua.edu.teacherlicence.file.service.FileAttachmentService;

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileAttachmentService fileAttachmentService;
    private final CurrentUserProvider currentUser;

    /**
     * Upload a file and attach it to an entity.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileAttachment> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("entityType") String entityType,
            @RequestParam("entityId") Long entityId,
            @RequestParam(value = "teacherId", required = false) Long teacherId
    ) throws IOException, AccessDeniedException {
        if (teacherId != null) {
            currentUser.checkTeacherAccess(teacherId);
        }

        FileAttachment saved = fileAttachmentService.upload(
                file, entityType, entityId, teacherId,
                currentUser.getCurrentUser().getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * List files attached to an entity.
     */
    @GetMapping
    public List<FileAttachment> list(
            @RequestParam("entityType") String entityType,
            @RequestParam("entityId") Long entityId
    ) {
        return fileAttachmentService.findByEntity(entityType, entityId);
    }

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * Preview/download a file (proxied from storage).
     * DOCX files are converted to PDF on-the-fly for browser preview.
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) throws IOException {
        FileAttachment fa = fileAttachmentService.findById(id);
        byte[] content = fileAttachmentService.download(fa);

        // Convert DOCX → PDF for preview
        if (DOCX_MIME.equals(fa.getMimeType()) || fa.getOriginalName().toLowerCase().endsWith(".docx")) {
            try {
                content = convertDocxToPdf(content);
                String pdfName = fa.getOriginalName().replaceAll("(?i)\\.docx$", ".pdf");
                String encodedName = URLEncoder.encode(pdfName, StandardCharsets.UTF_8)
                        .replace("+", "%20");
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header("Content-Disposition", "inline; filename*=UTF-8''" + encodedName)
                        .body(content);
            } catch (Exception e) {
                log.warn("DOCX→PDF conversion failed for file {}: {}", fa.getOriginalName(), e.getMessage());
                // Fall through to original file download
            }
        }

        String encodedName = URLEncoder.encode(fa.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fa.getMimeType()))
                .header("Content-Disposition", "inline; filename*=UTF-8''" + encodedName)
                .body(content);
    }

    private byte[] convertDocxToPdf(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             ByteArrayOutputStream pdfOut = new ByteArrayOutputStream()) {
            PdfOptions options = PdfOptions.create();
            PdfConverter.getInstance().convert(doc, pdfOut, options);
            return pdfOut.toByteArray();
        }
    }

    /**
     * Download a file as attachment.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws IOException {
        FileAttachment fa = fileAttachmentService.findById(id);
        byte[] content = fileAttachmentService.download(fa);

        String encodedName = URLEncoder.encode(fa.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName)
                .body(content);
    }

    /**
     * Delete a file.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) throws IOException, AccessDeniedException {
        FileAttachment fa = fileAttachmentService.findById(id);
        fileAttachmentService.delete(fa);
    }
}
