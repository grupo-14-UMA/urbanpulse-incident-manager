package es.uma.urbanpulse.incident.api;

import java.net.URI;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import es.uma.urbanpulse.api.ApiErrorResponse;
import es.uma.urbanpulse.auth.AuthTokens;
import es.uma.urbanpulse.incident.IncidentService;
import es.uma.urbanpulse.incident.StoredPhotoContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping("/api/incidents")
@Tag(name = "Incidencias", description = "Registro y consulta de incidencias urbanas.")
public class IncidentController {

	private static final Set<String> MAP_BOUNDS_PARAMETERS = Set.of("west", "south", "east", "north");

	private static final String INVALID_DATA = "La solicitud contiene datos no válidos.";
	private static final String NOT_ALLOWED = "No tienes permiso para hacer esto.";
	private static final String NOT_FOUND = "La incidencia no existe.";
	private static final String CONFLICT = "El estado actual no admite la acción, o la incidencia ha cambiado.";

	private final IncidentService incidentService;

	public IncidentController(IncidentService incidentService) {
		this.incidentService = incidentService;
	}

	@PostMapping
	@Operation(summary = "Registrar una incidencia (solo ciudadanos)",
			description = "El ciudadano que la registra se toma del token, nunca del cuerpo.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Incidencia creada."),
			@ApiResponse(
					responseCode = "400",
					description = "La solicitud contiene datos no válidos.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "403",
					description = "Solo los ciudadanos pueden registrar incidencias.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		IncidentResponse created = incidentService.create(request, AuthTokens.subjectOf(jwt));
		return ResponseEntity.created(URI.create("/api/incidents/" + created.id())).body(created);
	}

	@GetMapping
	@Operation(summary = "Listar incidencias con filtros y cursor",
			description = "Devuelve hasta 100 incidencias. Operadores ven todas; ciudadanos solo las propias; técnicos solo las asignadas. "
					+ "Usa nextCursor para avanzar sin degradación en páginas profundas. Los límites west, south, east y north se envían a /api/incidents/map.")
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Página de incidencias con cursor de continuación."),
			@ApiResponse(
					responseCode = "400",
					description = "Los filtros o el cursor no son válidos, o se enviaron límites del mapa a la ruta de lista.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentPageResponse list(
			@Valid @ParameterObject @ModelAttribute IncidentQueryParameters query,
			HttpServletRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		if (request.getParameterMap().keySet().stream().anyMatch(MAP_BOUNDS_PARAMETERS::contains)) {
			throw new InvalidIncidentRequestException("mapBounds",
					"Los límites west, south, east y north solo se admiten en /api/incidents/map.");
		}
		return incidentService.findPage(query, AuthTokens.subjectOf(jwt));
	}

