package es.uma.urbanpulse.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales para iniciar sesión.")
public record LoginRequest(
		@NotBlank(message = "El email es obligatorio.")
		@Schema(example = "operador@urbanpulse.test")
		String email,

		@NotBlank(message = "La contraseña es obligatoria.")
		@Schema(example = "urbanpulse-dev")
		String password) {
}
