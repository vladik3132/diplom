package ua.edu.teacherlicence.editorial.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.editorial.model.EditorialPlanItem;

import java.util.List;

public interface EditorialPlanItemRepository extends JpaRepository<EditorialPlanItem, Long> {
    List<EditorialPlanItem> findByPlanId(Long planId);
    List<EditorialPlanItem> findByTeacherId(Long teacherId);
    List<EditorialPlanItem> findByStatus(EditorialPlanItem.EditorialItemStatus status);
}
