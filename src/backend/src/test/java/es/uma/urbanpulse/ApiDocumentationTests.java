package es.uma.urbanpulse;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class ApiDocumentationTests {

	@Autowired
	private RestTestClient client;

	@Test
	void servesTheOpenApiDocument() {
		this.client.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
	}

	@Test
	void servesSwaggerUi() {
		URI swaggerUiPage = this.client.get()
			.uri("/swagger-ui.html")
			.exchange()
			.expectStatus()
			.is3xxRedirection()
			.returnResult()
			.getResponseHeaders()
			.getLocation();

		this.client.get()
			.uri(swaggerUiPage)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value((page) -> assertThat(page).contains("Swagger UI"));
	}

}
