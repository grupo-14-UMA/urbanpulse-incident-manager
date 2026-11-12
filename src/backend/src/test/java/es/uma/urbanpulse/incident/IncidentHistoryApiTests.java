package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_2_EMAIL;
import static es.uma.urbanpulse.TestAccounts.login;
import static es.uma.urbanpulse.TestAccounts.newCitizen;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.PostgisContainerConfiguration;
import es.uma.urbanpulse.auth.AuthResponse;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentHistoryApiTests {

	@Autowired
	private RestTestClient client;

	private LifecycleDriver flow;

	@BeforeEach
	void newFlow() {
		this.flow = new LifecycleDriver(this.client);
	}

	@Test
	void registeringAnIncidentWritesTheInitialEntryByTheCitizen() {
		int id = this.flow.report();

		this.flow.history(id, this.flow.citizen).expectStatus().isOk().expectBody()
			.jsonPath("$.length()").isEqualTo(1)
			.jsonPath("$[0].previousStatus").isEmpty()
			.jsonPath("$[0].newStatus").isEqualTo("REPORTED")
			.jsonPath("$[0].actorRole").isEqualTo("CITIZEN")
			.jsonPath("$[0].note").isEmpty()
			.jsonPath("$[0].changedAt").value(String.class, at -> org.assertj.core.api.Assertions.assertThat(at)
				.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));
	}

	@Test
	void entriesComeOldestFirst() {
		int id = this.flow.resolved();

		this.flow.history(id, this.flow.operator).expectBody()
			.jsonPath("$.length()").isEqualTo(5)
			.jsonPath("$[0].newStatus").isEqualTo("REPORTED")
			.jsonPath("$[1].newStatus").isEqualTo("VALIDATED")
			.jsonPath("$[2].newStatus").isEqualTo("ASSIGNED")
			.jsonPath("$[3].newStatus").isEqualTo("IN_PROGRESS")
			.jsonPath("$[4].newStatus").isEqualTo("RESOLVED");
	}

	@Test
	void operatorReadsAnyHistory() {
		this.flow.history(this.flow.report(), this.flow.operator).expectStatus().isOk();
		this.flow.history(this.flow.rejected(), this.flow.operator).expectStatus().isOk();
	}

	@Test
	void onlyTheAssignedTechnicianReadsTheHistory() {
		int id = this.flow.assigned();
		AuthResponse other = login(this.client, TECHNICIAN_2_EMAIL);

		this.flow.history(id, this.flow.technician).expectStatus().isOk();
		this.flow.history(id, other).expectStatus().isForbidden()
			.expectBody().jsonPath("$.message").isEqualTo("No tienes permiso para hacer esto.");
		// Not assigned to anyone yet.
		this.flow.history(this.flow.validated(), this.flow.technician).expectStatus().isForbidden();
	}

	@Test
	void onlyTheReporterReadsTheHistoryAndSeesTheRejectionReason() {
		int id = this.flow.report();
		this.flow.post(id, "reject", this.flow.operator, Map.of("reason", "Duplicada."));

		this.flow.history(id, this.flow.citizen).expectStatus().isOk().expectBody()
			.jsonPath("$[1].note").isEqualTo("Duplicada.");
		this.flow.history(id, newCitizen(this.client)).expectStatus().isForbidden();
	}

	@Test
	void historyNeverExposesNamesOrUserIds() {
		int id = this.flow.resolved();

		for (AuthResponse reader : new AuthResponse[] { this.flow.citizen, this.flow.operator,
				this.flow.technician }) {
			this.flow.history(id, reader).expectBody()
				.jsonPath("$[*].changedBy").doesNotExist()
				.jsonPath("$[*].changedById").doesNotExist()
				.jsonPath("$[*].actor").doesNotExist()
				.jsonPath("$[*].actorId").doesNotExist()
				.jsonPath("$[*].actorName").doesNotExist()
				.jsonPath("$[*].name").doesNotExist();
		}
	}

}
