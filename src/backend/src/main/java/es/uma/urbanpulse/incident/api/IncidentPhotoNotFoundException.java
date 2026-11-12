package es.uma.urbanpulse.incident.api;

public class IncidentPhotoNotFoundException extends RuntimeException {

	public IncidentPhotoNotFoundException(Integer incidentId, Integer photoId) {
		super("No existe una fotografía con identificador " + photoId + " en la incidencia " + incidentId + ".");
	}

}
