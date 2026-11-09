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
 * A new password, checked once normalized: the new-password rules of {@link Passwords}, with no
 * composition rules (NIST 800-63B).
 */
@Target({ FIELD, PARAMETER, RECORD_COMPONENT })
@Retention(RUNTIME)
@Constraint(validatedBy = Password.Validator.class)
public @interface Password {

	String ONLY_SPACES_MESSAGE = "La contraseña no puede estar formada solo por espacios.";

	String MIN_LENGTH_MESSAGE = "La contraseña debe tener al menos 8 caracteres.";

	String TOO_LONG_MESSAGE = "La contraseña es demasiado larga. Elige una más corta.";

	String message() default MIN_LENGTH_MESSAGE;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<Password, String> {

		@Override
		public boolean isValid(String password, ConstraintValidatorContext context) {
			if (password == null || !Passwords.hasMinimumLength(password)) {
				return false;
			}
			if (Passwords.exceedsBcryptLimit(password)) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(TOO_LONG_MESSAGE).addConstraintViolation();
				return false;
			}
			if (Passwords.isOnlySpaces(password)) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(ONLY_SPACES_MESSAGE).addConstraintViolation();
				return false;
			}
			return true;
		}

	}

}
