package es.uma.urbanpulse.incident;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {

	/** The history of one incident, oldest first. Served by idx_status_change_incident_id_changed_at. */
	@EntityGraph(attributePaths = "changedBy")
	List<StatusChange> findByIncidentIdOrderByChangedAtAscIdAsc(Integer incidentId);

}
