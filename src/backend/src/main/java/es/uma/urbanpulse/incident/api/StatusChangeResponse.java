package es.uma.urbanpulse.incident.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import es.uma.urbanpulse.incident.IncidentStatus;
import es.uma.urbanpulse.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Entrada del historial de estados. Nunca expone quién, solo su rol.")
public record StatusChangeResponse(
		@Schema(description = "Estado anterior. Nulo en la entrada inicial.", nullable = true)
		IncidentStatus previousStatus,
		IncidentStatus newStatus,
		@Schema(
				description = "Instante del cambio en UTC, en ISO-8601 con milisegundos y sufijo Z.",
				type = "string",
				format = "date-time",
				example = "2026-11-07T10:15:30.123Z")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
		Instant changedAt,
		@Schema(description = "Rol de quien hizo el cambio. Nulo en incidencias anteriores a la autenticación.",
				nullable = true)
		Role actorRole,
		@Schema(description = "Motivo del rechazo o nota de resolución.", nullable = true)
		String note) {
}
