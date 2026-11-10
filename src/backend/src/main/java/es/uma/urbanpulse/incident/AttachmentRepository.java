package es.uma.urbanpulse.incident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Integer> {

	List<Attachment> findByIncidentIdOrderByIdAsc(Integer incidentId);

	Optional<Attachment> findByIdAndIncidentId(Integer id, Integer incidentId);

	long countByIncidentId(Integer incidentId);

}
