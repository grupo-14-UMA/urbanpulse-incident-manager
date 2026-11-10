package es.uma.urbanpulse;

import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.newCitizen;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.incident.api.IncidentMapResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentTimestampsTests {

	private static final String UTC_INSTANT_WITH_MILLIS = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$";

	@Autowired
	private RestTestClient client;

	@Test
	void createdAtIsTheSameUtcInstantWithMillisOnEveryEndpoint() {
		String authorization = bearer(newCitizen(this.client));

		Map<String, Object> created = this.client.post()
			.uri("/api/incidents")
			.header("Authorization", authorization)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Farola fundida", "description", "Lleva varias noches apagada.", "latitude",
					36.7196, "longitude", -4.4216))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.returnResult()
			.getResponseBody();

		Object createdAt = created.get("createdAt");
		assertThat(createdAt).asString().matches(UTC_INSTANT_WITH_MILLIS);

		Map<String, Object> found = this.client.get()
			.uri("/api/incidents/{id}", created.get("id"))
			.header("Authorization", authorization)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.returnResult()
			.getResponseBody();

		assertThat(found.get("createdAt")).isEqualTo(createdAt);

		IncidentMapResponse listed = this.client.get()
			.uri("/api/incidents?west=-4.5&south=36.6&east=-4.3&north=36.8")
			.header("Authorization", authorization)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(IncidentMapResponse.class)
			.returnResult()
			.getResponseBody();

		assertThat(listed.incidents()).filteredOn((incident) -> incident.id().equals(created.get("id")))
			.singleElement()
			.extracting(IncidentResponse::createdAt)
			.isEqualTo(Instant.parse(createdAt.toString()));
	}

}
