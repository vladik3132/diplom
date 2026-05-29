package ua.edu.teacherlicence.editorial.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.editorial.model.EditorialPlan;
import ua.edu.teacherlicence.editorial.model.EditorialPlanItem;
import ua.edu.teacherlicence.editorial.repository.EditorialPlanItemRepository;
import ua.edu.teacherlicence.editorial.repository.EditorialPlanRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EditorialService {

    private final EditorialPlanRepository planRepository;
    private final EditorialPlanItemRepository itemRepository;

    public List<EditorialPlan> findAllPlans() {
        return planRepository.findAll();
    }

    public EditorialPlan findPlanById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("План не знайдено: " + id));
    }

    public List<EditorialPlan> findPlansByDepartment(Long departmentId) {
        return planRepository.findByDepartmentId(departmentId);
    }

    @Transactional
    public EditorialPlan createPlan(EditorialPlan plan) {
        return planRepository.save(plan);
    }

    @Transactional
    public EditorialPlan updatePlan(Long id, EditorialPlan updated) {
        EditorialPlan existing = findPlanById(id);
        existing.setTitle(updated.getTitle());
        existing.setAcademicYear(updated.getAcademicYear());
        existing.setFileUrl(updated.getFileUrl());
        return planRepository.save(existing);
    }

    @Transactional
    public void deletePlan(Long id) {
        planRepository.deleteById(id);
    }

    public List<EditorialPlanItem> findItemsByPlan(Long planId) {
        return itemRepository.findByPlanId(planId);
    }

    public List<EditorialPlanItem> findItemsByTeacher(Long teacherId) {
        return itemRepository.findByTeacherId(teacherId);
    }

    @Transactional
    public EditorialPlanItem createItem(EditorialPlanItem item) {
        return itemRepository.save(item);
    }

    @Transactional
    public EditorialPlanItem updateItem(Long id, EditorialPlanItem updated) {
        EditorialPlanItem existing = itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Елемент плану не знайдено: " + id));
        existing.setTitle(updated.getTitle());
        existing.setType(updated.getType());
        existing.setPlannedDate(updated.getPlannedDate());
        existing.setActualDate(updated.getActualDate());
        existing.setStatus(updated.getStatus());
        return itemRepository.save(existing);
    }

    @Transactional
    public void deleteItem(Long id) {
        itemRepository.deleteById(id);
    }
}
