package es.uma.urbanpulse.incident;

/**
 * Status values available to the first incident persistence slice.
 *
 * <p>The database id stays aligned with the future {@code Status} lookup table. The MVP only
 * needs the initial status, so it stores the code on {@code Incident}.</p>
 */
public enum IncidentStatus {

	REPORTED(1);

	private final int id;

	IncidentStatus(int id) {
		this.id = id;
	}

	int getId() {
		return id;
	}

	static IncidentStatus fromId(int id) {
		for (IncidentStatus status : values()) {
			if (status.id == id) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown incident status id: " + id);
	}

}
