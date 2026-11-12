package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.OPERATOR_EMAIL;
import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_EMAIL;
import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.login;
import static es.uma.urbanpulse.TestAccounts.newCitizen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import es.uma.urbanpulse.PostgisContainerConfiguration;
import es.uma.urbanpulse.auth.AuthResponse;
import es.uma.urbanpulse.incident.api.IncidentPhotoResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentPhotoApiTests {

	private static final byte[] SAMPLE_JPEG = new byte[] {
			(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, (byte) 0xFF,
			(byte) 0xD9
	};

	private static final byte[] SAMPLE_PNG = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
	};

	private static final byte[] SAMPLE_WEBP = new byte[] {
			'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
	};

	@Autowired
	private RestTestClient client;

	@Test
	void uploadsPhotosIncludesThemInIncidentDetailAndDownloadsThemBack() {
		AuthResponse citizen = newCitizen(this.client);
		IncidentResponse incident = createIncident(citizen);
		assertThat(incident.photos()).isEmpty();

		MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
		multipart.add("photos", filePart("farola.jpg", MediaType.IMAGE_JPEG, SAMPLE_JPEG));
		multipart.add("photos", filePart("poste.png", MediaType.IMAGE_PNG, SAMPLE_PNG));
		multipart.add("photos", filePart("acera.webp", MediaType.parseMediaType("image/webp"), SAMPLE_WEBP));

		List<IncidentPhotoResponse> uploaded = this.client.post()
				.uri("/api/incidents/{id}/photos", incident.id())
				.header("Authorization", bearer(citizen))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(multipart)
				.exchange()
				.expectStatus()
				.isCreated()
				.expectBody(new ParameterizedTypeReference<List<IncidentPhotoResponse>>() {
				})
				.returnResult()
				.getResponseBody();

		assertThat(uploaded).hasSize(3);
		assertThat(uploaded).extracting(IncidentPhotoResponse::contentType)
				.containsExactly("image/jpeg", "image/png", "image/webp");
		assertThat(uploaded).extracting(IncidentPhotoResponse::sizeBytes)
				.containsExactly((long) SAMPLE_JPEG.length, (long) SAMPLE_PNG.length, (long) SAMPLE_WEBP.length);

		IncidentResponse detail = this.client.get()
				.uri("/api/incidents/{id}", incident.id())
				.header("Authorization", bearer(citizen))
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(IncidentResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(detail).isNotNull();
		assertThat(detail.photos()).isEqualTo(uploaded);

		byte[] downloadedJpeg = this.client.get()
				.uri(uploaded.get(0).url())
				.header("Authorization", bearer(login(this.client, OPERATOR_EMAIL)))
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.contentType(MediaType.IMAGE_JPEG)
				.expectBody(byte[].class)
				.returnResult()
				.getResponseBody();

		assertThat(downloadedJpeg).isEqualTo(SAMPLE_JPEG);
	}

	@Test
	void aCitizenCannotDownloadAnotherCitizensIncidentPhoto() {
		AuthResponse reporter = newCitizen(this.client);
		AuthResponse otherCitizen = newCitizen(this.client);
		IncidentResponse incident = createIncident(reporter);
		MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
		multipart.add("photos", filePart("privada.jpg", MediaType.IMAGE_JPEG, SAMPLE_JPEG));

		List<IncidentPhotoResponse> uploaded = this.client.post()
			.uri("/api/incidents/{id}/photos", incident.id())
			.header("Authorization", bearer(reporter))
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(multipart)
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(new ParameterizedTypeReference<List<IncidentPhotoResponse>>() {
			})
			.returnResult()
			.getResponseBody();

		this.client.get()
			.uri(uploaded.get(0).url())
			.header("Authorization", bearer(otherCitizen))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	@Test
	void rejectsWrongFileTypesDisguisedFilesAndMissingOrMismatchedMimeTypes() {
		AuthResponse citizen = newCitizen(this.client);
		IncidentResponse incident = createIncident(citizen);
		byte[] textBytes = "not-an-image".getBytes(StandardCharsets.UTF_8);

		List<HttpEntity<ByteArrayResource>> invalidParts = List.of(
				filePart("archivo.txt", MediaType.TEXT_PLAIN, textBytes),
				filePart("falso.jpg", MediaType.IMAGE_JPEG, textBytes),
				filePart("binario.bin", MediaType.APPLICATION_OCTET_STREAM, SAMPLE_JPEG),
				filePart("incoherente.png", MediaType.IMAGE_PNG, SAMPLE_JPEG));

		for (HttpEntity<ByteArrayResource> part : invalidParts) {
			MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
			multipart.add("photos", part);

			this.client.post()
					.uri("/api/incidents/{id}/photos", incident.id())
					.header("Authorization", bearer(citizen))
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(multipart)
					.exchange()
					.expectStatus()
					.isBadRequest()
					.expectBody()
					.jsonPath("$.message").isEqualTo("Solo se permiten fotografías en formato JPEG, PNG o WebP.");
		}

		assertThatThrownBy(() -> PhotoFormat.validate(null, SAMPLE_JPEG))
				.hasMessage("Solo se permiten fotografías en formato JPEG, PNG o WebP.");
		assertThatThrownBy(() -> PhotoFormat.validate("   ", SAMPLE_JPEG))
				.hasMessage("Solo se permiten fotografías en formato JPEG, PNG o WebP.");
	}

	@Test
	void rejectsEmptyAndOversizedFiles() {
		AuthResponse citizen = newCitizen(this.client);
		IncidentResponse incident = createIncident(citizen);

		MultiValueMap<String, Object> emptyMultipart = new LinkedMultiValueMap<>();
		emptyMultipart.add("photos", filePart("vacia.jpg", MediaType.IMAGE_JPEG, new byte[0]));

		this.client.post()
				.uri("/api/incidents/{id}/photos", incident.id())
				.header("Authorization", bearer(citizen))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(emptyMultipart)
				.exchange()
				.expectStatus()
				.isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo("El archivo de la fotografía está vacío.");

		byte[] oversized = new byte[(int) PhotoFormat.MAX_PHOTO_SIZE_BYTES + 1];
		System.arraycopy(SAMPLE_JPEG, 0, oversized, 0, SAMPLE_JPEG.length);
		MultiValueMap<String, Object> oversizedMultipart = new LinkedMultiValueMap<>();
		oversizedMultipart.add("photos", filePart("grande.jpg", MediaType.IMAGE_JPEG, oversized));

		this.client.post()
				.uri("/api/incidents/{id}/photos", incident.id())
				.header("Authorization", bearer(citizen))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(oversizedMultipart)
				.exchange()
				.expectStatus()
				.isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo("Cada fotografía puede ocupar como máximo 5 MB.");
	}

	@Test
	void rejectsMoreThanThreePhotosPerIncident() {
		AuthResponse citizen = newCitizen(this.client);
		IncidentResponse incident = createIncident(citizen);

		MultiValueMap<String, Object> fourPhotos = new LinkedMultiValueMap<>();
		for (int i = 0; i < 4; i++) {
			fourPhotos.add("photos", filePart("foto-" + i + ".jpg", MediaType.IMAGE_JPEG, SAMPLE_JPEG));
		}

		this.client.post()
				.uri("/api/incidents/{id}/photos", incident.id())
				.header("Authorization", bearer(citizen))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(fourPhotos)
				.exchange()
				.expectStatus()
				.isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo("No se pueden adjuntar más de 3 fotografías a una incidencia.");
	}

	@Test
	void enforcesThreePhotoLimitUnderConcurrentUploads() throws Exception {
		AuthResponse citizen = newCitizen(this.client);
		IncidentResponse incident = createIncident(citizen);

		int threadCount = 5;
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<Integer>> tasks = new ArrayList<>(threadCount);

		for (int i = 0; i < threadCount; i++) {
			int index = i;
			tasks.add(() -> {
				MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
				multipart.add("photos", filePart("concurrent-" + index + ".jpg", MediaType.IMAGE_JPEG, SAMPLE_JPEG));
				ready.countDown();
				start.await(5, TimeUnit.SECONDS);
				return this.client.post()
						.uri("/api/incidents/{id}/photos", incident.id())
						.header("Authorization", bearer(citizen))
						.contentType(MediaType.MULTIPART_FORM_DATA)
						.body(multipart)
						.exchange()
						.returnResult()
						.getStatus()
						.value();
			});
		}

		try {
			List<Future<Integer>> futures = new ArrayList<>(threadCount);
			for (Callable<Integer> task : tasks) {
				futures.add(pool.submit(task));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Integer> statuses = new ArrayList<>(threadCount);
			for (Future<Integer> future : futures) {
				statuses.add(future.get(10, TimeUnit.SECONDS));
			}

			assertThat(statuses).containsExactlyInAnyOrder(201, 201, 201, 400, 400);
		}
		finally {
			pool.shutdownNow();
		}

		IncidentResponse detail = this.client.get()
				.uri("/api/incidents/{id}", incident.id())
				.header("Authorization", bearer(citizen))
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(IncidentResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(detail).isNotNull();
		assertThat(detail.photos()).hasSize(3);
	}

	@Test
	void technicianCanAttachResolutionPhoto() {
		AuthResponse citizen = newCitizen(this.client);
		IncidentResponse incident = createIncident(citizen);
		AuthResponse technician = login(this.client, TECHNICIAN_EMAIL);

		MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
		multipart.add("photos", filePart("reparacion.jpg", MediaType.IMAGE_JPEG, SAMPLE_JPEG));

		this.client.post()
				.uri("/api/incidents/{id}/photos", incident.id())
				.header("Authorization", bearer(technician))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(multipart)
				.exchange()
				.expectStatus()
				.isCreated();
	}

	@Test
	void forbidsOtherCitizensAndOperatorsFromUploadingPhotos() {
		AuthResponse reporter = newCitizen(this.client);
		AuthResponse otherCitizen = newCitizen(this.client);
		AuthResponse operator = login(this.client, OPERATOR_EMAIL);
		IncidentResponse incident = createIncident(reporter);

		for (AuthResponse unauthorized : List.of(otherCitizen, operator)) {
			MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
			multipart.add("photos", filePart("foto.jpg", MediaType.IMAGE_JPEG, SAMPLE_JPEG));

			this.client.post()
					.uri("/api/incidents/{id}/photos", incident.id())
					.header("Authorization", bearer(unauthorized))
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(multipart)
					.exchange()
					.expectStatus()
					.isForbidden()
					.expectBody()
					.jsonPath("$.message").isEqualTo("No tienes permiso para hacer esto.");
		}
	}

	@Test
	void fileSystemPhotoStorageSurvivesReinstantiationAndPreventsPathTraversal(@TempDir Path tempDir) {
		FileSystemPhotoStorage firstInstance = new FileSystemPhotoStorage(tempDir);
		firstInstance.store("incidents/10/sample.jpg", SAMPLE_JPEG, "image/jpeg");

		FileSystemPhotoStorage restartedInstance = new FileSystemPhotoStorage(tempDir);
		assertThat(restartedInstance.read("incidents/10/sample.jpg")).isEqualTo(SAMPLE_JPEG);

		assertThatThrownBy(() -> restartedInstance.store("../outside.jpg", SAMPLE_JPEG, "image/jpeg"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private IncidentResponse createIncident(AuthResponse citizen) {
		IncidentResponse created = this.client.post()
				.uri("/api/incidents")
				.header("Authorization", bearer(citizen))
				.body(Map.of(
						"title", "Farola fundida",
						"description", "La farola lleva varias noches sin encenderse.",
						"latitude", 36.7196,
						"longitude", -4.4216))
				.exchange()
				.expectStatus()
				.isCreated()
				.expectBody(IncidentResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(created).isNotNull();
		return created;
	}

	private static HttpEntity<ByteArrayResource> filePart(String filename, MediaType contentType, byte[] bytes) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		ByteArrayResource resource = new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
		return new HttpEntity<>(resource, headers);
	}

}
