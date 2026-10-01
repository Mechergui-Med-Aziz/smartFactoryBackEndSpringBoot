package com.smartfactory.controller;

import com.smartfactory.dto.request.CreateZoneRequest;
import com.smartfactory.dto.request.UpdateZoneRequest;
import com.smartfactory.dto.response.MachineResponse;
import com.smartfactory.dto.response.ZoneResponse;
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
    public ResponseEntity<List<ZoneResponse>> getAllZones() {
        List<ZoneResponse> zones = zoneService.getAllZones();
        return ResponseEntity.ok(zones);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneResponse> getZoneById(@PathVariable String id) {
        ZoneResponse response = zoneService.getZoneById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody CreateZoneRequest request) {
        ZoneResponse response = zoneService.createZone(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneResponse> updateZone(
            @PathVariable String id,
            @Valid @RequestBody UpdateZoneRequest request
    ) {
        ZoneResponse response = zoneService.updateZone(id, request);
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
    public ResponseEntity<List<MachineResponse>> getMachinesByZone(@PathVariable String id) {
        List<MachineResponse> machines = machineService.getMachinesByZone(id);
        return ResponseEntity.ok(machines);
    }
}
