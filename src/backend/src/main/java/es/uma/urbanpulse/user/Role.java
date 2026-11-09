package es.uma.urbanpulse.user;

/**
 * Role of a {@link User}, which decides what they can do. The database id matches the seeded
 * {@code Role} table.
 */
public enum Role {

	CITIZEN(1),
	OPERATOR(2),
	TECHNICIAN(3);

	private final int id;

	Role(int id) {
		this.id = id;
	}

	int getId() {
		return id;
	}

	static Role fromId(int id) {
		for (Role role : values()) {
			if (role.id == id) {
				return role;
			}
		}
		throw new IllegalArgumentException("Unknown role id: " + id);
	}

}
