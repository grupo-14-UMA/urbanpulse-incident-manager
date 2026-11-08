package es.uma.urbanpulse.auth;

/** Masks an email for the logs: {@code ana@mail.com} becomes {@code a***@mail.com}. */
final class EmailMask {

	private EmailMask() {
	}

	static String mask(String email) {
		if (email == null) {
			return "***";
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

}
