package es.uma.urbanpulse.incident.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Página de incidencias registradas por el ciudadano, incluidas las rechazadas y resueltas.")
public record CitizenIncidentPageResponse(
		List<IncidentResponse> incidents,
		int page,
		int size,
		boolean hasMore) {

	public CitizenIncidentPageResponse {
		incidents = incidents == null ? List.of() : List.copyOf(incidents);
	}
}
