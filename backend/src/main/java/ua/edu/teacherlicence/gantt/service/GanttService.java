package ua.edu.teacherlicence.gantt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.gantt.model.GanttEvent;
import ua.edu.teacherlicence.gantt.repository.GanttEventRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GanttService {

    private final GanttEventRepository ganttEventRepository;

    public List<GanttEvent> findAll() {
        return ganttEventRepository.findAll();
    }

    public GanttEvent findById(Long id) {
        return ganttEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Подію не знайдено: " + id));
    }

    public List<GanttEvent> findByTeacherId(Long teacherId) {
        return ganttEventRepository.findByTeacherId(teacherId);
    }

    public List<GanttEvent> findByAcademicYear(String academicYear) {
        return ganttEventRepository.findByAcademicYear(academicYear);
    }

    @Transactional
    public GanttEvent create(GanttEvent event) {
        return ganttEventRepository.save(event);
    }

    @Transactional
    public GanttEvent update(Long id, GanttEvent updated) {
        GanttEvent existing = findById(id);
        existing.setTitle(updated.getTitle());
        existing.setEventType(updated.getEventType());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setStatus(updated.getStatus());
        existing.setAcademicYear(updated.getAcademicYear());
        existing.setSemester(updated.getSemester());
        existing.setColor(updated.getColor());
        existing.setNotes(updated.getNotes());
        return ganttEventRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        ganttEventRepository.deleteById(id);
    }
}
