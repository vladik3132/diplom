package ua.edu.teacherlicence.publication.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.publication.model.Publication;

import java.util.List;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {

    List<Publication> findByTeacherId(Long teacherId);

    List<Publication> findByTeacherIdIn(List<Long> teacherIds);

    int countByTeacherId(Long teacherId);

    /** Кількість публікацій пп.1: статті з фахових/Scopus/WoS (type=ARTICLE, articleCategory IS NOT NULL) */
    int countByTeacherIdAndTypeAndArticleCategoryIsNotNull(Long teacherId, ua.edu.teacherlicence.publication.model.PublicationType type);

    /** Кількість статей пп.1 за напрямком кафедри (fieldRelevant=true) */
    int countByTeacherIdAndTypeAndArticleCategoryIsNotNullAndFieldRelevant(
            Long teacherId, ua.edu.teacherlicence.publication.model.PublicationType type, Boolean fieldRelevant);

    /** Методичні публікації без підтипу — для перекласифікації */
    List<Publication> findByTypeAndMethodicalSubtypeIsNull(ua.edu.teacherlicence.publication.model.PublicationType type);

    /** Апробації без підтипу */
    List<Publication> findByTypeAndApprobationSubtypeIsNull(ua.edu.teacherlicence.publication.model.PublicationType type);

    /** Всі публікації за типом */
    List<Publication> findByType(ua.edu.teacherlicence.publication.model.PublicationType type);
}
