package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.rating.model.RatingPeriod;

import java.util.Optional;

public interface RatingPeriodRepository extends JpaRepository<RatingPeriod, Long> {
    Optional<RatingPeriod> findByActiveTrue();
    Optional<RatingPeriod> findByName(String name);
}
