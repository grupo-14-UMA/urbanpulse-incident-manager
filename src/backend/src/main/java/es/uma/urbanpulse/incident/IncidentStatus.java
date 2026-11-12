package es.uma.urbanpulse.incident;

import java.util.List;

/**
 * Status of an incident. The database id matches the seeded {@code Status} table.
 *
 * <p>The transitions between statuses are enforced by {@link Incident}.</p>
 */
public enum IncidentStatus {

	REPORTED(1, "Registrada"),
	VALIDATED(2, "Validada"),
	REJECTED(3, "Rechazada"),
	ASSIGNED(4, "Asignada"),
	IN_PROGRESS(5, "En curso"),
	RESOLVED(6, "Resuelta");

	/** Statuses shown on the map: the incident is still open. */
	public static final List<IncidentStatus> ACTIVE = List.of(REPORTED, VALIDATED, ASSIGNED, IN_PROGRESS);

	private final int id;
	private final String label;

	IncidentStatus(int id, String label) {
		this.id = id;
		this.label = label;
	}

	int getId() {
		return id;
	}

	/** Spanish label shown to users. */
	public String getLabel() {
		return label;
	}

	static IncidentStatus fromId(int id) {
		for (IncidentStatus status : values()) {
			if (status.id == id) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown incident status id: " + id);
	}

	static List<Integer> activeIds() {
		return ACTIVE.stream().map(IncidentStatus::getId).toList();
	}

}
