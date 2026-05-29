package ua.edu.teacherlicence.teacher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.teacher.model.MilitaryEducation;

import java.util.List;

@Repository
public interface MilitaryEducationRepository extends JpaRepository<MilitaryEducation, Long> {

    List<MilitaryEducation> findByTeacherIdOrderByGraduationYearDesc(Long teacherId);

    void deleteByTeacherId(Long teacherId);
}
