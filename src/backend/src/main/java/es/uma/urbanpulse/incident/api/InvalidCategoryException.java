package es.uma.urbanpulse.incident.api;

public class InvalidCategoryException extends RuntimeException {

	public InvalidCategoryException(Integer id) {
		super("No existe una categoría con identificador " + id + ".");
	}
}
