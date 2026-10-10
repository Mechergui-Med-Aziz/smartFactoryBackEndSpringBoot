package com.smartfactory.controller;

import com.smartfactory.entity.Machine;
import com.smartfactory.entity.MachineStatus;
import com.smartfactory.service.MachineService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMachines(
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) MachineStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sortObj = Sort.by(direction, sortParts[0]);

        Pageable pageable = PageRequest.of(page, boundedSize, sortObj);
        Map<String, Object> response = machineService.getAllMachines(zoneId, status, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Machine> getMachineById(@PathVariable String id) {
        Machine response = machineService.getMachineById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Machine> createMachine(@Valid @RequestBody Machine machine) {
        Machine response = machineService.createMachine(machine);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Machine> updateMachine(
            @PathVariable String id,
            @Valid @RequestBody Machine machine
    ) {
        if (machine.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Machine status is required");
        }
        Machine response = machineService.updateMachine(id, machine);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMachine(@PathVariable String id) {
        machineService.deleteMachine(id);
        return ResponseEntity.noContent().build();
    }

    // US08: Machine details & associated sensors
    @GetMapping("/{id}/sensors")
    public ResponseEntity<List<Object>> getMachineSensors(@PathVariable String id) {
        // Verify machine exists
        machineService.getMachineById(id);
        // Sprint 1 boundary: sensors will be populated in Sprint 2
        return ResponseEntity.ok(Collections.emptyList());
    }
}
