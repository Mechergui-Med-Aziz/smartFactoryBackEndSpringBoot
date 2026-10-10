package com.smartfactory.service;

import com.smartfactory.entity.Machine;
import com.smartfactory.entity.MachineStatus;
import com.smartfactory.entity.Zone;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.ZoneRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MachineService {

    private final MachineRepository machineRepository;
    private final ZoneRepository zoneRepository;
    private final MongoTemplate mongoTemplate;

    public MachineService(MachineRepository machineRepository,
                          ZoneRepository zoneRepository,
                          MongoTemplate mongoTemplate) {
        this.machineRepository = machineRepository;
        this.zoneRepository = zoneRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Map<String, Object> getAllMachines(String zoneId, MachineStatus status, String search, Pageable pageable) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (StringUtils.hasText(zoneId)) {
            criteriaList.add(Criteria.where("zoneId").is(zoneId.trim()));
        }

        if (status != null) {
            criteriaList.add(Criteria.where("status").is(status));
        }

        if (StringUtils.hasText(search)) {
            String regex = search.trim();
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("name").regex(regex, "i"),
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("type").regex(regex, "i")
            ));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, Machine.class);
        query.with(pageable);
        List<Machine> machines = mongoTemplate.find(query, Machine.class);

        Map<String, String> zoneNameMap = zoneRepository.findAll().stream()
                .collect(Collectors.toMap(Zone::getId, Zone::getName, (a, b) -> a));

        machines.forEach(machine -> {
            String zoneName = machine.getZoneId() != null ? zoneNameMap.get(machine.getZoneId()) : null;
            machine.setZoneName(zoneName);
        });

        Page<Machine> machinePage = new PageImpl<>(machines, pageable, total);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", machinePage.getContent());
        response.put("page", machinePage.getNumber());
        response.put("size", machinePage.getSize());
        response.put("totalElements", machinePage.getTotalElements());
        response.put("totalPages", machinePage.getTotalPages());
        return response;
    }

    public Machine getMachineById(String id) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MACHINE_NOT_FOUND: Machine not found with id: " + id));

        String zoneName = null;
        if (StringUtils.hasText(machine.getZoneId())) {
            zoneName = zoneRepository.findById(machine.getZoneId())
                    .map(Zone::getName)
                    .orElse(null);
        }
        machine.setZoneName(zoneName);

        return machine;
    }

    // RB01: Machine has a unique ID and unique business code
    public Machine createMachine(Machine machine) {
        String trimmedCode = machine.getCode() != null ? machine.getCode().trim() : "";
        if (machineRepository.existsByCode(trimmedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MACHINE_ALREADY_EXISTS: Machine code already exists: " + trimmedCode);
        }

        String zoneName = null;
        if (StringUtils.hasText(machine.getZoneId())) {
            Zone zone = zoneRepository.findById(machine.getZoneId().trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ZONE_NOT_FOUND: Zone not found with id: " + machine.getZoneId()));
            zoneName = zone.getName();
        }

        machine.setName(machine.getName() != null ? machine.getName().trim() : null);
        machine.setCode(trimmedCode);
        machine.setType(machine.getType() != null ? machine.getType().trim() : null);
        machine.setZoneId(StringUtils.hasText(machine.getZoneId()) ? machine.getZoneId().trim() : null);
        if (machine.getStatus() == null) {
            machine.setStatus(MachineStatus.IDLE);
        }
        machine.setDescription(machine.getDescription() != null ? machine.getDescription().trim() : null);
        if (machine.getCaracteristiques() == null) {
            machine.setCaracteristiques(new ArrayList<>());
        }

        Machine saved = machineRepository.save(machine);
        saved.setZoneName(zoneName);
        return saved;
    }

    // RB01: Code uniqueness must be preserved during update
    public Machine updateMachine(String id, Machine request) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MACHINE_NOT_FOUND: Machine not found with id: " + id));

        String trimmedCode = request.getCode() != null ? request.getCode().trim() : "";
        if (!machine.getCode().equalsIgnoreCase(trimmedCode) && machineRepository.existsByCode(trimmedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MACHINE_ALREADY_EXISTS: Machine code already exists: " + trimmedCode);
        }

        String zoneName = null;
        if (StringUtils.hasText(request.getZoneId())) {
            Zone zone = zoneRepository.findById(request.getZoneId().trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ZONE_NOT_FOUND: Zone not found with id: " + request.getZoneId()));
            zoneName = zone.getName();
        }

        machine.setName(request.getName() != null ? request.getName().trim() : null);
        machine.setCode(trimmedCode);
        machine.setType(request.getType() != null ? request.getType().trim() : null);
        machine.setZoneId(StringUtils.hasText(request.getZoneId()) ? request.getZoneId().trim() : null);
        machine.setStatus(request.getStatus());
        machine.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        machine.setCaracteristiques(request.getCaracteristiques() != null ? request.getCaracteristiques() : new ArrayList<>());

        Machine updated = machineRepository.save(machine);
        updated.setZoneName(zoneName);
        return updated;
    }

    public List<Machine> getMachinesByZone(String zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ZONE_NOT_FOUND: Zone not found with id: " + zoneId));

        List<Machine> machines = machineRepository.findByZoneId(zoneId);
        machines.forEach(machine -> machine.setZoneName(zone.getName()));
        return machines;
    }

    public void deleteMachine(String id) {
        if (!machineRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MACHINE_NOT_FOUND: Machine not found with id: " + id);
        }
        machineRepository.deleteById(id);
    }
}
