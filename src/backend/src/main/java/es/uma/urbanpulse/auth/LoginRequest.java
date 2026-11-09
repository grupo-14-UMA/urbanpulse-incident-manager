package es.uma.urbanpulse.auth;

import es.uma.urbanpulse.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "Credenciales para iniciar sesión.")
public record LoginRequest(
		@NotBlank(message = "El email es obligatorio.")
		@Schema(example = "operador@urbanpulse.test")
		String email,

		// Only presence: login compares, it never applies the new-password rules of @Password.
		@NotEmpty(message = "La contraseña es obligatoria.")
		@Schema(example = "urbanpulse-dev")
		String password) {

	public LoginRequest {
		// Normalized before validation: mobile keyboards often add a space after the email.
		email = email == null ? null : User.normalizeEmail(email);
		password = Passwords.normalize(password);
	}

}
