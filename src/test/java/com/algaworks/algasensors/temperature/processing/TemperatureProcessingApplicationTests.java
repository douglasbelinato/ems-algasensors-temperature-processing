package com.algaworks.algasensors.temperature.processing;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TemperatureProcessingApplicationTests {

	// No broker in tests: mock the AMQP beans so RabbitMQInitializer#init() is a no-op.
	@MockitoBean
	private RabbitTemplate rabbitTemplate;

	@MockitoBean
	private RabbitAdmin rabbitAdmin;

	@Test
	void contextLoads() {
	}

}
