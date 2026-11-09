package es.uma.urbanpulse.user;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"User\"")
public class User {

	/** Spaces at either end, Unicode ones included: {@link String#strip()} misses the no-break space. */
	private static final Pattern SURROUNDING_SPACES = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "email", nullable = false, length = 254, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 60)
	private String passwordHash;

	@Convert(converter = RoleConverter.class)
	@Column(name = "role_id", nullable = false)
	private Role role;

	protected User() {
	}

	public User(String name, String email, String passwordHash, Role role) {
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.email = normalizeEmail(email);
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		this.role = Objects.requireNonNull(role, "role must not be null");
	}

	/**
	 * Emails are compared case-insensitively, so they are always stored in lower case, and without the
	 * spaces a keyboard may add around them.
	 */
	public static String normalizeEmail(String email) {
		Objects.requireNonNull(email, "email must not be null");
		return SURROUNDING_SPACES.matcher(email).replaceAll("").toLowerCase(Locale.ROOT);
	}

	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

}
