package ua.edu.teacherlicence.fakhove.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.fakhove.model.ScopusJournal;

import java.util.List;

@Repository
public interface ScopusJournalRepository extends JpaRepository<ScopusJournal, Long> {

    List<ScopusJournal> findByIssn(String issn);

    List<ScopusJournal> findByEissn(String eissn);

    List<ScopusJournal> findByNameNormalizedContaining(String query);

    /** Зворотний пошук: де назва з публікації МІСТИТЬ назву з реєстру */
    @Query("SELECT j FROM ScopusJournal j WHERE :query LIKE CONCAT('%', j.nameNormalized, '%') AND LENGTH(j.nameNormalized) >= 10")
    List<ScopusJournal> findWhereQueryContainsName(@Param("query") String query);

    @Query("SELECT j FROM ScopusJournal j WHERE LOWER(j.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ScopusJournal> searchByName(@Param("query") String query);

    @Query("SELECT j FROM ScopusJournal j WHERE j.issn = :issn OR j.eissn = :issn")
    List<ScopusJournal> findByAnyIssn(@Param("issn") String issn);
}
