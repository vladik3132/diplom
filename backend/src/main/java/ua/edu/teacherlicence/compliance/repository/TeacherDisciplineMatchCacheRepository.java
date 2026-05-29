package ua.edu.teacherlicence.compliance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.compliance.model.TeacherDisciplineMatchCache;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherDisciplineMatchCacheRepository extends JpaRepository<TeacherDisciplineMatchCache, Long> {

    Optional<TeacherDisciplineMatchCache> findByTeacherIdAndDisciplineId(Long teacherId, Long disciplineId);

    List<TeacherDisciplineMatchCache> findByTeacherId(Long teacherId);

    List<TeacherDisciplineMatchCache> findByDisciplineId(Long disciplineId);

    List<TeacherDisciplineMatchCache> findByDisciplineIdIn(List<Long> disciplineIds);

    @Modifying
    @Query("DELETE FROM TeacherDisciplineMatchCache c WHERE c.teacherId = :tid AND c.disciplineId = :did")
    void deleteByTeacherAndDiscipline(@Param("tid") Long teacherId, @Param("did") Long disciplineId);

    @Modifying
    @Query("DELETE FROM TeacherDisciplineMatchCache c WHERE c.teacherId = :tid")
    void deleteByTeacherId(@Param("tid") Long teacherId);

    @Modifying
    @Query("DELETE FROM TeacherDisciplineMatchCache c WHERE c.disciplineId = :did")
    void deleteByDisciplineId(@Param("did") Long disciplineId);
}
