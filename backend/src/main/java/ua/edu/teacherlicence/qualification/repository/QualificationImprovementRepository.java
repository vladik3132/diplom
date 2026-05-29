package ua.edu.teacherlicence.qualification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;

import java.util.List;

@Repository
public interface QualificationImprovementRepository extends JpaRepository<QualificationImprovement, Long> {

    List<QualificationImprovement> findByTeacherId(Long teacherId);

    List<QualificationImprovement> findByTeacherIdIn(List<Long> teacherIds);
}
