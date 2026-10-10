package com.smartfactory.service;

import com.smartfactory.entity.*;
import com.smartfactory.repository.AlertRepository;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SensorMonitoringServiceTest {

    @Autowired
    private SensorMonitoringService monitoringService;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private AlertRepository alertRepository;

    private Machine machine;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        sensorRepository.deleteAll();
        machineRepository.deleteAll();

        machine = machineRepository.save(new Machine(
                "Presse Hydraulique",
                "PRS-001",
                "Presse",
                "zone-1",
                MachineStatus.RUNNING,
                "Presse principale",
                List.of()
        ));
    }

    @Test
    @DisplayName("US19: Active sensor with recent lastSeen stays ONLINE")
    void testActiveSensorStaysOnline() {
        Sensor sensor = new Sensor(machine.getId(), SensorType.TEMPERATURE, "°C");
        sensor.setStatus(SensorStatus.ONLINE);
        sensor.setLastSeen(Instant.now().minusSeconds(5)); // 5s ago < 30s timeout
        sensorRepository.save(sensor);

        int transitioned = monitoringService.checkOfflineSensors();

        assertThat(transitioned).isEqualTo(0);
        Sensor found = sensorRepository.findById(sensor.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(SensorStatus.ONLINE);
        assertThat(alertRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("US19: Sensor with lastSeen exceeding timeout transitions to OFFLINE and creates alert")
    void testInactiveSensorTransitionsToOffline() {
        Sensor sensor = new Sensor(machine.getId(), SensorType.TEMPERATURE, "°C");
        sensor.setStatus(SensorStatus.ONLINE);
        sensor.setTimeoutSeconds(20L);
        sensor.setLastSeen(Instant.now().minusSeconds(25)); // 25s ago > 20s timeout
        sensorRepository.save(sensor);

        int transitioned = monitoringService.checkOfflineSensors();

        assertThat(transitioned).isEqualTo(1);
        Sensor found = sensorRepository.findById(sensor.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(SensorStatus.OFFLINE);

        // Alert created
        List<Alert> alerts = alertRepository.findAll();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getSeverity()).isEqualTo("WARNING");
        assertThat(alerts.get(0).getSensorId()).isEqualTo(sensor.getId());
    }

    @Test
    @DisplayName("US19: Subsequent monitoring runs do not duplicate events or alerts for already OFFLINE sensor")
    void testNoDuplicateOfflineEvents() {
        Sensor sensor = new Sensor(machine.getId(), SensorType.VIBRATION, "mm/s");
        sensor.setStatus(SensorStatus.ONLINE);
        sensor.setTimeoutSeconds(10L);
        sensor.setLastSeen(Instant.now().minusSeconds(15));
        sensorRepository.save(sensor);

        // First run transitions to OFFLINE
        int transitionedFirst = monitoringService.checkOfflineSensors();
        assertThat(transitionedFirst).isEqualTo(1);
        assertThat(alertRepository.findAll()).hasSize(1);

        // Second run detects sensor is already OFFLINE
        int transitionedSecond = monitoringService.checkOfflineSensors();
        assertThat(transitionedSecond).isEqualTo(0);
        assertThat(alertRepository.findAll()).hasSize(1); // Still exactly 1 alert
    }
}
