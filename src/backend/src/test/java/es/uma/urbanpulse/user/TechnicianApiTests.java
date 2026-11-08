package es.uma.urbanpulse.user;

import static es.uma.urbanpulse.TestAccounts.OPERATOR_EMAIL;
import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_EMAIL;
import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.login;
import static es.uma.urbanpulse.TestAccounts.newCitizen;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.PostgisContainerConfiguration;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class TechnicianApiTests {

	@Autowired
	private RestTestClient client;

	@Test
	void operatorListsTechniciansOrderedByName() {
		this.client.get()
			.uri("/api/technicians")
			.header("Authorization", bearer(login(this.client, OPERATOR_EMAIL)))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(2)
			.jsonPath("$[0].name").isEqualTo("Javier Ruiz")
			.jsonPath("$[1].name").isEqualTo("Lucía Martín")
			.jsonPath("$[0].email").doesNotExist();
	}

	@Test
	void technicianListRespectsTheLimit() {
		this.client.get()
			.uri("/api/technicians?limit=1")
			.header("Authorization", bearer(login(this.client, OPERATOR_EMAIL)))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(1)
			.jsonPath("$[0].name").isEqualTo("Javier Ruiz");
	}

	@Test
	void limitAboveTheMaximumIsRejected() {
		this.client.get()
			.uri("/api/technicians?limit=201")
			.header("Authorization", bearer(login(this.client, OPERATOR_EMAIL)))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.message").isEqualTo("La solicitud contiene datos no válidos.")
			.jsonPath("$.errors.limit").isEqualTo("El límite debe estar entre 1 y 200.");
	}

	@Test
	void citizensAndTechniciansCannotListTechnicians() {
		this.client.get()
			.uri("/api/technicians")
			.header("Authorization", bearer(newCitizen(this.client)))
			.exchange()
			.expectStatus()
			.isForbidden()
			.expectBody()
			.jsonPath("$.message").isEqualTo("No tienes permiso para realizar esta acción.");
		this.client.get()
			.uri("/api/technicians")
			.header("Authorization", bearer(login(this.client, TECHNICIAN_EMAIL)))
			.exchange()
			.expectStatus()
			.isForbidden();
	}

}
