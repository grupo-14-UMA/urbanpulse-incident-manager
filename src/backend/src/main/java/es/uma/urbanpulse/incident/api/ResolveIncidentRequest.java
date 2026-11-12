package es.uma.urbanpulse.incident.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para resolver una incidencia.")
public record ResolveIncidentRequest(
		@NotBlank(message = "La nota de resolución es obligatoria.")
		@Size(max = 1000, message = "La nota de resolución no puede superar los 1000 caracteres.")
		@Schema(description = "Nota de resolución.", example = "Bombilla sustituida.")
		String note) {
}
