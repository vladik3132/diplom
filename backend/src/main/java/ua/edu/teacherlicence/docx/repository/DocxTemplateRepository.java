package ua.edu.teacherlicence.docx.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.docx.model.DocxTemplate;

public interface DocxTemplateRepository extends JpaRepository<DocxTemplate, Long> {
}
