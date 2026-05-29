package ua.edu.teacherlicence.docx.dto;

import java.util.List;

public record TemplateUploadResponse(
        String templateFileName,
        String originalFileName,
        List<ColumnHeaderDto> columns
) {
}
