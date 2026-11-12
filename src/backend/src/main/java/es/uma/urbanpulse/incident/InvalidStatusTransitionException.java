package es.uma.urbanpulse.incident;

/** The action is not allowed from the current status of the incident. */
public class InvalidStatusTransitionException extends RuntimeException {

	private final Integer incidentId;
	private final IncidentStatus currentStatus;

	public InvalidStatusTransitionException(Integer incidentId, IncidentStatus currentStatus) {
		super("La incidencia está «" + currentStatus.getLabel() + "» y no admite esta acción.");
		this.incidentId = incidentId;
		this.currentStatus = currentStatus;
	}

	public Integer getIncidentId() {
		return incidentId;
	}

	public IncidentStatus getCurrentStatus() {
		return currentStatus;
	}

}
