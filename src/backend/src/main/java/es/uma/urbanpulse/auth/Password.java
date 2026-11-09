package es.uma.urbanpulse.auth;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A new password: at least 8 characters and at most BCrypt's 72 bytes, not made only of spaces, with no
 * composition rules (NIST 800-63B). It is checked once normalized (see {@link Passwords#normalize}). Login
 * never applies these rules, so a change here never locks out an existing account.
 */
@Target({ FIELD, PARAMETER, RECORD_COMPONENT })
@Retention(RUNTIME)
@Constraint(validatedBy = Password.Validator.class)
public @interface Password {

	int MIN_LENGTH = 8;

	String ONLY_SPACES_MESSAGE = "La contraseña no puede estar formada solo por espacios.";

	String message() default "La contraseña debe tener entre 8 y 72 caracteres. Las letras con tilde y la ñ cuentan como dos.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<Password, String> {

		@Override
		public boolean isValid(String password, ConstraintValidatorContext context) {
			if (password == null
					|| password.codePointCount(0, password.length()) < MIN_LENGTH
					|| Passwords.exceedsBcryptLimit(password)) {
				return false;
			}
			if (isOnlySpaces(password)) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(ONLY_SPACES_MESSAGE).addConstraintViolation();
				return false;
			}
			return true;
		}

		/** {@link String#isBlank()} misses no-break spaces, so Unicode space separators count too. */
		private static boolean isOnlySpaces(String password) {
			return password.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
		}

	}

}
