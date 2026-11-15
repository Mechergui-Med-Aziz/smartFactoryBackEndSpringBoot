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
     * Expose le template STOMP pour les services nécessitant une diffusion
     * vers des topics personnalisés (ex : prédictions IA).
     */
    public SimpMessagingTemplate getMessagingTemplate() {
        return messagingTemplate;
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

    /**
     * Broadcasts a machine status change (RUNNING, IDLE, MAINTENANCE, FAILURE, OFFLINE) (US24).
     * Sent to /topic/machines/status, /topic/machines/{machineId}/status and /topic/machines/{machineCode}/status.
     */
    public void broadcastMachineStatus(com.smartfactory.entity.Machine machine) {
        if (machine == null) {
            return;
        }
        broadcastMachineStatus(machine.getId(), machine.getCode(), machine.getStatus());
    }

    public void broadcastMachineStatus(String machineId, String machineCode, com.smartfactory.entity.MachineStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "MACHINE_STATUS");
        payload.put("machineId", machineId);
        if (StringUtils.hasText(machineCode)) {
            payload.put("machineCode", machineCode);
        }
        payload.put("status", status != null ? status.name() : null);
        payload.put("timestamp", Instant.now().toString());

        try {
            messagingTemplate.convertAndSend("/topic/machines/status", payload);
            if (StringUtils.hasText(machineId)) {
                messagingTemplate.convertAndSend("/topic/machines/" + machineId + "/status", payload);
            }
            if (StringUtils.hasText(machineCode) && !machineCode.equals(machineId)) {
                messagingTemplate.convertAndSend("/topic/machines/" + machineCode + "/status", payload);
            }
            log.info("Broadcasted machine status [{}] for machine [{}]", status, machineCode != null ? machineCode : machineId);
        } catch (Exception ex) {
            log.error("Failed to broadcast machine status for machine {}: {}", machineId, ex.getMessage());
        }
    }

    /**
     * Broadcasts an in-app notification to the specific recipient via WebSocket.
     */
    public void broadcastNotificationToUser(String userId, com.smartfactory.entity.AppNotification notification) {
        if (!StringUtils.hasText(userId) || notification == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "NOTIFICATION");
        payload.put("id", notification.getId());
        payload.put("userId", notification.getUserId());
        payload.put("alertId", notification.getAlertId());
        payload.put("machineId", notification.getMachineId());
        payload.put("title", notification.getTitle());
        payload.put("message", notification.getMessage());
        payload.put("type", notification.getType());
        payload.put("read", notification.isRead());
        payload.put("isRead", notification.isRead());
        payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : Instant.now().toString());

        try {
            messagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", payload);
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, payload);
            log.info("Broadcasted notification [{}] to user [{}]", notification.getId(), userId);
        } catch (Exception ex) {
            log.error("Failed to broadcast notification to user {}: {}", userId, ex.getMessage());
        }
    }

    /**
     * Broadcasts unread notification count update to the specific user via WebSocket.
     */
    public void broadcastUnreadCountToUser(String userId, long unreadCount) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "eventType", "UNREAD_COUNT",
                "userId", userId,
                "unreadCount", unreadCount,
                "timestamp", Instant.now().toString()
        );
        try {
            messagingTemplate.convertAndSend("/topic/users/" + userId + "/unread-count", payload);
        } catch (Exception ex) {
            log.error("Failed to broadcast unread count to user {}: {}", userId, ex.getMessage());
        }
    }
}
