package ua.edu.teacherlicence.gantt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.gantt.model.GanttEvent;

import java.util.List;

@Repository
public interface GanttEventRepository extends JpaRepository<GanttEvent, Long> {

    List<GanttEvent> findByTeacherId(Long teacherId);

    List<GanttEvent> findByAcademicYear(String academicYear);
}
