package ua.edu.teacherlicence.discipline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.discipline.model.DisciplineDocument;
import ua.edu.teacherlicence.discipline.model.DocumentStatus;

import java.time.LocalDate;
import java.util.List;

public interface DisciplineDocumentRepository extends JpaRepository<DisciplineDocument, Long> {

    List<DisciplineDocument> findByTeacherId(Long teacherId);

    List<DisciplineDocument> findByDisciplineId(Long disciplineId);

    List<DisciplineDocument> findByStatus(DocumentStatus status);

    List<DisciplineDocument> findByDeadlineBetween(LocalDate from, LocalDate to);

    List<DisciplineDocument> findByDeadlineBeforeAndStatusNot(LocalDate date, DocumentStatus status);

    void deleteByDisciplineId(Long disciplineId);
}
