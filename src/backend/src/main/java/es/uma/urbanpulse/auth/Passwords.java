package es.uma.urbanpulse.auth;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/** Rules every password follows, both when it is created and when it is compared at login. */
final class Passwords {

	/** BCrypt only reads this many bytes; anything after them is silently ignored. */
	static final int MAX_BYTES = 72;

	private Passwords() {
	}

	/**
	 * Unicode NFC, so an accented letter matches whether the keyboard sends it composed or decomposed.
	 * Never trimmed: a password is compared exactly as typed.
	 */
	static String normalize(String password) {
		return password == null ? null : Normalizer.normalize(password, Normalizer.Form.NFC);
	}

	static boolean exceedsBcryptLimit(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}

}
