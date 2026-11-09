package es.uma.urbanpulse.auth;

import es.uma.urbanpulse.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear una cuenta de ciudadano.")
public record RegisterRequest(
		@NotBlank(message = "El nombre es obligatorio.")
		@Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
		@Schema(example = "Ana García")
		String name,

		@NotBlank(message = "El email es obligatorio.")
		@Email(message = "El email no tiene un formato válido.")
		@Size(max = 254, message = "El email no puede superar los 254 caracteres.")
		@Schema(example = "ana@mail.com")
		String email,

		@Password
		@Schema(description = "Entre 8 caracteres y 72 bytes, y no solo espacios. Se envía tal como la escribe el usuario, sin recortar.", example = "contraseña-segura")
		String password) {

	public RegisterRequest {
		// Normalized before validation: mobile keyboards often add a space after the email.
		email = email == null ? null : User.normalizeEmail(email);
		password = Passwords.normalize(password);
	}

}