	@GetMapping("/map")
	@Operation(summary = "Consultar las incidencias del área visible del mapa",
			description = "Devuelve hasta 500 incidencias activas dentro del rectángulo WGS 84. "
					+ "Admite filtros de estado, categoría, prioridad y fecha; truncated indica si hay más resultados.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Incidencias encontradas en el área del mapa."),
			@ApiResponse(responseCode = "400", description = "Los límites del área no son válidos.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentMapResponse map(
			@Valid @ParameterObject @ModelAttribute IncidentMapQueryParameters query,
			@AuthenticationPrincipal Jwt jwt) {
		return incidentService.findWithinBounds(query, AuthTokens.subjectOf(jwt));
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
	public IncidentResponse findById(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		return incidentService.findById(id, AuthTokens.subjectOf(jwt));
	}

	@GetMapping("/assigned")
	@Operation(summary = "Listar trabajos asignados al técnico actual")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Trabajos asignados abiertos."),
			@ApiResponse(responseCode = "403", description = "Solo los técnicos pueden consultar sus trabajos.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public AssignedIncidentPageResponse assigned(
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa.") int page,
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño debe estar entre 1 y 50.")
			@Max(value = 50, message = "El tamaño debe estar entre 1 y 50.") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return incidentService.findAssigned(AuthTokens.subjectOf(jwt), page, size);
	}

	@GetMapping("/mine")
	@Operation(summary = "Listar las incidencias registradas por el ciudadano actual",
			description = "Devuelve todas las incidencias del ciudadano del token, incluidas las rechazadas y resueltas, "
					+ "de la más reciente a la más antigua. El ciudadano se toma siempre del token; no se acepta userId.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Página de incidencias del ciudadano."),
			@ApiResponse(responseCode = "403", description = "Solo los ciudadanos pueden consultar sus incidencias.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public CitizenIncidentPageResponse mine(
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa.") int page,
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño debe estar entre 1 y 50.")
			@Max(value = 50, message = "El tamaño debe estar entre 1 y 50.") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return incidentService.findMine(AuthTokens.subjectOf(jwt), page, size);
	}

	@PostMapping("/{id}/validate")
	@Operation(summary = "Validar una incidencia (solo operadores)",
			description = "REPORTED pasa a VALIDATED. Puede fijar la prioridad en el mismo paso. "
					+ "El cuerpo es opcional.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Incidencia validada."),
			@ApiResponse(responseCode = "400", description = INVALID_DATA, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = NOT_ALLOWED, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = CONFLICT, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentResponse validate(@PathVariable Integer id,
			@Valid @RequestBody(required = false) ValidateIncidentRequest request, @AuthenticationPrincipal Jwt jwt) {
		return incidentService.validate(id, request == null ? ValidateIncidentRequest.EMPTY : request,
				AuthTokens.subjectOf(jwt));
	}

	@PostMapping("/{id}/reject")
	@Operation(summary = "Rechazar una incidencia (solo operadores)",
			description = "REPORTED pasa a REJECTED, un estado final. El motivo es obligatorio.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Incidencia rechazada."),
			@ApiResponse(responseCode = "400", description = INVALID_DATA, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = NOT_ALLOWED, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = CONFLICT, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentResponse reject(@PathVariable Integer id, @Valid @RequestBody RejectIncidentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return incidentService.reject(id, request, AuthTokens.subjectOf(jwt));
	}

	@PostMapping("/{id}/assign")
	@Operation(summary = "Asignar una incidencia a un técnico (solo operadores)",
			description = "VALIDATED pasa a ASSIGNED. No hay reasignación.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Incidencia asignada."),
			@ApiResponse(responseCode = "400", description = INVALID_DATA, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = NOT_ALLOWED, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = CONFLICT, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentResponse assign(@PathVariable Integer id, @Valid @RequestBody AssignIncidentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return incidentService.assign(id, request, AuthTokens.subjectOf(jwt));
	}

	@PutMapping("/{id}/priority")
	@Operation(summary = "Cambiar la prioridad de una incidencia (solo operadores)",
			description = "Solo en VALIDATED, ASSIGNED o IN_PROGRESS. No cambia el estado ni escribe en el historial. "
					+ "La justificación sustituye a la anterior.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Prioridad cambiada."),
			@ApiResponse(responseCode = "400", description = INVALID_DATA, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = NOT_ALLOWED, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = CONFLICT, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentResponse changePriority(@PathVariable Integer id,
			@Valid @RequestBody ChangePriorityRequest request, @AuthenticationPrincipal Jwt jwt) {
		return incidentService.changePriority(id, request, AuthTokens.subjectOf(jwt));
	}

	@PostMapping("/{id}/start")
	@Operation(summary = "Empezar a trabajar en una incidencia (solo el técnico asignado)",
			description = "ASSIGNED pasa a IN_PROGRESS.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Incidencia en curso."),
			@ApiResponse(responseCode = "403", description = NOT_ALLOWED, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = CONFLICT, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentResponse start(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		return incidentService.start(id, AuthTokens.subjectOf(jwt));
	}

	@PostMapping("/{id}/resolve")
	@Operation(summary = "Resolver una incidencia (solo el técnico asignado)",
			description = "IN_PROGRESS pasa a RESOLVED, un estado final. La nota de resolución es obligatoria.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Incidencia resuelta."),
			@ApiResponse(responseCode = "400", description = INVALID_DATA, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = NOT_ALLOWED, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = CONFLICT, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public IncidentResponse resolve(@PathVariable Integer id, @Valid @RequestBody ResolveIncidentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return incidentService.resolve(id, request, AuthTokens.subjectOf(jwt));
	}

	@GetMapping("/{id}/history")
	@Operation(summary = "Leer el historial de estados de una incidencia",
			description = "Devuelve como máximo los 5 cambios más recientes, del más antiguo al más reciente. "
					+ "Lo leen los operadores, el técnico asignado y el ciudadano que la registró. "
					+ "Indica el rol de quien hizo cada cambio, nunca su nombre ni su identificador.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Historial de la incidencia."),
			@ApiResponse(responseCode = "403", description = NOT_ALLOWED, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public List<StatusChangeResponse> history(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		return incidentService.findHistory(id, AuthTokens.subjectOf(jwt));
	}

	@PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
			summary = "Adjuntar fotografías a una incidencia (ciudadano autor o técnico)",
			description = "Permite al ciudadano que registró la incidencia o a un técnico adjuntar hasta 3 fotografías en total (JPEG, PNG o WebP, máximo 5 MB cada una).")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Fotografías almacenadas."),
			@ApiResponse(responseCode = "400", description = "El tipo, tamaño o número de fotografías no es válido.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Sin permiso para adjuntar fotografías a esta incidencia.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "La incidencia no existe.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<IncidentPhotoResponse>> uploadPhotos(
			@PathVariable Integer id,
			@RequestParam(name = "photos", required = false) List<MultipartFile> photos,
			@AuthenticationPrincipal Jwt jwt) {
		List<IncidentPhotoResponse> uploaded = incidentService.uploadPhotos(id, photos, AuthTokens.subjectOf(jwt));
		return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
	}

	@GetMapping("/{id}/photos/{photoId}")
	@Operation(summary = "Descargar una fotografía de una incidencia",
			description = "Los ciudadanos solo pueden descargar fotografías de sus incidencias.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Contenido binario de la fotografía.",
					content = {@Content(mediaType = "image/jpeg"), @Content(mediaType = "image/png"),
							@Content(mediaType = "image/webp")}),
			@ApiResponse(responseCode = "404", description = "La incidencia o la fotografía no existe.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<byte[]> downloadPhoto(
			@PathVariable Integer id,
			@PathVariable Integer photoId,
			@AuthenticationPrincipal Jwt jwt) {
		StoredPhotoContent photo = incidentService.loadPhoto(id, photoId, AuthTokens.subjectOf(jwt));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(photo.contentType()))
				.contentLength(photo.content().length)
				.cacheControl(CacheControl.noCache().mustRevalidate())
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
				.header("X-Content-Type-Options", "nosniff")
				.body(photo.content());
	}

}
