package es.uma.urbanpulse.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Usuario autenticado.")
public record UserResponse(Integer id, String name, String email, Role role) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
	}

}
