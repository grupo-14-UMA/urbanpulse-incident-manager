package es.uma.urbanpulse.user;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.uma.urbanpulse.api.ApiErrorResponse;
import es.uma.urbanpulse.auth.AuthTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Tag(name = "Usuarios", description = "Usuario actual y técnicos.")
public class UserController {

	static final int MAX_TECHNICIANS = 200;

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/api/users/me")
	@Operation(summary = "Consultar el usuario actual")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return userService.findCurrent(AuthTokens.subjectOf(jwt).userId());
	}

	@GetMapping("/api/technicians")
	@Operation(summary = "Listar técnicos por nombre (solo operadores)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Técnicos ordenados por nombre."),
			@ApiResponse(responseCode = "400", description = "El límite no es válido.",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Solo los operadores pueden listar técnicos.",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public List<TechnicianResponse> technicians(
			@RequestParam(defaultValue = "50")
			@Min(value = 1, message = "El límite debe estar entre 1 y 200.")
			@Max(value = MAX_TECHNICIANS, message = "El límite debe estar entre 1 y 200.")
			int limit) {
		return userService.findTechnicians(limit);
	}

}
