package com.kaska.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end check: create a link, follow it, read the stats back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedirectFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createRedirectAndReadStats() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "demo-key-akshaya");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<Map> created = restTemplate.postForEntity(
                url("/api/v1/links"),
                new HttpEntity<>(Map.of("url", "https://akshayapatra.org/donate"), headers),
                Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String code = (String) created.getBody().get("code");

        ResponseEntity<Void> redirect = restTemplate.exchange(
                url("/" + code), HttpMethod.GET, HttpEntity.EMPTY, Void.class);
        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation())
                .hasToString("https://akshayapatra.org/donate");
    }

    @Test
    void rejectsMissingApiKey() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/v1/links"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
