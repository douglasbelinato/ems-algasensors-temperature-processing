package com.algaworks.algasensors.temperature.processing.api.controller;

import com.algaworks.algasensors.temperature.processing.api.model.TemperatureLogOutput;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import org.mockito.ArgumentCaptor;

import static com.algaworks.algasensors.temperature.processing.infrastructure.rabbitmq.RabbitMQConfig.FANOUT_EXCHANGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TemperatureProcessingControllerIT {

    private static final String DATA_PATH = "/api/sensors/{id}/temperatures/data";

    @LocalServerPort
    private int port;

    // No broker in tests: mock the AMQP beans. RabbitTemplate is also used to assert the publish.
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private RabbitAdmin rabbitAdmin;

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

        @Test
        @DisplayName("should publish the reading to the fanout exchange with the sensorId header")
        void shouldPublishTemperatureReading() {
            TSID sensorId = TSID.fast();

            client.post().uri(DATA_PATH, sensorId.toString())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("25.5")
                    .exchange()
                    .expectStatus().isOk();

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<MessagePostProcessor> mppCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
            verify(rabbitTemplate).convertAndSend(
                    eq(FANOUT_EXCHANGE_NAME), eq(""), payloadCaptor.capture(), mppCaptor.capture());

            assertThat(payloadCaptor.getValue()).isInstanceOf(TemperatureLogOutput.class);
            TemperatureLogOutput payload = (TemperatureLogOutput) payloadCaptor.getValue();
            assertThat(payload.getSensorId()).isEqualTo(sensorId);
            assertThat(payload.getValue()).isEqualTo(25.5);
            assertThat(payload.getId()).isNotNull();
            assertThat(payload.getRegisteredAt()).isNotNull();

            // The MessagePostProcessor must stamp the sensorId header onto the outgoing message.
            Message processed = mppCaptor.getValue()
                    .postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertThat((String) processed.getMessageProperties().getHeader("sensorId"))
                    .isEqualTo(sensorId.toString());
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

            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("should return 415 when the content type is not text/plain")
        void shouldRejectUnsupportedMediaType() {
            client.post().uri(DATA_PATH, TSID.fast().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("25.5")
                    .exchange()
                    .expectStatus().isEqualTo(415);

            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("should return 400 when the sensor id in the path is not a valid TSID")
        void shouldRejectInvalidSensorId() {
            client.post().uri(DATA_PATH, "not-a-valid-tsid")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("25.5")
                    .exchange()
                    .expectStatus().isBadRequest();

            verifyNoInteractions(rabbitTemplate);
        }
    }
}
