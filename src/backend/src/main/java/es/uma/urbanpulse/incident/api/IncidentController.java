package es.uma.urbanpulse.incident.api;

import java.net.URI;
import java.util.List;

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
		IncidentResponse created = incidentService.create(request, AuthTokens.subjectOf(jwt).userId());
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

	@PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
			summary = "Adjuntar fotografías a una incidencia (ciudadano autor)",
			description = "Permite al ciudadano que registró la incidencia adjuntar hasta 3 fotografías en total (JPEG, PNG o WebP, máximo 5 MB cada una).")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Fotografías almacenadas."),
			@ApiResponse(
					responseCode = "400",
					description = "El tipo, tamaño o número de fotografías no es válido.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "403",
					description = "Sin permiso para adjuntar fotografías a esta incidencia.",
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
	public ResponseEntity<List<IncidentPhotoResponse>> uploadPhotos(
			@PathVariable Integer id,
			@RequestParam(name = "photos", required = false) List<MultipartFile> photos,
			@AuthenticationPrincipal Jwt jwt) {
		List<IncidentPhotoResponse> uploaded = incidentService.uploadPhotos(id, photos, AuthTokens.subjectOf(jwt));
		return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
	}

	@GetMapping("/{id}/photos/{photoId}")
	@Operation(summary = "Descargar una fotografía de una incidencia")
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Contenido binario de la fotografía.",
					content = {
							@Content(mediaType = "image/jpeg"),
							@Content(mediaType = "image/png"),
							@Content(mediaType = "image/webp")
					}),
			@ApiResponse(
					responseCode = "404",
					description = "La incidencia o la fotografía no existe.",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<byte[]> downloadPhoto(
			@PathVariable Integer id,
			@PathVariable Integer photoId) {
		StoredPhotoContent photo = incidentService.loadPhoto(id, photoId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(photo.contentType()))
				.contentLength(photo.content().length)
				.cacheControl(CacheControl.noCache().mustRevalidate())
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
				.header("X-Content-Type-Options", "nosniff")
				.body(photo.content());
	}

}
