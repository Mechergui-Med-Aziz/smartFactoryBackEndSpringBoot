package com.smartfactory.repository;

import com.smartfactory.entity.Machine;
import com.smartfactory.entity.MachineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MachineRepository extends MongoRepository<Machine, String> {

    Optional<Machine> findByCode(String code);

    boolean existsByCode(String code);

    List<Machine> findByZoneId(String zoneId);

    Page<Machine> findByZoneId(String zoneId, Pageable pageable);

    Page<Machine> findByStatus(MachineStatus status, Pageable pageable);

    long countByZoneId(String zoneId);
}
