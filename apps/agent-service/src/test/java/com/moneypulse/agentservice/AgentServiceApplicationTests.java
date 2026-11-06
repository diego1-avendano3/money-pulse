package com.moneypulse.agentservice;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Tracer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AgentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void tracingBridgeIsWired(@Autowired Tracer tracer) {
        assertThat(tracer).isNotNull();
    }

    @Test
    void healthEndpointReportsUp(@LocalServerPort int port) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        var body = JsonMapper.shared().readTree(response.body());
        assertThat(body.get("status").asString()).isEqualTo("UP");
    }
}
