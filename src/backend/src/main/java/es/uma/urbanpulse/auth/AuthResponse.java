package es.uma.urbanpulse.auth;

import java.time.Instant;

import es.uma.urbanpulse.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acceso y usuario al que pertenece.")
public record AuthResponse(
		@Schema(description = "Token que se envía en la cabecera Authorization: Bearer <token>.")
		String token,
		@Schema(description = "Instante en que el token caduca.")
		Instant expiresAt,
		UserResponse user) {
}
