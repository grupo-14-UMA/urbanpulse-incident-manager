package es.uma.urbanpulse.incident.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Incidencias visibles para el mapa, con indicación de límite alcanzado.")
public record IncidentMapResponse(
		List<IncidentResponse> incidents,
		@Schema(description = "Indica que existen más incidencias en el área; acerca el mapa para verlas.")
		boolean truncated) {
}
