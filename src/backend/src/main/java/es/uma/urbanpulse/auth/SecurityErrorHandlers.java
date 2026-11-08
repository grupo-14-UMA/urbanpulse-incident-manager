package es.uma.urbanpulse.auth;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import es.uma.urbanpulse.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/** Writes 401 and 403 responses with the common {@link ApiErrorResponse} contract. */
@Component
class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	static final String FORBIDDEN_MESSAGE = "No tienes permiso para hacer esto.";

	private static final String ROLE_PREFIX = "ROLE_";

	private static final Logger log = LoggerFactory.getLogger(SecurityErrorHandlers.class);

	private final ObjectMapper objectMapper;

	SecurityErrorHandlers(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		log.debug("Unauthenticated request: {} {}", request.getMethod(), request.getRequestURI());
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(response, HttpStatus.UNAUTHORIZED, InvalidSessionException.MESSAGE);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException exception) throws IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		log.warn("Forbidden request: {} {}, userId={}, role={}", request.getMethod(), request.getRequestURI(),
				authentication == null ? null : authentication.getName(), roleOf(authentication));
		write(response, HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
	}

	private static String roleOf(Authentication authentication) {
		if (authentication == null) {
			return null;
		}
		return authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.filter((authority) -> authority.startsWith(ROLE_PREFIX))
			.map((authority) -> authority.substring(ROLE_PREFIX.length()))
			.findFirst()
			.orElse(null);
	}

	private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(message, Map.of()));
	}

}
