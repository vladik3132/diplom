package ua.edu.teacherlicence.rating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.teacherlicence.rating.model.ProtocolSettings;

public interface ProtocolSettingsRepository extends JpaRepository<ProtocolSettings, Long> {
}
