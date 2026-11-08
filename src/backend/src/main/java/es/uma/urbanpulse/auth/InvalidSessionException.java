package es.uma.urbanpulse.auth;

/** The request has no valid token, or its user no longer exists. */
public class InvalidSessionException extends RuntimeException {

	public static final String MESSAGE = "Tu sesión no es válida o ha caducado. Inicia sesión de nuevo.";

	public InvalidSessionException() {
		super(MESSAGE);
	}

}
