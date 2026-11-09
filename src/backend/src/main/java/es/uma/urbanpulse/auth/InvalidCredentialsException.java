package es.uma.urbanpulse.auth;

/** Wrong email or wrong password: the message does not say which one. */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("El email o la contraseña no son correctos.");
	}

}
