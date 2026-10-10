package com.smartfactory.service;

import com.smartfactory.entity.Alert;
import com.smartfactory.entity.Machine;
import com.smartfactory.entity.Sensor;
import com.smartfactory.entity.SensorStatus;
import com.smartfactory.repository.AlertRepository;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SensorMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SensorMonitoringService.class);

    private final SensorRepository sensorRepository;
    private final MachineRepository machineRepository;
    private final AlertRepository alertRepository;
    private final RealtimeMessageService realtimeMessageService;

    @Value("${sensor.offline-timeout-seconds:30}")
    private long defaultTimeoutSeconds = 30;

    public SensorMonitoringService(SensorRepository sensorRepository,
                                   MachineRepository machineRepository,
                                   AlertRepository alertRepository,
                                   RealtimeMessageService realtimeMessageService) {
        this.sensorRepository = sensorRepository;
        this.machineRepository = machineRepository;
        this.alertRepository = alertRepository;
        this.realtimeMessageService = realtimeMessageService;
    }

    /**
     * Periodically monitors sensors and detects offline sensors (US19).
     * Sensors that have not emitted a measurement within their timeout transition to OFFLINE.
     */
    @Scheduled(fixedRateString = "${sensor.check-interval-ms:10000}", initialDelay = 5000)
    public int checkOfflineSensors() {
        List<Sensor> onlineSensors = sensorRepository.findByStatus(SensorStatus.ONLINE);
        if (onlineSensors.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        int transitionedCount = 0;

        for (Sensor sensor : onlineSensors) {
            long timeout = sensor.getTimeoutSeconds() != null ? sensor.getTimeoutSeconds() : defaultTimeoutSeconds;
            Instant threshold = now.minusSeconds(timeout);

            // If sensor has a lastSeen older than threshold, it has timed out
            if (sensor.getLastSeen() != null && sensor.getLastSeen().isBefore(threshold)) {
                sensor.setStatus(SensorStatus.OFFLINE);
                sensorRepository.save(sensor);
                transitionedCount++;

                String machineCode = machineRepository.findById(sensor.getMachineId())
                        .map(Machine::getCode)
                        .orElse(sensor.getMachineId());

                log.warn("Sensor [{}] ({}) on machine [{}] timed out (no data since {} > {}s) -> transitioned to OFFLINE",
                        sensor.getId(), sensor.getType(), machineCode, sensor.getLastSeen(), timeout);

                // Broadcast sensor status change to /topic/sensors/status and /topic/machines/{id}/sensors
                realtimeMessageService.broadcastSensorStatus(sensor, machineCode);

                // Create and broadcast an offline alert
                Alert offlineAlert = new Alert(
                        sensor.getMachineId(),
                        sensor.getId(),
                        sensor.getType(),
                        "WARNING",
                        null,
                        (double) timeout,
                        String.format("Capteur %s hors ligne sur la machine %s (inactif depuis > %ds)",
                                sensor.getType(), machineCode, timeout),
                        now
                );
                Alert savedAlert = alertRepository.save(offlineAlert);
                realtimeMessageService.broadcastAlert(savedAlert, machineCode);
            }
        }

        if (transitionedCount > 0) {
            log.info("Offline sensor check completed: {} sensor(s) transitioned to OFFLINE", transitionedCount);
        }
        return transitionedCount;
    }

    public void setDefaultTimeoutSeconds(long defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public long getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }
}
