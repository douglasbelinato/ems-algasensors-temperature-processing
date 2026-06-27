package com.algaworks.algasensors.temperature.processing.api.controller;

import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TemperatureProcessingControllerIT {

    private static final String DATA_PATH = "/api/sensors/{id}/temperatures/data";

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Nested
    @DisplayName("POST /api/sensors/{id}/temperatures/data")
    class Data {

        @ParameterizedTest(name = "value=[{0}] -> 200")
        @DisplayName("should accept a numeric temperature and return 200 with an empty body")
        @ValueSource(strings = {"25.5", "0", "-10.7", "37", "100.0"})
        void shouldAcceptNumericTemperature(String body) {
            client.post().uri(DATA_PATH, TSID.fast().toString())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().isEmpty();
        }

        @ParameterizedTest(name = "value=[{0}] -> 400")
        @DisplayName("should return 400 when the body is blank or not a number")
        @ValueSource(strings = {"abc", "12,5", "1.2.3", "twelve", "   ", ""})
        void shouldRejectInvalidTemperature(String body) {
            client.post().uri(DATA_PATH, TSID.fast().toString())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("should return 415 when the content type is not text/plain")
        void shouldRejectUnsupportedMediaType() {
            client.post().uri(DATA_PATH, TSID.fast().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("25.5")
                    .exchange()
                    .expectStatus().isEqualTo(415);
        }

        @Test
        @DisplayName("should return 400 when the sensor id in the path is not a valid TSID")
        void shouldRejectInvalidSensorId() {
            client.post().uri(DATA_PATH, "not-a-valid-tsid")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("25.5")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}
