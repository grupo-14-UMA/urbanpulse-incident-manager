package es.uma.urbanpulse.incident.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fotografía adjunta a una incidencia.")
public record IncidentPhotoResponse(
		Integer id,
		@Schema(description = "Ruta relativa de la API para descargar la fotografía.", example = "/api/incidents/1/photos/1")
		String url,
		@Schema(description = "Tipo MIME de la fotografía.", example = "image/jpeg")
		String contentType,
		@Schema(description = "Tamaño del archivo en bytes.", example = "184320")
		long sizeBytes,
		@Schema(
				description = "Fecha de subida en UTC, en ISO-8601 con milisegundos y sufijo Z.",
				type = "string",
				format = "date-time",
				example = "2026-11-07T10:15:30.123Z")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
		Instant createdAt) {
}
