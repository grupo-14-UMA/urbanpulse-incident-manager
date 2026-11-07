package es.uma.urbanpulse.incident.api;

public class IncidentNotFoundException extends RuntimeException {

	public IncidentNotFoundException(Integer id) {
		super("No existe una incidencia con identificador " + id + ".");
	}
}
