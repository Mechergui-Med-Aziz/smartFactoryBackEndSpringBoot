package com.smartfactory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.entity.*;
import com.smartfactory.repository.AlertRepository;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.SensorReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class SensorReadingService {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingService.class);

    private final SensorReadingRepository readingRepository;
    private final MachineRepository machineRepository;
    private final SensorService sensorService;
    private final AlertRepository alertRepository;
    private final RealtimeMessageService realtimeMessageService;
    private final ObjectMapper objectMapper;

    public SensorReadingService(SensorReadingRepository readingRepository,
                                MachineRepository machineRepository,
                                SensorService sensorService,
                                AlertRepository alertRepository,
                                RealtimeMessageService realtimeMessageService,
                                ObjectMapper objectMapper) {
        this.readingRepository = readingRepository;
        this.machineRepository = machineRepository;
        this.sensorService = sensorService;
        this.alertRepository = alertRepository;
        this.realtimeMessageService = realtimeMessageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives and validates an MQTT message, persists it, checks thresholds, and broadcasts via STOMP.
     * US16, US17, US18, US25.
     */
    public SensorReading validateAndProcess(String topic, String rawPayload) {
        log.debug("Processing MQTT message on topic [{}]: {}", topic, rawPayload);

        if (!StringUtils.hasText(rawPayload)) {
            log.warn("Rejected MQTT message: empty payload on topic [{}]", topic);
            throw new IllegalArgumentException("Empty MQTT payload");
        }

        // 1. Parse JSON
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.warn("Rejected MQTT message: invalid JSON on topic [{}]: {}", topic, e.getMessage());
            throw new IllegalArgumentException("Invalid JSON payload: " + e.getMessage(), e);
        }

        // 2. Extract and validate topic components (e.g. factory/machines/{machineId}/{sensorType})
        String topicMachineCode = null;
        String topicSensorStr = null;
        if (StringUtils.hasText(topic)) {
            String[] parts = topic.split("/");
            if (parts.length >= 4 && "factory".equals(parts[0]) && "machines".equals(parts[1])) {
                topicMachineCode = parts[2];
                topicSensorStr = parts[3];
            }
        }

        // 3. Machine identification (US17 6.1)
        String payloadMachineId = root.path("machineId").asText(null);
        String machineLookupKey = StringUtils.hasText(payloadMachineId) ? payloadMachineId : topicMachineCode;

        if (!StringUtils.hasText(machineLookupKey)) {
            log.warn("Rejected MQTT message: missing machine identifier in topic and payload");
            throw new IllegalArgumentException("Machine identifier is required");
        }

        Machine machine = findMachineByCodeOrId(machineLookupKey).orElse(null);
        if (machine == null && StringUtils.hasText(topicMachineCode)) {
            machine = findMachineByCodeOrId(topicMachineCode).orElse(null);
        }

        if (machine == null) {
            log.warn("Rejected MQTT message: unknown machine [{}] on topic [{}]", machineLookupKey, topic);
            throw new IllegalArgumentException("Unknown machine: " + machineLookupKey);
        }

        // 4. Sensor type validation (US17 6.3)
        String payloadSensorTypeStr = root.path("sensorType").asText(null);
        SensorType payloadSensorType = SensorType.fromString(payloadSensorTypeStr);
        SensorType topicSensorType = SensorType.fromString(topicSensorStr);

        SensorType effectiveSensorType = payloadSensorType != null ? payloadSensorType : topicSensorType;
        if (effectiveSensorType == null) {
            log.warn("Rejected MQTT message: invalid sensor type in payload [{}] or topic [{}]", payloadSensorTypeStr, topicSensorStr);
            throw new IllegalArgumentException("Invalid sensor type");
        }

        if (payloadSensorType != null && topicSensorType != null && payloadSensorType != topicSensorType) {
            log.warn("Rejected MQTT message: sensor type mismatch between topic [{}] and payload [{}]", topicSensorType, payloadSensorType);
            throw new IllegalArgumentException("Topic sensor type mismatch with payload");
        }

        // 5. Value validation (US17 6.4)
        JsonNode valueNode = root.get("value");
        if (valueNode == null || !valueNode.isNumber()) {
            log.warn("Rejected MQTT message: missing or non-numeric value for machine [{}] sensor [{}]", machine.getCode(), effectiveSensorType);
            throw new IllegalArgumentException("Value must be a valid number");
        }
        double value = valueNode.asDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            log.warn("Rejected MQTT message: invalid numerical value (NaN/Infinite) for machine [{}] sensor [{}]", machine.getCode(), effectiveSensorType);
            throw new IllegalArgumentException("Value cannot be NaN or Infinite");
        }

        // 6. Unit validation (US17 6.5)
        String unit = root.path("unit").asText(null);
        if (!StringUtils.hasText(unit)) {
            unit = effectiveSensorType.getDefaultUnit();
        } else if (!effectiveSensorType.isValidUnit(unit)) {
            log.warn("Rejected MQTT message: invalid unit [{}] for sensor type [{}] on machine [{}]", unit, effectiveSensorType, machine.getCode());
            throw new IllegalArgumentException("Invalid unit [" + unit + "] for sensor type " + effectiveSensorType);
        }

        // 7. Timestamp validation (US17 6.6)
        String timestampStr = root.path("timestamp").asText(null);
        Instant timestamp = parseTimestamp(timestampStr);
        if (timestamp == null) {
            log.warn("Rejected MQTT message: invalid timestamp [{}] for machine [{}]", timestampStr, machine.getCode());
            throw new IllegalArgumentException("Invalid timestamp: " + timestampStr);
        }

        // Timestamp policy: reject future timestamps beyond 24h or ancient timestamps older than 30 days
        Instant now = Instant.now();
        if (timestamp.isAfter(now.plus(24, ChronoUnit.HOURS))) {
            log.warn("Rejected MQTT message: timestamp too far in future [{}] for machine [{}]", timestamp, machine.getCode());
            throw new IllegalArgumentException("Timestamp is in the future");
        }
        if (timestamp.isBefore(now.minus(30, ChronoUnit.DAYS))) {
            log.warn("Rejected MQTT message: timestamp too old [{}] for machine [{}]", timestamp, machine.getCode());
            throw new IllegalArgumentException("Timestamp is too old (> 30 days)");
        }

        // 8. Sensor identification & Auto-provisioning (US17 6.2)
        Sensor sensor = sensorService.getOrCreateSensor(machine, effectiveSensorType, unit);
        if (!machine.getId().equals(sensor.getMachineId())) {
            log.warn("Rejected MQTT message: sensor [{}] does not belong to machine [{}]", sensor.getId(), machine.getId());
            throw new IllegalArgumentException("Sensor does not belong to machine");
        }

        // 9. Deduplication check (US18 7.2)
        if (readingRepository.existsByMachineIdAndSensorIdAndTimestamp(machine.getId(), sensor.getId(), timestamp)) {
            log.debug("Duplicate measurement ignored for machine [{}] sensor [{}] timestamp [{}]", machine.getCode(), sensor.getId(), timestamp);
            return null;
        }

        // 10. Persist reading (US18 7.1)
        SensorReading reading = new SensorReading(
                machine.getId(),
                sensor.getId(),
                effectiveSensorType,
                value,
                unit,
                timestamp,
                now
        );
        SensorReading savedReading = readingRepository.save(reading);
        log.info("Persisted sensor reading: machine={}, sensor={}, type={}, value={} {}, ts={}",
                machine.getCode(), sensor.getId(), effectiveSensorType, value, unit, timestamp);

        // 11. Update sensor status & lastSeen (US19)
        sensor.setLastSeen(now);
        if (sensor.getStatus() == SensorStatus.OFFLINE) {
            sensor.setStatus(SensorStatus.ONLINE);
            sensorService.save(sensor);
            log.info("Sensor [{}] on machine [{}] transitioned OFFLINE -> ONLINE", sensor.getId(), machine.getCode());
            realtimeMessageService.broadcastSensorStatus(sensor, machine.getCode());
        } else {
            sensorService.save(sensor);
        }

        // 12. Check thresholds & create alerts if applicable
        checkThresholdsAndAlert(machine, sensor, effectiveSensorType, value, timestamp);

        // 13. Broadcast reading via STOMP (US25)
        realtimeMessageService.broadcastReading(savedReading, machine.getCode());

        return savedReading;
    }

    /**
     * Finds machine by unique code (e.g. CNC-024) or internal MongoDB ID.
     */
    public Optional<Machine> findMachineByCodeOrId(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return Optional.empty();
        }
        Optional<Machine> byCode = machineRepository.findByCode(identifier.trim());
        if (byCode.isPresent()) {
            return byCode;
        }
        return machineRepository.findById(identifier.trim());
    }

    /**
     * Parses ISO-8601 timestamps with Z, timezone offsets, or local naive timestamps.
     */
    private Instant parseTimestamp(String ts) {
        if (!StringUtils.hasText(ts)) {
            return null;
        }
        try {
            return Instant.parse(ts.trim());
        } catch (DateTimeParseException ignored) {
            try {
                // Support naive ISO timestamps like 2026-09-18T20:15:30 produced by simulator
                LocalDateTime ldt = LocalDateTime.parse(ts.trim());
                return ldt.toInstant(ZoneOffset.UTC);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * Verifies physical thresholds and persists an Alert when limits are breached.
     */
    private void checkThresholdsAndAlert(Machine machine, Sensor sensor, SensorType type, double value, Instant timestamp) {
        String severity = null;
        double threshold = 0.0;
        String message = null;

        switch (type) {
            case TEMPERATURE:
                if (value >= 80.0) {
                    severity = "CRITICAL";
                    threshold = 80.0;
                    message = String.format("Température critique détectée: %.1f°C (seuil: 80.0°C)", value);
                } else if (value >= 60.0) {
                    severity = "WARNING";
                    threshold = 60.0;
                    message = String.format("Avertissement température élevée: %.1f°C (seuil: 60.0°C)", value);
                }
                break;
            case VIBRATION:
                if (value >= 8.0) {
                    severity = "CRITICAL";
                    threshold = 8.0;
                    message = String.format("Vibration critique détectée: %.2f mm/s (seuil: 8.0 mm/s)", value);
                } else if (value >= 4.5) {
                    severity = "WARNING";
                    threshold = 4.5;
                    message = String.format("Avertissement vibration anormale: %.2f mm/s (seuil: 4.5 mm/s)", value);
                }
                break;
            case CURRENT:
                if (value >= 13.0) {
                    severity = "CRITICAL";
                    threshold = 13.0;
                    message = String.format("Courant critique détecté: %.2f A (seuil: 13.0 A)", value);
                } else if (value >= 9.5) {
                    severity = "WARNING";
                    threshold = 9.5;
                    message = String.format("Avertissement surintensité: %.2f A (seuil: 9.5 A)", value);
                }
                break;
            case RPM:
                if (value < 500.0 || value > 2000.0) {
                    severity = "CRITICAL";
                    threshold = value < 500.0 ? 500.0 : 2000.0;
                    message = String.format("Vitesse de rotation critique: %.0f RPM", value);
                } else if (value < 1000.0 || value > 1800.0) {
                    severity = "WARNING";
                    threshold = value < 1000.0 ? 1000.0 : 1800.0;
                    message = String.format("Avertissement vitesse de rotation anormale: %.0f RPM", value);
                }
                break;
        }

        if (severity != null) {
            Alert alert = new Alert(machine.getId(), sensor.getId(), type, severity, value, threshold, message, timestamp);
            Alert savedAlert = alertRepository.save(alert);
            log.warn("Created alert [{}] on machine [{}]: {}", severity, machine.getCode(), message);
            realtimeMessageService.broadcastAlert(savedAlert, machine.getCode());
        }
    }

    /**
     * History consultation with machine, dates, sensorType and pagination.
     * GET /api/machines/{id}/readings
     */
    public Map<String, Object> getReadings(String machineIdOrCode, Instant from, Instant to, SensorType sensorType, Pageable pageable) {
        Machine machine = findMachineByCodeOrId(machineIdOrCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "MACHINE_NOT_FOUND: Machine not found with id: " + machineIdOrCode));

        String machineId = machine.getId();
        Page<SensorReading> page;

        if (from != null && to != null && sensorType != null) {
            page = readingRepository.findByMachineIdAndSensorTypeAndTimestampBetween(machineId, sensorType, from, to, pageable);
        } else if (from != null && to != null) {
            page = readingRepository.findByMachineIdAndTimestampBetween(machineId, from, to, pageable);
        } else if (sensorType != null) {
            page = readingRepository.findByMachineIdAndSensorType(machineId, sensorType, pageable);
        } else {
            page = readingRepository.findByMachineId(machineId, pageable);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", page.getContent());
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        return response;
    }
}
