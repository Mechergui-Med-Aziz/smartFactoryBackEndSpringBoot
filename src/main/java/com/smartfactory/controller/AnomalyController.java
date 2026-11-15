package com.smartfactory.controller;

import com.smartfactory.dto.AiPredictionRequest;
import com.smartfactory.dto.AiPredictionResponse;
import com.smartfactory.entity.Anomaly;
import com.smartfactory.entity.Machine;
import com.smartfactory.entity.SensorType;
import com.smartfactory.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Contrôleur REST pour la détection et l'analyse d'anomalies (Sprint 5 - US33, US34, US35, US36).
 */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyService anomalyService;
    private final AdvancedAnomalyDetectionService advancedDetectionService;
    private final MultiSensorAnalysisService multiSensorAnalysisService;
    private final AnomalyScoreService anomalyScoreService;
    private final SensorReadingService sensorReadingService;
    private final AiPredictionService aiPredictionService;

    public AnomalyController(AnomalyService anomalyService,
                             AdvancedAnomalyDetectionService advancedDetectionService,
                             MultiSensorAnalysisService multiSensorAnalysisService,
                             AnomalyScoreService anomalyScoreService,
                             SensorReadingService sensorReadingService,
                             AiPredictionService aiPredictionService) {
        this.anomalyService = anomalyService;
        this.advancedDetectionService = advancedDetectionService;
        this.multiSensorAnalysisService = multiSensorAnalysisService;
        this.anomalyScoreService = anomalyScoreService;
        this.sensorReadingService = sensorReadingService;
        this.aiPredictionService = aiPredictionService;
    }

    /**
     * Consultation paginée des anomalies (US33, Section 8).
     * STRICTEMENT INTERDIT à l'OPERATOR.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_INDUSTRIEL', 'TECHNICIAN') or (hasAuthority('ALERT_READ') and !hasRole('OPERATOR'))")
    public ResponseEntity<Page<Anomaly>> getAnomalies(
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Anomaly> anomalies = anomalyService.getAnomalies(machineId, status, from, to, pageable);
        return ResponseEntity.ok(anomalies);
    }

    /**
     * Consultation du détail d'une anomalie par son ID (US44, Section 8).
     * STRICTEMENT INTERDIT à l'OPERATOR.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_INDUSTRIEL', 'TECHNICIAN') or (hasAuthority('ALERT_READ') and !hasRole('OPERATOR'))")
    public ResponseEntity<Anomaly> getAnomalyById(@PathVariable String id) {
        return ResponseEntity.ok(anomalyService.getAnomalyById(id));
    }

    /**
     * Score d'anomalie normalisé sur 100 pour une machine (US36).
     */
    @GetMapping("/machines/{machineId}/score")
    @PreAuthorize("hasAnyAuthority('ALERT_READ', 'ALERT:CONSULTER', 'MACHINE_READ')")
    public ResponseEntity<AnomalyScoreService.AnomalyScoreResult> getMachineAnomalyScore(@PathVariable String machineId) {
        return ResponseEntity.ok(anomalyScoreService.calculateScore(machineId));
    }

    /**
     * Analyse corrélée multi-capteurs pour une machine (US35).
     */
    @GetMapping("/machines/{machineId}/multi-sensor")
    @PreAuthorize("hasAnyAuthority('ALERT_READ', 'ALERT:CONSULTER', 'MACHINE_READ')")
    public ResponseEntity<MultiSensorAnalysisService.MultiSensorAnalysisResult> getMultiSensorAnalysis(@PathVariable String machineId) {
        Machine machine = sensorReadingService.resolveMachine(machineId);
        return ResponseEntity.ok(multiSensorAnalysisService.analyzeMachine(machine));
    }

    /**
     * Détection avancée par modélisation statistique comportementale (US34).
     */
    @GetMapping("/machines/{machineId}/sensors/{sensorType}/advanced-detection")
    @PreAuthorize("hasAnyAuthority('ALERT_READ', 'ALERT:CONSULTER', 'MACHINE_READ')")
    public ResponseEntity<AdvancedAnomalyDetectionService.DetectionResult> getAdvancedDetection(
            @PathVariable String machineId,
            @PathVariable String sensorType,
            @RequestParam(required = false) Double currentValue) {

        SensorType type = SensorType.fromString(sensorType);
        Machine machine = sensorReadingService.resolveMachine(machineId);
        return ResponseEntity.ok(advancedDetectionService.analyzeSensorBehavior(machine.getId(), type, currentValue));
    }

    /**
     * Réception d'une prédiction du modèle IA SmartFactory (Sprint 8 - US AI).
     *
     * Endpoint interne utilisé par le service Python d'inférence temps réel
     * pour transmettre les résultats de classification de panne et détection d'anomalies.
     * Crée une anomalie (thresholdType=AI_MODEL) et une alerte liée, puis diffuse via WebSocket.
     *
     * Sécurité : endpoint permitAll dans SecurityConfig, protégé par clé API
     * dans le header X-AI-Api-Key (machine-to-machine, pas de JWT nécessaire).
     */
    @PostMapping("/ai-prediction")
    public ResponseEntity<AiPredictionResponse> receiveAiPrediction(
            @RequestBody AiPredictionRequest request,
            @RequestHeader(value = "X-AI-Api-Key", required = false) String apiKey) {
        // Validation de la clé API interne
        String expectedKey = System.getenv().getOrDefault("SMARTFACTORY_AI_API_KEY", "smartfactory-ai-internal-2024");
        if (apiKey == null || !apiKey.equals(expectedKey)) {
            return ResponseEntity.status(403).build();
        }
        AiPredictionResponse response = aiPredictionService.processPrediction(request);
        return ResponseEntity.ok(response);
    }
}
