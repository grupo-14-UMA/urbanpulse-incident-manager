package es.uma.urbanpulse.incident.api;

import es.uma.urbanpulse.incident.IncidentPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos opcionales al validar una incidencia.")
public record ValidateIncidentRequest(
		@Schema(description = "Prioridad. Es opcional.", nullable = true)
		IncidentPriority priority,

		@Size(max = 1000, message = "La justificación no puede superar los 1000 caracteres.")
		@Schema(description = "Justificación de la prioridad. Solo con prioridad.", nullable = true)
		String priorityJustification) {

	public static final ValidateIncidentRequest EMPTY = new ValidateIncidentRequest(null, null);

}
