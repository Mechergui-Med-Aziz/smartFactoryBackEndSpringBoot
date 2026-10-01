package com.smartfactory.controller;

import com.smartfactory.dto.request.CreateMachineRequest;
import com.smartfactory.dto.request.UpdateMachineRequest;
import com.smartfactory.dto.response.MachineResponse;
import com.smartfactory.dto.response.PageResponse;
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

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<MachineResponse>> getMachines(
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
        PageResponse<MachineResponse> response = machineService.getAllMachines(zoneId, status, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MachineResponse> getMachineById(@PathVariable String id) {
        MachineResponse response = machineService.getMachineById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MachineResponse> createMachine(@Valid @RequestBody CreateMachineRequest request) {
        MachineResponse response = machineService.createMachine(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MachineResponse> updateMachine(
            @PathVariable String id,
            @Valid @RequestBody UpdateMachineRequest request
    ) {
        MachineResponse response = machineService.updateMachine(id, request);
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
