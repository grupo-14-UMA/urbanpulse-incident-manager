package es.uma.urbanpulse.incident.api;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uma.urbanpulse.api.ApiErrorResponse;
import es.uma.urbanpulse.incident.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/incidents")
@Tag(name = "Incidencias", description = "Registro y consulta de incidencias urbanas.")
public class IncidentController {

	private final IncidentService incidentService;

	public IncidentController(IncidentService incidentService) {
		this.incidentService = incidentService;
	}

	@PostMapping
	@Operation(summary = "Registrar una incidencia")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Incidencia creada."),
			@ApiResponse(
					responseCode = "400",
					description = "La solicitud contiene datos no válidos.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
		IncidentResponse created = incidentService.create(request);
		return ResponseEntity.created(URI.create("/api/incidents/" + created.id())).body(created);
	}

	@GetMapping
	@Operation(
			summary = "Listar incidencias del área visible del mapa",
			description = "Devuelve hasta 500 incidencias REPORTED dentro del rectángulo WGS 84 indicado. "
					+ "Si truncated es true, acerca el mapa para ver más incidencias."
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Incidencias encontradas en el área, con máximo de 500.",
					content = @Content(
							mediaType = "application/json",
							examples = @ExampleObject(value = "{\"incidents\": [], \"truncated\": false}"))),
			@ApiResponse(
					responseCode = "400",
					description = "Los límites del área no son válidos.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentMapResponse findWithinBounds(
			@Valid @ParameterObject @ModelAttribute IncidentBounds bounds) {
		return incidentService.findWithinBounds(bounds);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Consultar una incidencia por identificador")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Incidencia encontrada."),
			@ApiResponse(
					responseCode = "400",
					description = "El identificador no es válido.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "404",
					description = "La incidencia no existe.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentResponse findById(@PathVariable Integer id) {
		return incidentService.findById(id);
	}
}
