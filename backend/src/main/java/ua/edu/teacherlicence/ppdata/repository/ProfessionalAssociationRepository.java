package ua.edu.teacherlicence.ppdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.ppdata.model.ProfessionalAssociation;

import java.util.List;

@Repository
public interface ProfessionalAssociationRepository extends JpaRepository<ProfessionalAssociation, Long> {

    List<ProfessionalAssociation> findByTeacherId(Long teacherId);
    List<ProfessionalAssociation> findByTeacherIdIn(List<Long> teacherIds);
}
