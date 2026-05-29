package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.ForeignLanguageTeaching;

import java.util.List;

@Repository
public interface ForeignLanguageTeachingRepository extends JpaRepository<ForeignLanguageTeaching, Long> {

    List<ForeignLanguageTeaching> findByTeacherId(Long teacherId);
    List<ForeignLanguageTeaching> findByTeacherIdIn(List<Long> teacherIds);
}
