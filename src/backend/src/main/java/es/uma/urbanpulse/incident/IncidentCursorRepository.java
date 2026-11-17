package es.uma.urbanpulse.incident;

import java.util.List;

import es.uma.urbanpulse.auth.TokenSubject;
import es.uma.urbanpulse.incident.api.IncidentCursor;
import es.uma.urbanpulse.incident.api.IncidentQueryFilters;

public interface IncidentCursorRepository {

	List<Incident> findNextPage(
			IncidentQueryFilters filters,
			TokenSubject caller,
			IncidentCursor cursor,
			String sortBy,
			String sortDirection,
			int limit);
}
