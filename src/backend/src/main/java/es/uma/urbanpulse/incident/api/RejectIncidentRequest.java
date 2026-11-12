package es.uma.urbanpulse.incident.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para rechazar una incidencia.")
public record RejectIncidentRequest(
		@NotBlank(message = "El motivo del rechazo es obligatorio.")
		@Size(max = 1000, message = "El motivo del rechazo no puede superar los 1000 caracteres.")
		@Schema(description = "Motivo del rechazo.", example = "Duplicada de otra incidencia.")
		String reason) {
}
