package com.smartfactory.controller;

import com.smartfactory.entity.Machine;
import com.smartfactory.entity.Zone;
import com.smartfactory.service.MachineService;
import com.smartfactory.service.ZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zoneService;
    private final MachineService machineService;

    public ZoneController(ZoneService zoneService, MachineService machineService) {
        this.zoneService = zoneService;
        this.machineService = machineService;
    }

    @GetMapping
    public ResponseEntity<List<Zone>> getAllZones() {
        List<Zone> zones = zoneService.getAllZones();
        return ResponseEntity.ok(zones);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Zone> getZoneById(@PathVariable String id) {
        Zone response = zoneService.getZoneById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Zone> createZone(@Valid @RequestBody Zone zone) {
        Zone response = zoneService.createZone(zone);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Zone> updateZone(
            @PathVariable String id,
            @Valid @RequestBody Zone zone
    ) {
        Zone response = zoneService.updateZone(id, zone);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteZone(@PathVariable String id) {
        zoneService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }

    // US09: Associate & view machines organized by zone
    @GetMapping("/{id}/machines")
    public ResponseEntity<List<Machine>> getMachinesByZone(@PathVariable String id) {
        List<Machine> machines = machineService.getMachinesByZone(id);
        return ResponseEntity.ok(machines);
    }
}
