package es.uma.urbanpulse.incident.api;

import es.uma.urbanpulse.incident.IncidentPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para cambiar la prioridad de una incidencia.")
public record ChangePriorityRequest(
		@NotNull(message = "La prioridad es obligatoria.")
		IncidentPriority priority,

		@Size(max = 1000, message = "La justificación no puede superar los 1000 caracteres.")
		@Schema(description = "Justificación. Sustituye a la anterior.", nullable = true)
		String justification) {
}
