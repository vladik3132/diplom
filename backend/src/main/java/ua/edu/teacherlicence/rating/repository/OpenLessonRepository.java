package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.rating.model.OpenLesson;

import java.util.List;

public interface OpenLessonRepository extends JpaRepository<OpenLesson, Long> {
    List<OpenLesson> findByTeacherIdOrderByDateDesc(Long teacherId);
    void deleteByTeacherId(Long teacherId);
}
