package es.uma.urbanpulse.incident.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Página de incidencias filtrada y ordenada mediante cursor.")
public record IncidentPageResponse(
		List<IncidentResponse> incidents,
		int size,
		boolean hasMore,
		String nextCursor) {

	public IncidentPageResponse {
		incidents = List.copyOf(incidents);
	}
}
