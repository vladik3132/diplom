package ua.edu.teacherlicence.teacher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.teacher.model.AcademicTitle;

import java.util.List;

@Repository
public interface AcademicTitleRepository extends JpaRepository<AcademicTitle, Long> {

    @Query("SELECT t FROM AcademicTitle t WHERE t.teacher.id = :teacherId "
         + "ORDER BY t.attestatDate ASC NULLS LAST, t.id ASC")
    List<AcademicTitle> findByTeacherIdOrderByAttestatDateAsc(@Param("teacherId") Long teacherId);

    /** Batch-fetch для збагачення списку викладачів — уникаємо N+1. */
    @Query("SELECT t FROM AcademicTitle t WHERE t.teacher.id IN :teacherIds")
    List<AcademicTitle> findByTeacherIdIn(@Param("teacherIds") List<Long> teacherIds);

    void deleteByTeacherId(Long teacherId);
}
