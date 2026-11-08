package es.uma.urbanpulse.auth;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A new password: between 8 and 72 characters, with no composition rules (NIST 800-63B). 72 is
 * BCrypt's limit in bytes, so a password with multi-byte characters must also fit in 72 bytes.
 */
@Target({ FIELD, PARAMETER, RECORD_COMPONENT })
@Retention(RUNTIME)
@Constraint(validatedBy = Password.Validator.class)
public @interface Password {

	int MIN_LENGTH = 8;

	int MAX_BYTES = 72;

	String message() default "La contraseña debe tener entre 8 y 72 caracteres.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<Password, String> {

		@Override
		public boolean isValid(String password, ConstraintValidatorContext context) {
			return password != null
					&& password.codePointCount(0, password.length()) >= MIN_LENGTH
					&& password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
		}

	}

}
