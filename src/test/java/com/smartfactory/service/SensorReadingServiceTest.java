package com.smartfactory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.entity.*;
import com.smartfactory.repository.AlertRepository;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.SensorReadingRepository;
import com.smartfactory.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SensorReadingServiceTest {

    @Autowired
    private SensorReadingService readingService;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository readingRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Machine machine;

    @BeforeEach
    void setUp() {
        readingRepository.deleteAll();
        sensorRepository.deleteAll();
        alertRepository.deleteAll();
        machineRepository.deleteAll();

        machine = machineRepository.save(new Machine(
                "Fraiseuse CNC",
                "CNC-024",
                "Fraiseuse",
                "zone-1",
                MachineStatus.RUNNING,
                "Machine principale d'usinage",
                List.of()
        ));
    }

    @Test
    @DisplayName("US16 & US17 & US18: Valid MQTT message is validated, persisted, and sensor is updated")
    void testValidateAndProcessSuccess() {
        String topic = "factory/machines/CNC-024/temperature";
        String payload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 45.2,
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;

        SensorReading reading = readingService.validateAndProcess(topic, payload);

        assertThat(reading).isNotNull();
        assertThat(reading.getId()).isNotNull();
        assertThat(reading.getMachineId()).isEqualTo(machine.getId());
        assertThat(reading.getSensorType()).isEqualTo(SensorType.TEMPERATURE);
        assertThat(reading.getValue()).isEqualTo(45.2);
        assertThat(reading.getUnit()).isEqualTo("°C");
        assertThat(reading.getTimestamp()).isEqualTo(Instant.parse("2026-09-28T10:15:30.123Z"));

        // Verify sensor was provisioned and marked ONLINE
        Sensor sensor = sensorRepository.findByMachineIdAndType(machine.getId(), SensorType.TEMPERATURE).orElseThrow();
        assertThat(sensor.getStatus()).isEqualTo(SensorStatus.ONLINE);
        assertThat(sensor.getLastSeen()).isNotNull();

        // Verify reading in MongoDB
        List<SensorReading> stored = readingRepository.findAll();
        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("US16: Message resolved by internal MongoDB machine ID")
    void testValidateAndProcessByInternalMachineId() {
        String topic = "factory/machines/" + machine.getId() + "/vibration";
        String payload = String.format("""
                {
                  "machineId": "%s",
                  "sensorType": "VIBRATION",
                  "value": 2.15,
                  "unit": "mm/s",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """, machine.getId());

        SensorReading reading = readingService.validateAndProcess(topic, payload);

        assertThat(reading).isNotNull();
        assertThat(reading.getMachineId()).isEqualTo(machine.getId());
        assertThat(reading.getSensorType()).isEqualTo(SensorType.VIBRATION);
    }

    @Test
    @DisplayName("US17: Unknown machine is rejected")
    void testRejectUnknownMachine() {
        String topic = "factory/machines/UNKNOWN-999/temperature";
        String payload = """
                {
                  "machineId": "UNKNOWN-999",
                  "sensorType": "TEMPERATURE",
                  "value": 50.0,
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;

        assertThatThrownBy(() -> readingService.validateAndProcess(topic, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown machine");
    }

    @Test
    @DisplayName("US17: Invalid JSON payload is rejected")
    void testRejectInvalidJson() {
        String topic = "factory/machines/CNC-024/temperature";
        String payload = "{ invalid-json }";

        assertThatThrownBy(() -> readingService.validateAndProcess(topic, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    @DisplayName("US17: Missing, NaN, and Infinite values are rejected")
    void testRejectInvalidValues() {
        String topic = "factory/machines/CNC-024/temperature";

        // Missing value
        String missingValuePayload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;
        assertThatThrownBy(() -> readingService.validateAndProcess(topic, missingValuePayload))
                .isInstanceOf(IllegalArgumentException.class);

        // String value
        String nonNumericPayload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": "high",
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;
        assertThatThrownBy(() -> readingService.validateAndProcess(topic, nonNumericPayload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US17: Invalid unit for sensor type is rejected")
    void testRejectInvalidUnit() {
        String topic = "factory/machines/CNC-024/temperature";
        String payload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 45.0,
                  "unit": "RPM",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;

        assertThatThrownBy(() -> readingService.validateAndProcess(topic, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid unit");
    }

    @Test
    @DisplayName("US17: Future timestamp beyond 24h is rejected")
    void testRejectFutureTimestamp() {
        Instant farFuture = Instant.now().plus(48, ChronoUnit.HOURS);
        String topic = "factory/machines/CNC-024/temperature";
        String payload = String.format("""
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 45.0,
                  "unit": "°C",
                  "timestamp": "%s"
                }
                """, farFuture);

        assertThatThrownBy(() -> readingService.validateAndProcess(topic, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("US17: Topic sensor type mismatch with payload is rejected")
    void testRejectTypeMismatch() {
        String topic = "factory/machines/CNC-024/temperature";
        String payload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "VIBRATION",
                  "value": 3.0,
                  "unit": "mm/s",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;

        assertThatThrownBy(() -> readingService.validateAndProcess(topic, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mismatch");
    }

    @Test
    @DisplayName("US18: Duplicate message with exact same machine, sensor, timestamp is deduplicated")
    void testDeduplication() {
        String topic = "factory/machines/CNC-024/temperature";
        String payload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 45.0,
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;

        SensorReading r1 = readingService.validateAndProcess(topic, payload);
        assertThat(r1).isNotNull();

        // Exact same message sent again
        SensorReading r2 = readingService.validateAndProcess(topic, payload);
        assertThat(r2).isNull(); // ignored

        assertThat(readingRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("US19: Offline sensor recovers to ONLINE on new valid measurement")
    void testOfflineSensorRecovery() {
        // Pre-create offline sensor
        Sensor sensor = new Sensor(machine.getId(), SensorType.TEMPERATURE, "°C");
        sensor.setStatus(SensorStatus.OFFLINE);
        sensor.setLastSeen(Instant.now().minus(10, ChronoUnit.MINUTES));
        sensorRepository.save(sensor);

        String topic = "factory/machines/CNC-024/temperature";
        String payload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 46.5,
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;

        readingService.validateAndProcess(topic, payload);

        Sensor updatedSensor = sensorRepository.findByMachineIdAndType(machine.getId(), SensorType.TEMPERATURE).orElseThrow();
        assertThat(updatedSensor.getStatus()).isEqualTo(SensorStatus.ONLINE);
    }

    @Test
    @DisplayName("US25: Physical threshold breaches generate WARNING and CRITICAL alerts")
    void testThresholdBreachAlertGeneration() {
        String topic = "factory/machines/CNC-024/temperature";

        // 1. Warning threshold: 65°C (> 60°C warning)
        String warningPayload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 65.0,
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:15:30.123Z"
                }
                """;
        readingService.validateAndProcess(topic, warningPayload);

        List<Alert> alerts = alertRepository.findAll();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getSeverity()).isEqualTo("WARNING");
        assertThat(alerts.get(0).getValue()).isEqualTo(65.0);

        // 2. Critical threshold: 88°C (> 80°C critical)
        String criticalPayload = """
                {
                  "machineId": "CNC-024",
                  "sensorType": "TEMPERATURE",
                  "value": 88.0,
                  "unit": "°C",
                  "timestamp": "2026-09-28T10:16:30.123Z"
                }
                """;
        readingService.validateAndProcess(topic, criticalPayload);

        alerts = alertRepository.findAll();
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(1).getSeverity()).isEqualTo("CRITICAL");
        assertThat(alerts.get(1).getValue()).isEqualTo(88.0);
    }

    @Test
    @DisplayName("US18: History query returns paginated readings filtered by date and sensorType")
    void testGetReadingsHistory() {
        Sensor sensor = sensorRepository.save(new Sensor(machine.getId(), SensorType.TEMPERATURE, "°C"));

        Instant t1 = Instant.parse("2026-09-28T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-28T11:00:00Z");
        Instant t3 = Instant.parse("2026-09-28T12:00:00Z");

        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 40.0, "°C", t1));
        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 45.0, "°C", t2));
        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.VIBRATION, 2.5, "mm/s", t2));
        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 50.0, "°C", t3));

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp"));

        // Query by date range and type
        Map<String, Object> result = readingService.getReadings(
                machine.getId(),
                Instant.parse("2026-09-28T10:30:00Z"),
                Instant.parse("2026-09-28T12:30:00Z"),
                SensorType.TEMPERATURE,
                pageable
        );

        assertThat(result.get("totalElements")).isEqualTo(2L);
        @SuppressWarnings("unchecked")
        List<SensorReading> content = (List<SensorReading>) result.get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0).getValue()).isEqualTo(50.0);
        assertThat(content.get(1).getValue()).isEqualTo(45.0);
    }
}
