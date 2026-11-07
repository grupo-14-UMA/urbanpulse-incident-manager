package es.uma.urbanpulse.category;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Categoría disponible para clasificar una incidencia.")
public record CategoryResponse(Integer id, String name) {

	static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getName());
	}
}
