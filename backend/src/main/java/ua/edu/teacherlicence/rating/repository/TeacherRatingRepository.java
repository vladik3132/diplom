package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ua.edu.teacherlicence.rating.model.RatingCriterion;
import ua.edu.teacherlicence.rating.model.TeacherRating;

import java.util.List;

public interface TeacherRatingRepository extends JpaRepository<TeacherRating, Long> {

    List<TeacherRating> findByPeriodIdAndTeacherId(Long periodId, Long teacherId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TeacherRating tr WHERE tr.period.id = :periodId AND tr.teacher.id = :teacherId")
    void deleteByPeriodIdAndTeacherId(Long periodId, Long teacherId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TeacherRating tr WHERE tr.period.id = :periodId")
    void deleteByPeriodId(Long periodId);

    @Query("SELECT tr.teacher.id, SUM(tr.score) " +
           "FROM TeacherRating tr " +
           "WHERE tr.period.id = :periodId " +
           "GROUP BY tr.teacher.id " +
           "ORDER BY SUM(tr.score) DESC")
    List<Object[]> findTotalScoresByPeriodId(Long periodId);

    @Query("SELECT tr.teacher.id, SUM(tr.score) " +
           "FROM TeacherRating tr " +
           "WHERE tr.period.id = :periodId AND tr.teacher.department.id = :departmentId " +
           "GROUP BY tr.teacher.id " +
           "ORDER BY SUM(tr.score) DESC")
    List<Object[]> findTotalScoresByPeriodIdAndDepartmentId(Long periodId, Long departmentId);

    @Query("SELECT tr.teacher.department.id, AVG(s.total) " +
           "FROM TeacherRating tr " +
           "JOIN (SELECT t.teacher.id as tid, SUM(t.score) as total FROM TeacherRating t WHERE t.period.id = :periodId GROUP BY t.teacher.id) s " +
           "ON tr.teacher.id = s.tid " +
           "WHERE tr.period.id = :periodId " +
           "GROUP BY tr.teacher.department.id")
    List<Object[]> findDepartmentAveragesByPeriodId(Long periodId);
}
