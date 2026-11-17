package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.newCitizen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import es.uma.urbanpulse.incident.api.CitizenIncidentPageResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;

/** The paginated list of the incidents a citizen reported, every status included. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentCitizenQueryApiTests {

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private LifecycleDriver flow;

	@BeforeEach
	void newFlow() {
		this.flow = new LifecycleDriver(this.client);
	}

	@Test
	void aCitizenReadsOnlyTheirOwnIncidentsWhateverUserIdTheRequestCarries() {
		LifecycleDriver colleagueFlow = new LifecycleDriver(this.client);
		List<Integer> own = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			own.add(this.flow.report());
		}
		int colleaguesIncident = colleagueFlow.report();
		Integer colleaguesUserId = colleagueFlow.citizen.user().id();

		CitizenIncidentPageResponse page = mine(this.flow.citizen,
				"/api/incidents/mine?page=0&size=20&userId=" + colleaguesUserId);
		assertThat(page.incidents())
			.extracting(IncidentResponse::id)
			.containsExactlyInAnyOrderElementsOf(own)
			.doesNotContain(colleaguesIncident);
	}

	@Test
	void rejectedAndResolvedIncidentsAreListedToo() {
		int rejected = this.flow.rejected();
		int resolved = this.flow.resolved();

		CitizenIncidentPageResponse page = mine(this.flow.citizen, "/api/incidents/mine?page=0&size=20");
		assertThat(page.incidents())
			.extracting(IncidentResponse::id, IncidentResponse::status)
			.contains(tuple(rejected, IncidentStatus.REJECTED), tuple(resolved, IncidentStatus.RESOLVED));
	}

	@Test
	void incidentsComeNewestFirstWithIdAsTheTieBreak() {
		for (int i = 0; i < 5; i++) {
			this.flow.report();
		}

		List<IncidentResponse> incidents = mine(this.flow.citizen, "/api/incidents/mine?page=0&size=20")
			.incidents();
		assertThat(incidents).hasSize(5);
		for (int i = 1; i < incidents.size(); i++) {
			Instant previous = incidents.get(i - 1).createdAt();
			Instant current = incidents.get(i).createdAt();
			if (previous.equals(current)) {
				assertThat(incidents.get(i - 1).id()).isGreaterThan(incidents.get(i).id());
			}
			else {
				assertThat(previous).isAfter(current);
			}
		}
	}

	@Test
	void incidentsReportedAtTheSameInstantAreOrderedByIdDescending() {
		int first = this.flow.report();
		int second = this.flow.report();
		this.jdbcTemplate.update(
				"UPDATE \"Incident\" SET \"created_at\" = ? WHERE \"id\" IN (?, ?)",
				Timestamp.from(Instant.parse("2026-11-15T10:00:00.123Z")), first, second);

		List<IncidentResponse> incidents = mine(this.flow.citizen, "/api/incidents/mine?page=0&size=20")
			.incidents();
		assertThat(incidents).extracting(IncidentResponse::id).containsExactly(second, first);
	}

	@Test
	void theFirstPageHoldsAtMostTwentyAndTheRestArrivesThroughHasMore() {
		for (int i = 0; i < 21; i++) {
			this.flow.report();
		}

		CitizenIncidentPageResponse first = mine(this.flow.citizen, "/api/incidents/mine?page=0&size=20");
		assertThat(first.page()).isZero();
		assertThat(first.size()).isEqualTo(20);
		assertThat(first.incidents()).hasSize(20);
		assertThat(first.hasMore()).isTrue();

		CitizenIncidentPageResponse second = mine(this.flow.citizen, "/api/incidents/mine?page=1&size=20");
		assertThat(second.page()).isEqualTo(1);
		assertThat(second.incidents()).hasSize(1);
		assertThat(second.hasMore()).isFalse();
		assertThat(first.incidents())
			.extracting(IncidentResponse::id)
			.doesNotContainAnyElementsOf(second.incidents().stream().map(IncidentResponse::id).toList());
	}

	@Test
	void theRequestedSizeCanNeverExceedFifty() {
		this.client.get()
			.uri("/api/incidents/mine?page=0&size=500")
			.header("Authorization", bearer(this.flow.citizen))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors.size").isEqualTo("El tamaño debe estar entre 1 y 50.");

		this.client.get()
			.uri("/api/incidents/mine?page=0&size=50")
			.header("Authorization", bearer(this.flow.citizen))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.size").isEqualTo(50);
	}

	@Test
	void staffIsRejectedFromTheCitizenList() {
		for (AuthResponse staff : new AuthResponse[] { this.flow.operator, this.flow.technician }) {
			this.client.get()
				.uri("/api/incidents/mine")
				.header("Authorization", bearer(staff))
				.exchange()
				.expectStatus()
				.isForbidden()
				.expectBody()
				.jsonPath("$.message").isEqualTo("No tienes permiso para hacer esto.");
		}
	}

	@Test
	void aCitizenWithoutIncidentsReceivesAnEmptyPage() {
		CitizenIncidentPageResponse page = mine(this.flow.citizen, "/api/incidents/mine?page=0&size=20");

		assertThat(page.incidents()).isEmpty();
		assertThat(page.hasMore()).isFalse();
	}

	@Test
	void anotherCitizenIsRejectedFromTheDetailAndTheHistory() {
		int id = this.flow.report();
		AuthResponse stranger = newCitizen(this.client);

		this.client.get()
			.uri("/api/incidents/{id}", id)
			.header("Authorization", bearer(stranger))
			.exchange()
			.expectStatus()
			.isForbidden();
		this.client.get()
			.uri("/api/incidents/{id}/history", id)
			.header("Authorization", bearer(stranger))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

	private CitizenIncidentPageResponse mine(AuthResponse as, String uri) {
		return this.client.get()
			.uri(uri)
			.header("Authorization", bearer(as))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(CitizenIncidentPageResponse.class)
			.returnResult()
			.getResponseBody();
	}

}
