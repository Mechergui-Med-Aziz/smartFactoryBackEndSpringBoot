package com.smartfactory.repository;

import com.smartfactory.entity.Sensor;
import com.smartfactory.entity.SensorStatus;
import com.smartfactory.entity.SensorType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorRepository extends MongoRepository<Sensor, String> {

    List<Sensor> findByMachineId(String machineId);

    Optional<Sensor> findByMachineIdAndType(String machineId, SensorType type);

    boolean existsByMachineIdAndType(String machineId, SensorType type);

    List<Sensor> findByStatus(SensorStatus status);

    void deleteByMachineId(String machineId);
}
