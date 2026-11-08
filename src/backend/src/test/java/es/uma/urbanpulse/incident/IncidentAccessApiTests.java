package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.OPERATOR_EMAIL;
import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_EMAIL;
import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.login;
import static es.uma.urbanpulse.TestAccounts.newCitizen;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.PostgisContainerConfiguration;
import es.uma.urbanpulse.auth.AuthResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentAccessApiTests {

	private static final Map<String, Object> NEW_INCIDENT = Map.of(
			"title", "Farola fundida",
			"description", "La farola lleva varias noches sin encenderse.",
			"latitude", 36.7196,
			"longitude", -4.4216);

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void newIncidentIsLinkedToTheCitizenWhoReportedIt() {
		AuthResponse citizen = newCitizen(this.client);

		IncidentResponse created = this.client.post()
			.uri("/api/incidents")
			.header("Authorization", bearer(citizen))
			.body(NEW_INCIDENT)
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(IncidentResponse.class)
			.returnResult()
			.getResponseBody();

		assertThat(created).isNotNull();
		// The API does not expose the reporter until "my incidents" (#67), so read it from the table.
		Integer reporterId = this.jdbcTemplate.queryForObject(
				"SELECT reporter_id FROM \"Incident\" WHERE id = ?", Integer.class, created.id());
		assertThat(reporterId).isEqualTo(citizen.user().id());
	}

	@Test
	void incidentResponsesDoNotExposeTheReporter() {
		AuthResponse citizen = newCitizen(this.client);
		String location = this.client.post()
			.uri("/api/incidents")
			.header("Authorization", bearer(citizen))
			.body(NEW_INCIDENT)
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.reporterId").doesNotExist()
			.jsonPath("$.reporter").doesNotExist()
			.returnResult()
			.getResponseHeaders()
			.getLocation()
			.toString();

		this.client.get()
			.uri(location)
			.header("Authorization", bearer(login(this.client, OPERATOR_EMAIL)))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.reporterId").doesNotExist()
			.jsonPath("$.reporter").doesNotExist();
	}

	@Test
	void staffCannotRegisterIncidents() {
		for (String staff : new String[] { OPERATOR_EMAIL, TECHNICIAN_EMAIL }) {
			this.client.post()
				.uri("/api/incidents")
				.header("Authorization", bearer(login(this.client, staff)))
				.body(NEW_INCIDENT)
				.exchange()
				.expectStatus()
				.isForbidden()
				.expectBody()
				.jsonPath("$.message").isEqualTo("No tienes permiso para hacer esto.");
		}
	}

	@Test
	void everyRoleListsIncidentsAndCategories() {
		for (AuthResponse user : new AuthResponse[] { newCitizen(this.client), login(this.client, OPERATOR_EMAIL),
				login(this.client, TECHNICIAN_EMAIL) }) {
			this.client.get().uri("/api/incidents").header("Authorization", bearer(user)).exchange()
				.expectStatus().isOk();
			this.client.get().uri("/api/categories").header("Authorization", bearer(user)).exchange()
				.expectStatus().isOk();
		}
	}

}
