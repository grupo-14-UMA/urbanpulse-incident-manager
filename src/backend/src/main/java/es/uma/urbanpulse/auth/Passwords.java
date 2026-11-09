package es.uma.urbanpulse.auth;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * The password rules. Every password is normalized and checked against BCrypt's limit, both at sign-up
 * and at login. The new-password rules ({@link #isAcceptableLength}, {@link #isOnlySpaces}) only apply at
 * sign-up, through {@link Password}: login only compares, so changing them never locks out an account.
 */
final class Passwords {

	static final int MIN_LENGTH = 8;

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

	static boolean isAcceptableLength(String password) {
		return password.codePointCount(0, password.length()) >= MIN_LENGTH && !exceedsBcryptLimit(password);
	}

	/** {@link String#isBlank()} misses no-break spaces, so Unicode space separators count too. */
	static boolean isOnlySpaces(String password) {
		return password.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
	}

}
