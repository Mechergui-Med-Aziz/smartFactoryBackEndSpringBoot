package com.smartfactory.repository;

import com.smartfactory.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends MongoRepository<Alert, String> {

    List<Alert> findByMachineId(String machineId);

    Page<Alert> findByMachineId(String machineId, Pageable pageable);

    List<Alert> findByStatus(String status);

    void deleteByMachineId(String machineId);
}
