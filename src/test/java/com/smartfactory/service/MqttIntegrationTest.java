package com.smartfactory.service;

import com.smartfactory.entity.Machine;
import com.smartfactory.entity.MachineStatus;
import com.smartfactory.entity.SensorReading;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.SensorReadingRepository;
import com.smartfactory.repository.SensorRepository;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class MqttIntegrationTest {

    @Autowired
    private MessageHandler mqttInboundHandler;

    @Autowired
    private SensorReadingRepository readingRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private MachineRepository machineRepository;

    private Machine machine;

    @BeforeEach
    void setUp() {
        readingRepository.deleteAll();
        sensorRepository.deleteAll();
        machineRepository.deleteAll();

        machine = machineRepository.save(new Machine(
                "Tour Numérique",
                "CNC-024",
                "Tour",
                "zone-1",
                MachineStatus.RUNNING,
                "Machine test",
                List.of()
        ));
    }

    @Test
    @DisplayName("US16: Message handler receives MQTT message, unpacks payload, and processes reading")
    void testMqttHandlerSuccess() {
        String topic = "factory/machines/CNC-024/temperature";
        String payload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 46.8,
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;

        GenericMessage<byte[]> message = new GenericMessage<>(
                payload.getBytes(StandardCharsets.UTF_8),
                Map.of(org.springframework.integration.mqtt.support.MqttHeaders.RECEIVED_TOPIC, topic)
        );

        mqttInboundHandler.handleMessage(message);

        List<SensorReading> readings = readingRepository.findAll();
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).getValue()).isEqualTo(46.8);
    }

    @Test
    @DisplayName("US16: Message handler does not throw or crash subscriber when payload is invalid")
    void testMqttHandlerErrorResilience() {
        String topic = "factory/machines/CNC-024/temperature";
        String invalidPayload = "{ corrupt json ";

        GenericMessage<byte[]> message = new GenericMessage<>(
                invalidPayload.getBytes(StandardCharsets.UTF_8),
                Map.of(org.springframework.integration.mqtt.support.MqttHeaders.RECEIVED_TOPIC, topic)
        );

        // Must not throw exception
        assertThatCode(() -> mqttInboundHandler.handleMessage(message))
                .doesNotThrowAnyException();

        // Database remains unaffected
        assertThat(readingRepository.findAll()).isEmpty();
    }
}
