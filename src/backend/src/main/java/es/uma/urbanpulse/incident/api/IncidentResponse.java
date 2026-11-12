package es.uma.urbanpulse.incident.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import es.uma.urbanpulse.incident.IncidentPriority;
import es.uma.urbanpulse.incident.IncidentStatus;
import es.uma.urbanpulse.user.TechnicianResponse;
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
		Instant createdAt,
		@Schema(description = "Prioridad manual. Nula si no tiene prioridad.", nullable = true)
		IncidentPriority priority,
		@Schema(description = "Justificación de la prioridad vigente.", nullable = true)
		String priorityJustification,
		@Schema(description = "Técnico asignado. Solo lo ven operadores y técnicos: es nulo para ciudadanos y en el mapa.",
				nullable = true)
		TechnicianResponse assignee,
		@Schema(description = "Fotografías adjuntas a la incidencia (incluidas al consultar su detalle).")
		List<IncidentPhotoResponse> photos) {

	public IncidentResponse {
		photos = photos == null ? List.of() : List.copyOf(photos);
	}

	public IncidentResponse(
			Integer id,
			String title,
			String description,
			double latitude,
			double longitude,
			Integer categoryId,
			String categoryName,
			IncidentStatus status,
			Instant createdAt,
			IncidentPriority priority,
			String priorityJustification,
			TechnicianResponse assignee) {
		this(id, title, description, latitude, longitude, categoryId, categoryName, status, createdAt,
				priority, priorityJustification, assignee, List.of());
	}

	public IncidentResponse(
			Integer id,
			String title,
			String description,
			double latitude,
			double longitude,
			Integer categoryId,
			String categoryName,
			IncidentStatus status,
			Instant createdAt) {
		this(id, title, description, latitude, longitude, categoryId, categoryName, status, createdAt,
				null, null, null, List.of());
	}
}
