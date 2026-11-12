package es.uma.urbanpulse.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import es.uma.urbanpulse.auth.EmailAlreadyRegisteredException;
import es.uma.urbanpulse.auth.InvalidCredentialsException;
import es.uma.urbanpulse.auth.InvalidSessionException;
import es.uma.urbanpulse.incident.InvalidStatusTransitionException;
import es.uma.urbanpulse.incident.api.IncidentAccessDeniedException;
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.InvalidIncidentRequestException;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
	private static final String INVALID_REQUEST_MESSAGE = "La solicitud contiene datos no válidos.";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				errors.putIfAbsent(error.getField(),
						error.isBindingFailure() ? "Formato no válido." : error.getDefaultMessage()));
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(INVALID_REQUEST_MESSAGE, errors));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors()
				.forEach(error -> errors.putIfAbsent(
						result.getMethodParameter().getParameterName(), error.getDefaultMessage())));
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(INVALID_REQUEST_MESSAGE, errors));
	}

	@ExceptionHandler(InvalidCategoryException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidCategory(InvalidCategoryException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(IncidentNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleIncidentNotFound(IncidentNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler({ InvalidCredentialsException.class, InvalidSessionException.class })
	public ResponseEntity<ApiErrorResponse> handleUnauthorized(RuntimeException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableBody() {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("El cuerpo de la solicitud no contiene JSON válido.", Map.of()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
		String parameter = exception.getName();
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(
						"El parámetro «" + parameter + "» debe tener un valor válido.",
						Map.of(parameter, "Formato no válido.")));
	}

	@ExceptionHandler(InvalidIncidentRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidIncidentRequest(InvalidIncidentRequestException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(exception.getMessage(),
						Map.of(exception.getField(), exception.getFieldMessage())));
	}

	@ExceptionHandler(IncidentAccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleIncidentAccessDenied(IncidentAccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ApiErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(InvalidStatusTransitionException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidTransition(InvalidStatusTransitionException exception) {
		LOGGER.warn("Incident {} rejected an action: status {} does not allow it",
				exception.getIncidentId(), exception.getCurrentStatus());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiErrorResponse> handleConcurrentChange(OptimisticLockingFailureException exception) {
		Object incidentId = exception instanceof ObjectOptimisticLockingFailureException locking
				? locking.getIdentifier() : null;
		LOGGER.warn("Concurrent change rejected on incident {}", incidentId);
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse(
						"Otra persona ha cambiado esta incidencia. Recárgala e inténtalo de nuevo.", Map.of()));
	}
}
