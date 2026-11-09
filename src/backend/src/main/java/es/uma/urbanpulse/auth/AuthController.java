package es.uma.urbanpulse.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.uma.urbanpulse.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Registro de ciudadanos, inicio de sesión y renovación del token.")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirements
	@Operation(summary = "Crear una cuenta de ciudadano e iniciar sesión")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Cuenta creada."),
			@ApiResponse(responseCode = "400", description = "La solicitud contiene datos no válidos.",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Ya existe una cuenta con este email.",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Iniciar sesión")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Sesión iniciada."),
			@ApiResponse(responseCode = "401", description = "El email o la contraseña no son correctos.",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Renovar un token todavía válido por otro de 7 días")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Token renovado."),
			@ApiResponse(responseCode = "401", description = "El token no es válido o ha caducado.",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public AuthResponse refresh(@AuthenticationPrincipal Jwt jwt) {
		return authService.refresh(AuthTokens.subjectOf(jwt));
	}

}
