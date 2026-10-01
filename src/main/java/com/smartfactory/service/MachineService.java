package com.smartfactory.service;

import com.smartfactory.dto.request.CreateMachineRequest;
import com.smartfactory.dto.request.UpdateMachineRequest;
import com.smartfactory.dto.response.MachineResponse;
import com.smartfactory.dto.response.PageResponse;
import com.smartfactory.entity.Machine;
import com.smartfactory.entity.MachineStatus;
import com.smartfactory.entity.Zone;
import com.smartfactory.exception.ApiException;
import com.smartfactory.exception.ErrorCode;
import com.smartfactory.mapper.MachineMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MachineService {

    private final MachineRepository machineRepository;
    private final ZoneRepository zoneRepository;
    private final MachineMapper machineMapper;
    private final MongoTemplate mongoTemplate;

    public MachineService(MachineRepository machineRepository,
                          ZoneRepository zoneRepository,
                          MachineMapper machineMapper,
                          MongoTemplate mongoTemplate) {
        this.machineRepository = machineRepository;
        this.zoneRepository = zoneRepository;
        this.machineMapper = machineMapper;
        this.mongoTemplate = mongoTemplate;
    }

    public PageResponse<MachineResponse> getAllMachines(String zoneId, MachineStatus status, String search, Pageable pageable) {
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

        Page<Machine> machinePage = new PageImpl<>(machines, pageable, total);

        Map<String, String> zoneNameMap = zoneRepository.findAll().stream()
                .collect(Collectors.toMap(Zone::getId, Zone::getName, (a, b) -> a));

        Page<MachineResponse> responsePage = machinePage.map(machine -> {
            String zoneName = machine.getZoneId() != null ? zoneNameMap.get(machine.getZoneId()) : null;
            return machineMapper.toResponse(machine, zoneName);
        });

        return PageResponse.of(responsePage);
    }

    public MachineResponse getMachineById(String id) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MACHINE_NOT_FOUND, "Machine not found with id: " + id));

        String zoneName = null;
        if (StringUtils.hasText(machine.getZoneId())) {
            zoneName = zoneRepository.findById(machine.getZoneId())
                    .map(Zone::getName)
                    .orElse(null);
        }

        return machineMapper.toResponse(machine, zoneName);
    }

    // RB01: Machine has a unique ID and unique business code
    public MachineResponse createMachine(CreateMachineRequest request) {
        String trimmedCode = request.getCode().trim();
        if (machineRepository.existsByCode(trimmedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.MACHINE_ALREADY_EXISTS, "Machine code already exists: " + trimmedCode);
        }

        String zoneName = null;
        if (StringUtils.hasText(request.getZoneId())) {
            Zone zone = zoneRepository.findById(request.getZoneId().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ZONE_NOT_FOUND, "Zone not found with id: " + request.getZoneId()));
            zoneName = zone.getName();
        }

        Machine machine = new Machine(
                request.getName().trim(),
                trimmedCode,
                request.getType().trim(),
                StringUtils.hasText(request.getZoneId()) ? request.getZoneId().trim() : null,
                request.getStatus() != null ? request.getStatus() : MachineStatus.IDLE,
                request.getDescription() != null ? request.getDescription().trim() : null,
                request.getCaracteristiques()
        );

        Machine saved = machineRepository.save(machine);
        return machineMapper.toResponse(saved, zoneName);
    }

    // RB01: Code uniqueness must be preserved during update
    public MachineResponse updateMachine(String id, UpdateMachineRequest request) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MACHINE_NOT_FOUND, "Machine not found with id: " + id));

        String trimmedCode = request.getCode().trim();
        if (!machine.getCode().equalsIgnoreCase(trimmedCode) && machineRepository.existsByCode(trimmedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.MACHINE_ALREADY_EXISTS, "Machine code already exists: " + trimmedCode);
        }

        String zoneName = null;
        if (StringUtils.hasText(request.getZoneId())) {
            Zone zone = zoneRepository.findById(request.getZoneId().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ZONE_NOT_FOUND, "Zone not found with id: " + request.getZoneId()));
            zoneName = zone.getName();
        }

        machine.setName(request.getName().trim());
        machine.setCode(trimmedCode);
        machine.setType(request.getType().trim());
        machine.setZoneId(StringUtils.hasText(request.getZoneId()) ? request.getZoneId().trim() : null);
        machine.setStatus(request.getStatus());
        machine.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        machine.setCaracteristiques(request.getCaracteristiques());

        Machine updated = machineRepository.save(machine);
        return machineMapper.toResponse(updated, zoneName);
    }

    public List<MachineResponse> getMachinesByZone(String zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ZONE_NOT_FOUND, "Zone not found with id: " + zoneId));

        return machineRepository.findByZoneId(zoneId).stream()
                .map(machine -> machineMapper.toResponse(machine, zone.getName()))
                .collect(Collectors.toList());
    }

    public void deleteMachine(String id) {
        if (!machineRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MACHINE_NOT_FOUND, "Machine not found with id: " + id);
        }
        machineRepository.deleteById(id);
    }
}
