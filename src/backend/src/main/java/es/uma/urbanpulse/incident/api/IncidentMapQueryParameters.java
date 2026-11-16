package es.uma.urbanpulse.incident.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

/** Spatial bounds and filters for the map response. */
@Schema(description = "Límites WGS 84 y filtros del área visible del mapa.")
public class IncidentMapQueryParameters extends IncidentQueryFilters {

	@Schema(description = "Longitud oeste WGS 84.", example = "-4.45")
	private Double west;
	@Schema(description = "Latitud sur WGS 84.", example = "36.68")
	private Double south;
	@Schema(description = "Longitud este WGS 84.", example = "-4.38")
	private Double east;
	@Schema(description = "Latitud norte WGS 84.", example = "36.77")
	private Double north;

	public IncidentBounds toBounds() {
		return new IncidentBounds(west, south, east, north);
	}

	@AssertTrue(message = "Indica los cuatro límites del mapa y forma un rectángulo WGS 84 válido.")
	@Schema(hidden = true)
	public boolean isBoundsValid() {
		return west != null && south != null && east != null && north != null
				&& Double.isFinite(west) && Double.isFinite(south)
				&& Double.isFinite(east) && Double.isFinite(north)
				&& west >= -180 && west <= 180 && east >= -180 && east <= 180
				&& south >= -90 && south <= 90 && north >= -90 && north <= 90
				&& west < east && south < north;
	}

	public Double getWest() { return west; }
	public void setWest(Double west) { this.west = west; }
	public Double getSouth() { return south; }
	public void setSouth(Double south) { this.south = south; }
	public Double getEast() { return east; }
	public void setEast(Double east) { this.east = east; }
	public Double getNorth() { return north; }
	public void setNorth(Double north) { this.north = north; }
}
