package com.smartfactory.service;

import com.smartfactory.dto.request.CreateZoneRequest;
import com.smartfactory.dto.request.UpdateZoneRequest;
import com.smartfactory.dto.response.ZoneResponse;
import com.smartfactory.entity.Zone;
import com.smartfactory.exception.ApiException;
import com.smartfactory.exception.ErrorCode;
import com.smartfactory.mapper.ZoneMapper;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final MachineRepository machineRepository;
    private final ZoneMapper zoneMapper;

    public ZoneService(ZoneRepository zoneRepository,
                       MachineRepository machineRepository,
                       ZoneMapper zoneMapper) {
        this.zoneRepository = zoneRepository;
        this.machineRepository = machineRepository;
        this.zoneMapper = zoneMapper;
    }

    public List<ZoneResponse> getAllZones() {
        return zoneRepository.findAll().stream()
                .map(zone -> {
                    long count = machineRepository.countByZoneId(zone.getId());
                    return zoneMapper.toResponse(zone, count);
                })
                .collect(Collectors.toList());
    }

    public ZoneResponse getZoneById(String id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ZONE_NOT_FOUND, "Zone not found with id: " + id));
        long count = machineRepository.countByZoneId(zone.getId());
        return zoneMapper.toResponse(zone, count);
    }

    public ZoneResponse createZone(CreateZoneRequest request) {
        String trimmedName = request.getName().trim();
        if (zoneRepository.existsByName(trimmedName)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ZONE_ALREADY_EXISTS, "Zone already exists with name: " + trimmedName);
        }

        Zone zone = new Zone(
                trimmedName,
                request.getDescription() != null ? request.getDescription().trim() : null,
                request.getLocation() != null ? request.getLocation().trim() : null
        );

        Zone saved = zoneRepository.save(zone);
        return zoneMapper.toResponse(saved, 0);
    }

    public ZoneResponse updateZone(String id, UpdateZoneRequest request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ZONE_NOT_FOUND, "Zone not found with id: " + id));

        String trimmedName = request.getName().trim();
        if (!zone.getName().equalsIgnoreCase(trimmedName) && zoneRepository.existsByName(trimmedName)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ZONE_ALREADY_EXISTS, "Zone already exists with name: " + trimmedName);
        }

        zone.setName(trimmedName);
        zone.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        zone.setLocation(request.getLocation() != null ? request.getLocation().trim() : null);

        Zone updated = zoneRepository.save(zone);
        long count = machineRepository.countByZoneId(updated.getId());
        return zoneMapper.toResponse(updated, count);
    }

    public void deleteZone(String id) {
        if (!zoneRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ZONE_NOT_FOUND, "Zone not found with id: " + id);
        }
        // Disassociate machines that belonged to this zone
        machineRepository.findByZoneId(id).forEach(machine -> {
            machine.setZoneId(null);
            machineRepository.save(machine);
        });

        zoneRepository.deleteById(id);
    }
}
