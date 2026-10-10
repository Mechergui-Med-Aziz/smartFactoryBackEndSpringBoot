package com.smartfactory.controller;

import com.smartfactory.entity.SensorReading;
import com.smartfactory.entity.SensorType;
import com.smartfactory.service.SensorReadingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/machines")
public class SensorReadingController {

    private final SensorReadingService readingService;

    public SensorReadingController(SensorReadingService readingService) {
        this.readingService = readingService;
    }

    /**
     * US18 7.3: Historical sensor readings consultation
     * GET /api/machines/{id}/readings?from=...&to=...&sensorType=...&page=0&size=20&sort=timestamp,desc
     */
    @GetMapping("/{id}/readings")
    public ResponseEntity<Map<String, Object>> getMachineReadings(
            @PathVariable String id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String sensorType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp,desc") String sort) {

        Instant fromInstant = null;
        if (StringUtils.hasText(from)) {
            try {
                fromInstant = Instant.parse(from.trim());
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid 'from' date format: " + from);
            }
        }

        Instant toInstant = null;
        if (StringUtils.hasText(to)) {
            try {
                toInstant = Instant.parse(to.trim());
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid 'to' date format: " + to);
            }
        }

        SensorType parsedType = null;
        if (StringUtils.hasText(sensorType)) {
            parsedType = SensorType.fromString(sensorType);
            if (parsedType == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid sensorType: " + sensorType);
            }
        }

        int boundedSize = Math.max(1, Math.min(size, 100));
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String sortProperty = sortParts[0].trim();
        Sort sortObj = Sort.by(direction, sortProperty);

        Pageable pageable = PageRequest.of(page, boundedSize, sortObj);
        Map<String, Object> response = readingService.getReadings(id, fromInstant, toInstant, parsedType, pageable);
        return ResponseEntity.ok(response);
    }
}
