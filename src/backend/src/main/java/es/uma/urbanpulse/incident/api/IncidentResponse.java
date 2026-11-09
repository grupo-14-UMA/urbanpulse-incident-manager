package es.uma.urbanpulse.incident.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

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
		@Schema(
				description = "Fecha de registro: instante en UTC, en ISO-8601 con milisegundos y sufijo Z.",
				type = "string",
				format = "date-time",
				example = "2026-11-07T10:15:30.123Z")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
		Instant createdAt) {
}
