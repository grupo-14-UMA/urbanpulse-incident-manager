package es.uma.urbanpulse.incident.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Página de trabajos abiertos asignados al técnico.")
public record AssignedIncidentPageResponse(
		List<IncidentResponse> incidents,
		int page,
		int size,
		boolean hasMore) {

	public AssignedIncidentPageResponse {
		incidents = incidents == null ? List.of() : List.copyOf(incidents);
	}
}
