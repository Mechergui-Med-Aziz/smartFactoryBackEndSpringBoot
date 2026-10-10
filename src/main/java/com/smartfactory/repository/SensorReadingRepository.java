package com.smartfactory.repository;

import com.smartfactory.entity.SensorReading;
import com.smartfactory.entity.SensorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SensorReadingRepository extends MongoRepository<SensorReading, String> {

    Page<SensorReading> findByMachineId(String machineId, Pageable pageable);

    Page<SensorReading> findByMachineIdAndTimestampBetween(String machineId, Instant from, Instant to, Pageable pageable);

    Page<SensorReading> findByMachineIdAndSensorType(String machineId, SensorType sensorType, Pageable pageable);

    Page<SensorReading> findByMachineIdAndSensorTypeAndTimestampBetween(String machineId, SensorType sensorType, Instant from, Instant to, Pageable pageable);

    List<SensorReading> findByMachineId(String machineId, Sort sort);

    List<SensorReading> findByMachineIdAndTimestampBetween(String machineId, Instant from, Instant to, Sort sort);

    List<SensorReading> findByMachineIdAndSensorType(String machineId, SensorType sensorType, Sort sort);

    List<SensorReading> findByMachineIdAndSensorTypeAndTimestampBetween(String machineId, SensorType sensorType, Instant from, Instant to, Sort sort);

    boolean existsByMachineIdAndSensorIdAndTimestamp(String machineId, String sensorId, Instant timestamp);

    void deleteByMachineId(String machineId);
}
