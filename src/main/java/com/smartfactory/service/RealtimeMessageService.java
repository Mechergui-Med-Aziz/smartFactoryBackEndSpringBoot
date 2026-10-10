package com.smartfactory.service;

import com.smartfactory.entity.Alert;
import com.smartfactory.entity.Sensor;
import com.smartfactory.entity.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RealtimeMessageService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeMessageService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeMessageService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts a persisted sensor reading to WebSocket clients.
     * Sent to /topic/machines/{machineId}/readings and /topic/machines/{machineCode}/readings.
     */
    public void broadcastReading(SensorReading reading, String machineCode) {
        if (reading == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "SENSOR_READING");
        payload.put("id", reading.getId());
        payload.put("machineId", reading.getMachineId());
        if (StringUtils.hasText(machineCode)) {
            payload.put("machineCode", machineCode);
        }
        payload.put("sensorId", reading.getSensorId());
        payload.put("sensorType", reading.getSensorType() != null ? reading.getSensorType().name() : null);
        payload.put("value", reading.getValue());
        payload.put("unit", reading.getUnit());
        payload.put("timestamp", reading.getTimestamp() != null ? reading.getTimestamp().toString() : Instant.now().toString());

        try {
            // Broadcast to internal machineId topic
            String destById = "/topic/machines/" + reading.getMachineId() + "/readings";
            messagingTemplate.convertAndSend(destById, payload);
            log.debug("Broadcasted reading to {}", destById);

            // Also broadcast to human-readable machineCode topic if available (e.g. CNC-024)
            if (StringUtils.hasText(machineCode) && !machineCode.equals(reading.getMachineId())) {
                String destByCode = "/topic/machines/" + machineCode + "/readings";
                messagingTemplate.convertAndSend(destByCode, payload);
                log.debug("Broadcasted reading to {}", destByCode);
            }
        } catch (Exception ex) {
            log.error("Failed to broadcast sensor reading for machine {}: {}", reading.getMachineId(), ex.getMessage());
        }
    }

    /**
     * Broadcasts a sensor status change (e.g. ONLINE <-> OFFLINE).
     * Sent to /topic/sensors/status and /topic/machines/{machineId}/sensors.
     */
    public void broadcastSensorStatus(Sensor sensor, String machineCode) {
        if (sensor == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "SENSOR_STATUS");
        payload.put("sensorId", sensor.getId());
        payload.put("machineId", sensor.getMachineId());
        if (StringUtils.hasText(machineCode)) {
            payload.put("machineCode", machineCode);
        }
        payload.put("sensorType", sensor.getType() != null ? sensor.getType().name() : null);
        payload.put("status", sensor.getStatus() != null ? sensor.getStatus().name() : null);
        payload.put("unit", sensor.getUnit());
        payload.put("lastSeen", sensor.getLastSeen() != null ? sensor.getLastSeen().toString() : null);
        payload.put("timestamp", Instant.now().toString());

        try {
            // Global sensor status destination
            messagingTemplate.convertAndSend("/topic/sensors/status", payload);

            // Machine-specific sensors destination
            if (StringUtils.hasText(sensor.getMachineId())) {
                messagingTemplate.convertAndSend("/topic/machines/" + sensor.getMachineId() + "/sensors", payload);
                if (StringUtils.hasText(machineCode) && !machineCode.equals(sensor.getMachineId())) {
                    messagingTemplate.convertAndSend("/topic/machines/" + machineCode + "/sensors", payload);
                }
            }
            log.info("Broadcasted sensor status change to OFFLINE/ONLINE for sensor {}", sensor.getId());
        } catch (Exception ex) {
            log.error("Failed to broadcast sensor status for sensor {}: {}", sensor.getId(), ex.getMessage());
        }
    }

    /**
     * Broadcasts an alert to WebSocket clients.
     * Sent to /topic/alerts and /topic/machines/{machineId}/alerts.
     */
    public void broadcastAlert(Alert alert, String machineCode) {
        if (alert == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "ALERT");
        payload.put("id", alert.getId());
        payload.put("machineId", alert.getMachineId());
        if (StringUtils.hasText(machineCode)) {
            payload.put("machineCode", machineCode);
        }
        payload.put("sensorId", alert.getSensorId());
        payload.put("sensorType", alert.getSensorType() != null ? alert.getSensorType().name() : null);
        payload.put("severity", alert.getSeverity());
        payload.put("value", alert.getValue());
        payload.put("threshold", alert.getThreshold());
        payload.put("message", alert.getMessage());
        payload.put("status", alert.getStatus());
        payload.put("timestamp", alert.getTimestamp() != null ? alert.getTimestamp().toString() : Instant.now().toString());

        try {
            messagingTemplate.convertAndSend("/topic/alerts", payload);
            if (StringUtils.hasText(alert.getMachineId())) {
                messagingTemplate.convertAndSend("/topic/machines/" + alert.getMachineId() + "/alerts", payload);
            }
            log.info("Broadcasted alert [{}] for machine {}: {}", alert.getSeverity(), alert.getMachineId(), alert.getMessage());
        } catch (Exception ex) {
            log.error("Failed to broadcast alert: {}", ex.getMessage());
        }
    }
}
