package com.smartfactory.service;

import com.smartfactory.entity.Machine;
import com.smartfactory.entity.Sensor;
import com.smartfactory.entity.SensorStatus;
import com.smartfactory.entity.SensorType;
import com.smartfactory.repository.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SensorService {

    private static final Logger log = LoggerFactory.getLogger(SensorService.class);

    private final SensorRepository sensorRepository;

    public SensorService(SensorRepository sensorRepository) {
        this.sensorRepository = sensorRepository;
    }

    public List<Sensor> getSensorsByMachine(String machineId) {
        return sensorRepository.findByMachineId(machineId);
    }

    public Sensor getSensorById(String id) {
        return sensorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "SENSOR_NOT_FOUND: Sensor not found with id: " + id));
    }

    public Optional<Sensor> findByMachineAndType(String machineId, SensorType type) {
        return sensorRepository.findByMachineIdAndType(machineId, type);
    }

    public Sensor getOrCreateSensor(Machine machine, SensorType type, String unit) {
        return sensorRepository.findByMachineIdAndType(machine.getId(), type)
                .orElseGet(() -> {
                    String effectiveUnit = unit != null ? unit : type.getDefaultUnit();
                    Sensor sensor = new Sensor(machine.getId(), type, effectiveUnit);
                    Sensor saved = sensorRepository.save(sensor);
                    log.info("Auto-provisioned sensor [{}] for machine [{}] with unit [{}]", type, machine.getCode(), effectiveUnit);
                    return saved;
                });
    }

    public Sensor save(Sensor sensor) {
        return sensorRepository.save(sensor);
    }

    public List<Sensor> getSensorsByStatus(SensorStatus status) {
        return sensorRepository.findByStatus(status);
    }

    public List<Sensor> getAllSensors() {
        return sensorRepository.findAll();
    }
}
