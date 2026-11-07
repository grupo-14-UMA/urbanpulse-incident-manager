package es.uma.urbanpulse.incident.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para registrar una incidencia.")
public record CreateIncidentRequest(
		@NotBlank(message = "El título es obligatorio.")
		@Size(max = 255, message = "El título no puede superar los 255 caracteres.")
		@Schema(description = "Título breve de la incidencia.", example = "Farola fundida")
		String title,

		@NotBlank(message = "La descripción es obligatoria.")
		@Schema(description = "Descripción del problema.", example = "La farola lleva varias noches sin encenderse.")
		String description,

		@NotNull(message = "La latitud es obligatoria.")
		@DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90.")
		@DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90.")
		@Schema(description = "Latitud WGS 84, entre -90 y 90.", example = "36.7196")
		Double latitude,

		@NotNull(message = "La longitud es obligatoria.")
		@DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180.")
		@DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180.")
		@Schema(description = "Longitud WGS 84, entre -180 y 180.", example = "-4.4216")
		Double longitude,

		@Positive(message = "El identificador de categoría debe ser positivo.")
		@Schema(description = "Identificador de categoría. Es opcional.", example = "1", nullable = true)
		Integer categoryId) {
}
