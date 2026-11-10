package es.uma.urbanpulse.incident.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Límites WGS 84 del área visible del mapa.")
public record IncidentBounds(
		@NotNull(message = "El límite oeste es obligatorio.")
		@DecimalMin(value = "-180", message = "La longitud oeste debe estar entre -180 y 180.")
		@DecimalMax(value = "180", message = "La longitud oeste debe estar entre -180 y 180.")
		@Schema(description = "Longitud mínima del área visible.", example = "-4.45")
		Double west,

		@NotNull(message = "El límite sur es obligatorio.")
		@DecimalMin(value = "-90", message = "La latitud sur debe estar entre -90 y 90.")
		@DecimalMax(value = "90", message = "La latitud sur debe estar entre -90 y 90.")
		@Schema(description = "Latitud mínima del área visible.", example = "36.68")
		Double south,

		@NotNull(message = "El límite este es obligatorio.")
		@DecimalMin(value = "-180", message = "La longitud este debe estar entre -180 y 180.")
		@DecimalMax(value = "180", message = "La longitud este debe estar entre -180 y 180.")
		@Schema(description = "Longitud máxima del área visible.", example = "-4.38")
		Double east,

		@NotNull(message = "El límite norte es obligatorio.")
		@DecimalMin(value = "-90", message = "La latitud norte debe estar entre -90 y 90.")
		@DecimalMax(value = "90", message = "La latitud norte debe estar entre -90 y 90.")
		@Schema(description = "Latitud máxima del área visible.", example = "36.77")
		Double north) {

	@AssertTrue(message = "Los límites deben ser finitos y formar un rectángulo con oeste menor que este y sur menor que norte.")
	@Schema(hidden = true)
	public boolean isOrdered() {
		return west == null || south == null || east == null || north == null
				|| (Double.isFinite(west) && Double.isFinite(south)
						&& Double.isFinite(east) && Double.isFinite(north)
						&& west < east && south < north);
	}
}
