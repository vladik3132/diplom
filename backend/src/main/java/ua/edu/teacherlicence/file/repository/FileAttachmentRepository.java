package ua.edu.teacherlicence.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.file.model.FileAttachment;

import java.util.Collection;
import java.util.List;

@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    List<FileAttachment> findByEntityTypeAndEntityId(String entityType, Long entityId);

    List<FileAttachment> findByEntityTypeAndEntityIdIn(String entityType, Collection<Long> entityIds);

    void deleteByEntityTypeAndEntityId(String entityType, Long entityId);
}
