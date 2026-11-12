package es.uma.urbanpulse.incident.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Página de incidencias filtrada y ordenada en el servidor.")
public record IncidentPageResponse(
		List<IncidentResponse> incidents,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public IncidentPageResponse {
		incidents = List.copyOf(incidents);
	}
}
