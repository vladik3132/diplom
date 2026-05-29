package ua.edu.teacherlicence.docx.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.docx.model.DocxExportTemplate;

@Repository
public interface DocxExportTemplateRepository extends JpaRepository<DocxExportTemplate, Long> {
}
