package ua.edu.teacherlicence.fakhove.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.edu.teacherlicence.fakhove.model.FakhovyiJournal;

import java.util.List;

@Repository
public interface FakhovyiJournalRepository extends JpaRepository<FakhovyiJournal, Long> {

    List<FakhovyiJournal> findByNameNormalizedContaining(String query);

    /** Зворотний пошук: де назва з публікації МІСТИТЬ назву з реєстру */
    @Query("SELECT j FROM FakhovyiJournal j WHERE :query LIKE CONCAT('%', j.nameNormalized, '%') AND LENGTH(j.nameNormalized) >= 10")
    List<FakhovyiJournal> findWhereQueryContainsName(@Param("query") String query);

    @Query("SELECT j FROM FakhovyiJournal j WHERE LOWER(j.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<FakhovyiJournal> searchByName(@Param("query") String query);
}
