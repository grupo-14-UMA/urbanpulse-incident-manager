package es.uma.urbanpulse.incident.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Filters and keyset pagination for the incident list response. */
@Schema(description = "Filtros, orden y cursor de la lista de incidencias.")
public class IncidentQueryParameters extends IncidentQueryFilters {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	@Min(value = 1, message = "El tamaño de página debe ser positivo.")
	@Max(value = MAX_PAGE_SIZE, message = "El tamaño máximo de página es 100.")
	private int size = DEFAULT_PAGE_SIZE;
	@Pattern(regexp = "createdAt|priority", message = "Ordena por createdAt o priority.")
	private String sortBy = "createdAt";
	@Pattern(regexp = "asc|desc", message = "La dirección debe ser asc o desc.")
	private String sortDirection = "desc";
	@Schema(description = "Cursor opaco devuelto por la página anterior.")
	private String cursor;

	@AssertTrue(message = "El cursor no es válido para el orden solicitado.")
	@Schema(hidden = true)
	public boolean isCursorValid() {
		if (cursor == null) {
			return true;
		}
		try {
			return IncidentCursor.decode(cursor).matches(sortBy, sortDirection);
		}
		catch (IllegalArgumentException exception) {
			return false;
		}
	}

	public int getSize() { return size; }
	public void setSize(int size) { this.size = size; }
	public String getSortBy() { return sortBy; }
	public void setSortBy(String sortBy) { this.sortBy = sortBy; }
	public String getSortDirection() { return sortDirection; }
	public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
	public String getCursor() { return cursor; }
	public void setCursor(String cursor) { this.cursor = cursor; }
	public IncidentCursor cursorPosition() {
		return cursor == null ? null : IncidentCursor.decode(cursor);
	}
}
