package es.uma.urbanpulse.api;

import java.util.LinkedHashMap;
import java.util.Map;

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
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("La solicitud contiene datos no válidos.", errors));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors()
				.forEach(error -> errors.putIfAbsent(
						result.getMethodParameter().getParameterName(), error.getDefaultMessage())));
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("La solicitud contiene datos no válidos.", errors));
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
}
