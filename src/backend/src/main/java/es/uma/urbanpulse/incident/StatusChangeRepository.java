package es.uma.urbanpulse.incident;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {

	/** The five most recent history entries, newest first. Served by idx_status_change_incident_id_changed_at. */
	@EntityGraph(attributePaths = "changedBy")
	List<StatusChange> findTop5ByIncidentIdOrderByChangedAtDescIdDesc(Integer incidentId);

}
