package com.smartfactory.service;

import com.smartfactory.entity.Alert;
import com.smartfactory.entity.Sensor;
import com.smartfactory.entity.SensorReading;
import com.smartfactory.entity.SensorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeMessageServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RealtimeMessageService realtimeMessageService;

    @Test
    @DisplayName("US25: Broadcast reading sends to internal machineId and machineCode topics")
    void testBroadcastReading() {
        SensorReading reading = new SensorReading(
                "machine-123",
                "sensor-456",
                SensorType.TEMPERATURE,
                48.5,
                "°C",
                Instant.now()
        );
        reading.setId("reading-789");

        realtimeMessageService.broadcastReading(reading, "CNC-024");

        verify(messagingTemplate).convertAndSend(eq("/topic/machines/machine-123/readings"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/machines/CNC-024/readings"), any(Map.class));
    }

    @Test
    @DisplayName("US25: Broadcast sensor status sends to /topic/sensors/status and machine topic")
    void testBroadcastSensorStatus() {
        Sensor sensor = new Sensor("machine-123", SensorType.VIBRATION, "mm/s");
        sensor.setId("sensor-456");

        realtimeMessageService.broadcastSensorStatus(sensor, "CNC-024");

        verify(messagingTemplate).convertAndSend(eq("/topic/sensors/status"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/machines/machine-123/sensors"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/machines/CNC-024/sensors"), any(Map.class));
    }

    @Test
    @DisplayName("US25: Broadcast alert sends to /topic/alerts and /topic/machines/{id}/alerts")
    void testBroadcastAlert() {
        Alert alert = new Alert(
                "machine-123",
                "sensor-456",
                SensorType.TEMPERATURE,
                "CRITICAL",
                85.0,
                80.0,
                "Température critique",
                Instant.now()
        );
        alert.setId("alert-001");

        realtimeMessageService.broadcastAlert(alert, "CNC-024");

        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/machines/machine-123/alerts"), any(Map.class));
    }

    @Test
    @DisplayName("US25: Messaging failure is handled gracefully without throwing exception")
    void testBroadcastFailureHandledGracefully() {
        SensorReading reading = new SensorReading(
                "machine-123",
                "sensor-456",
                SensorType.TEMPERATURE,
                48.5,
                "°C",
                Instant.now()
        );

        doThrow(new MessagingException("Broker unavailable"))
                .when(messagingTemplate).convertAndSend(any(String.class), any(Map.class));

        assertThatCode(() -> realtimeMessageService.broadcastReading(reading, "CNC-024"))
                .doesNotThrowAnyException();
    }
}
