package es.uma.urbanpulse.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error legible devuelto por la API.")
public record ApiErrorResponse(String message, Map<String, String> errors) {
}
