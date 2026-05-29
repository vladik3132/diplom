package ua.edu.teacherlicence.teacher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.teacher.model.CareerRecord;

import java.util.List;

@Repository
public interface CareerRecordRepository extends JpaRepository<CareerRecord, Long> {

    /**
     * Хронологічно: спершу найстаріші (start ASC), null start у кінці.
     * Якщо start однаковий — сортуємо за end ASC (null end теж у кінці).
     */
    @Query("SELECT c FROM CareerRecord c WHERE c.teacher.id = :teacherId "
         + "ORDER BY c.startDate ASC NULLS LAST, c.endDate ASC NULLS LAST, c.id ASC")
    List<CareerRecord> findByTeacherId(@Param("teacherId") Long teacherId);

    List<CareerRecord> findByTeacherIdIn(List<Long> teacherIds);
}
