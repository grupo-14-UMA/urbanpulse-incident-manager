package es.uma.urbanpulse.incident.api;

import java.time.LocalDateTime;

import es.uma.urbanpulse.incident.IncidentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Incidencia devuelta por la API.")
public record IncidentResponse(
		Integer id,
		String title,
		String description,
		double latitude,
		double longitude,
		Integer categoryId,
		String categoryName,
		@Schema(description = "Código del estado. REPORTED se muestra al ciudadano como Registrada.", example = "REPORTED")
		IncidentStatus status,
		LocalDateTime createdAt) {
}
