package es.uma.urbanpulse.incident.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Datos para asignar una incidencia a un técnico.")
public record AssignIncidentRequest(
		@NotNull(message = "Elige un técnico.")
		@Positive(message = "Elige un técnico.")
		@Schema(description = "Identificador del técnico.", example = "7")
		Integer technicianId) {
}
