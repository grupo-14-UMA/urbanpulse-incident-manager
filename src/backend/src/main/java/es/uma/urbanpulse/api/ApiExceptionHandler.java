package es.uma.urbanpulse.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import es.uma.urbanpulse.auth.EmailAlreadyRegisteredException;
import es.uma.urbanpulse.auth.InvalidCredentialsException;
import es.uma.urbanpulse.auth.InvalidSessionException;
import es.uma.urbanpulse.incident.api.IncidentNotFoundException;
import es.uma.urbanpulse.incident.api.IncidentPhotoNotFoundException;
import es.uma.urbanpulse.incident.api.InvalidCategoryException;
import es.uma.urbanpulse.incident.api.InvalidIncidentPhotoException;

@RestControllerAdvice
public class ApiExceptionHandler {

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

	@ExceptionHandler({ InvalidCategoryException.class, InvalidIncidentPhotoException.class })
	public ResponseEntity<ApiErrorResponse> handleBadRequestDomainException(RuntimeException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded() {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("Cada fotografía puede ocupar como máximo 5 MB.", Map.of()));
	}

	@ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
	public ResponseEntity<ApiErrorResponse> handleMultipartError() {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("Debes adjuntar al menos una fotografía.", Map.of()));
	}

	@ExceptionHandler({ IncidentNotFoundException.class, IncidentPhotoNotFoundException.class })
	public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException exception) {
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
