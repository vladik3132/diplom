package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.ScientificConsulting;

import java.util.List;

@Repository
public interface ScientificConsultingRepository extends JpaRepository<ScientificConsulting, Long> {

    List<ScientificConsulting> findByTeacherId(Long teacherId);
    List<ScientificConsulting> findByTeacherIdIn(List<Long> teacherIds);
}
