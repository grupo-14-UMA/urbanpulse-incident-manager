package es.uma.urbanpulse.incident;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import es.uma.urbanpulse.incident.api.IncidentCursor;

public interface IncidentCursorRepository {

	List<Incident> findNextPage(
			Specification<Incident> specification,
			IncidentCursor cursor,
			String sortBy,
			String sortDirection,
			int limit);
}
