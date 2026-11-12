package es.uma.urbanpulse.incident.api;

/** The caller has the right role but the incident is not theirs. */
public class IncidentAccessDeniedException extends RuntimeException {

	public IncidentAccessDeniedException() {
		super("No tienes permiso para hacer esto.");
	}

}
