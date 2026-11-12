package es.uma.urbanpulse.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Técnico al que se pueden asignar incidencias.")
public record TechnicianResponse(Integer id, String name) {

	public static TechnicianResponse from(User user) {
		return new TechnicianResponse(user.getId(), user.getName());
	}

}
