package ua.edu.teacherlicence.compliance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.compliance.model.TeacherProgramMatchCache;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherProgramMatchCacheRepository extends JpaRepository<TeacherProgramMatchCache, Long> {

    Optional<TeacherProgramMatchCache> findByTeacherIdAndProgramId(Long teacherId, Long programId);

    List<TeacherProgramMatchCache> findByTeacherId(Long teacherId);

    List<TeacherProgramMatchCache> findByProgramId(Long programId);

    @Modifying
    @Query("DELETE FROM TeacherProgramMatchCache c WHERE c.teacherId = :tid AND c.programId = :pid")
    void deleteByTeacherAndProgram(@Param("tid") Long teacherId, @Param("pid") Long programId);

    @Modifying
    @Query("DELETE FROM TeacherProgramMatchCache c WHERE c.teacherId = :tid")
    void deleteByTeacherId(@Param("tid") Long teacherId);

    @Modifying
    @Query("DELETE FROM TeacherProgramMatchCache c WHERE c.programId = :pid")
    void deleteByProgramId(@Param("pid") Long programId);
}
