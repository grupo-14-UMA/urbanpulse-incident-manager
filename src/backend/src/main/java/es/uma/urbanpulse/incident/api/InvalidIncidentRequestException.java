package es.uma.urbanpulse.incident.api;

/** A field of the request is not valid for reasons that only the service can check. */
public class InvalidIncidentRequestException extends RuntimeException {

	private final String field;
	private final String fieldMessage;

	public InvalidIncidentRequestException(String field, String fieldMessage) {
		super("La solicitud contiene datos no válidos.");
		this.field = field;
		this.fieldMessage = fieldMessage;
	}

	public String getField() {
		return field;
	}

	public String getFieldMessage() {
		return fieldMessage;
	}

}
