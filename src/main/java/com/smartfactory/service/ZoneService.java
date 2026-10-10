package com.smartfactory.service;

import com.smartfactory.entity.Zone;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final MachineRepository machineRepository;

    public ZoneService(ZoneRepository zoneRepository,
                       MachineRepository machineRepository) {
        this.zoneRepository = zoneRepository;
        this.machineRepository = machineRepository;
    }

    public List<Zone> getAllZones() {
        return zoneRepository.findAll().stream()
                .peek(zone -> {
                    long count = machineRepository.countByZoneId(zone.getId());
                    zone.setMachineCount(count);
                })
                .collect(Collectors.toList());
    }

    public Zone getZoneById(String id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ZONE_NOT_FOUND: Zone not found with id: " + id));
        long count = machineRepository.countByZoneId(zone.getId());
        zone.setMachineCount(count);
        return zone;
    }

    public Zone createZone(Zone zone) {
        String trimmedName = zone.getName() != null ? zone.getName().trim() : "";
        if (zoneRepository.existsByName(trimmedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ZONE_ALREADY_EXISTS: Zone already exists with name: " + trimmedName);
        }

        zone.setName(trimmedName);
        zone.setDescription(zone.getDescription() != null ? zone.getDescription().trim() : null);
        zone.setLocation(zone.getLocation() != null ? zone.getLocation().trim() : null);

        Zone saved = zoneRepository.save(zone);
        saved.setMachineCount(0);
        return saved;
    }

    public Zone updateZone(String id, Zone request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ZONE_NOT_FOUND: Zone not found with id: " + id));

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (!zone.getName().equalsIgnoreCase(trimmedName) && zoneRepository.existsByName(trimmedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ZONE_ALREADY_EXISTS: Zone already exists with name: " + trimmedName);
        }

        zone.setName(trimmedName);
        zone.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        zone.setLocation(request.getLocation() != null ? request.getLocation().trim() : null);

        Zone updated = zoneRepository.save(zone);
        long count = machineRepository.countByZoneId(updated.getId());
        updated.setMachineCount(count);
        return updated;
    }

    public void deleteZone(String id) {
        if (!zoneRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ZONE_NOT_FOUND: Zone not found with id: " + id);
        }
        // Disassociate machines that belonged to this zone
        machineRepository.findByZoneId(id).forEach(machine -> {
            machine.setZoneId(null);
            machineRepository.save(machine);
        });

        zoneRepository.deleteById(id);
    }
}
