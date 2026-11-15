package com.smartfactory.service;

import com.smartfactory.dto.AiPredictionRequest;
import com.smartfactory.dto.AiPredictionResponse;
import com.smartfactory.entity.Alert;
import com.smartfactory.entity.Anomaly;
import com.smartfactory.entity.Machine;
import com.smartfactory.repository.AlertRepository;
import com.smartfactory.repository.AnomalyRepository;
import com.smartfactory.repository.MachineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service de traitement des prédictions du modèle IA SmartFactory (Sprint 8).
 *
 * Reçoit les prédictions du service Python d'inférence (fault classification
 * et anomaly detection) et les intègre dans le cycle de vie existant des
 * anomalies et alertes du backend.
 *
 * Fonctionnalités :
 * - Résolution du machineCode vers l'entité Machine.
 * - Création/déduplication d'anomalies avec thresholdType "AI_MODEL".
 * - Création d'alertes liées et diffusion WebSocket temps réel.
 * - Filtrage des prédictions "normal" pour éviter la pollution de données.
 */
@Service
public class AiPredictionService {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionService.class);

    /** Type de seuil pour les anomalies détectées par le modèle IA */
    private static final String THRESHOLD_TYPE_AI = "AI_MODEL";

    private final MachineRepository machineRepository;
    private final AnomalyRepository anomalyRepository;
    private final AlertRepository alertRepository;
    private final RealtimeMessageService realtimeMessageService;
    private final NotificationService notificationService;

    public AiPredictionService(MachineRepository machineRepository,
                               AnomalyRepository anomalyRepository,
                               AlertRepository alertRepository,
                               RealtimeMessageService realtimeMessageService,
                               NotificationService notificationService) {
        this.machineRepository = machineRepository;
        this.anomalyRepository = anomalyRepository;
        this.alertRepository = alertRepository;
        this.realtimeMessageService = realtimeMessageService;
        this.notificationService = notificationService;
    }

    /**
     * Traite une prédiction IA entrante et crée/met à jour l'anomalie et l'alerte correspondantes.
     *
     * @param request DTO contenant la prédiction du modèle (machineCode, predictedStatus, confidence, etc.)
     * @return AiPredictionResponse avec les IDs de l'anomalie et de l'alerte créées
     */
    public AiPredictionResponse processPrediction(AiPredictionRequest request) {
        // 1. Validation des champs obligatoires
        if (!StringUtils.hasText(request.getMachineCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le champ machineCode est obligatoire");
        }
        if (!StringUtils.hasText(request.getPredictedStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le champ predictedStatus est obligatoire");
        }

        String machineCode = request.getMachineCode().trim();
        String predictedStatus = request.getPredictedStatus().trim().toLowerCase();
        String severity = resolveSeverity(request.getSeverity(), predictedStatus);
        double confidence = request.getConfidence() != null ? request.getConfidence() : 0.0;
        double anomalyScore = request.getAnomalyScore() != null ? request.getAnomalyScore() : 0.0;
        Instant predictionTime = parseTimestamp(request.getTimestamp());

        // 2. Résoudre machineCode → entité Machine
        Machine machine = machineRepository.findByCode(machineCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Machine introuvable avec le code : " + machineCode));

        String machineId = machine.getId();

        // 3. Filtrage : si le statut est "normal", résoudre les anomalies IA ouvertes
        if ("normal".equals(predictedStatus)) {
            resolveActiveAiAnomalies(machineId, machineCode);
            return new AiPredictionResponse(
                    null, null, machineId, machineCode, "INFO", "RESOLVED",
                    "Prédiction IA : état normal — aucune anomalie détectée", predictionTime
            );
        }

        // 4. Construire le message descriptif
        String message = buildPredictionMessage(machineCode, predictedStatus, confidence,
                anomalyScore, request.getTemperatureMean(), request.getVibrationRms());

        // 5. Déduplication : chercher une anomalie IA active pour cette machine
        Optional<Anomaly> activeAiAnomaly = findActiveAiAnomaly(machineId);

        Anomaly anomaly;
        if (activeAiAnomaly.isPresent()) {
            // Mettre à jour l'anomalie existante
            anomaly = activeAiAnomaly.get();
            anomaly.setLastOccurrence(predictionTime);
            anomaly.setObservedValue(confidence);
            anomaly.setOccurrenceCount(anomaly.getOccurrenceCount() + 1);
            anomaly.setMessage(message);
            anomaly.setUpdatedAt(Instant.now());

            // Escalade de sévérité si nécessaire
            if (isSeverityHigher(severity, anomaly.getSeverity())) {
                anomaly.setSeverity(severity);
            }
            anomaly = anomalyRepository.save(anomaly);
            log.debug("Anomalie IA mise à jour [{}] pour {} (occurrences: {}, confiance: {})",
                    anomaly.getId(), machineCode, anomaly.getOccurrenceCount(), confidence);
        } else {
            // Créer une nouvelle anomalie IA
            anomaly = new Anomaly(
                    machineId,
                    machineCode,
                    null,  // sensorId — pas de capteur spécifique, analyse multi-capteur
                    null,  // sensorType — idem
                    confidence,
                    anomalyScore,
                    THRESHOLD_TYPE_AI,
                    severity,
                    message,
                    predictionTime
            );
            anomaly = anomalyRepository.save(anomaly);
            log.warn("Nouvelle anomalie IA détectée [{}] pour {} : {} (confiance={}, anomaly_score={})",
                    severity, machineCode, predictedStatus, confidence, anomalyScore);
        }

        // 6. Créer ou mettre à jour l'alerte liée
        Alert alert = createOrUpdateAiAlert(anomaly, machine, severity, confidence, message, predictionTime);

        // 7. Diffusion WebSocket temps réel — prédiction IA
        broadcastAiPrediction(machine, predictedStatus, confidence, anomalyScore, severity,
                request.getTemperatureMean(), request.getVibrationRms(),
                request.getCurrentMean(), request.getRpmMean(), predictionTime,
                request.getEstimatedHoursToEvent(), request.getDrivingSensors(),
                request.getRecommendedFix());

        return new AiPredictionResponse(
                anomaly.getId(), alert != null ? alert.getId() : null,
                machineId, machineCode, severity, "OPEN", message, predictionTime
        );
    }

    /**
     * Recherche une anomalie IA active (OPEN ou ACKNOWLEDGED) pour une machine donnée.
     */
    private Optional<Anomaly> findActiveAiAnomaly(String machineId) {
        // Chercher parmi les anomalies actives de cette machine celles avec thresholdType AI_MODEL
        List<Anomaly> openAnomalies = anomalyRepository.findByMachineIdAndStatus(machineId, "OPEN");
        Optional<Anomaly> result = openAnomalies.stream()
                .filter(a -> THRESHOLD_TYPE_AI.equals(a.getThresholdType()))
                .findFirst();
        if (result.isPresent()) {
            return result;
        }
        // Vérifier aussi les anomalies acquittées
        List<Anomaly> acknowledgedAnomalies = anomalyRepository.findByMachineIdAndStatus(machineId, "ACKNOWLEDGED");
        return acknowledgedAnomalies.stream()
                .filter(a -> THRESHOLD_TYPE_AI.equals(a.getThresholdType()))
                .findFirst();
    }

    /**
     * Résout les anomalies IA ouvertes lorsque le modèle prédit un état "normal".
     */
    private void resolveActiveAiAnomalies(String machineId, String machineCode) {
        List<Anomaly> openAnomalies = anomalyRepository.findByMachineIdAndStatus(machineId, "OPEN");
        List<Anomaly> acknowledgedAnomalies = anomalyRepository.findByMachineIdAndStatus(machineId, "ACKNOWLEDGED");

        List<Anomaly> aiAnomalies = new java.util.ArrayList<>();
        openAnomalies.stream().filter(a -> THRESHOLD_TYPE_AI.equals(a.getThresholdType())).forEach(aiAnomalies::add);
        acknowledgedAnomalies.stream().filter(a -> THRESHOLD_TYPE_AI.equals(a.getThresholdType())).forEach(aiAnomalies::add);

        for (Anomaly anomaly : aiAnomalies) {
            anomaly.setStatus("RESOLVED");
            anomaly.setUpdatedAt(Instant.now());
            anomalyRepository.save(anomaly);

            // Résoudre l'alerte liée
            if (StringUtils.hasText(anomaly.getAlertId())) {
                alertRepository.findById(anomaly.getAlertId()).ifPresent(alert -> {
                    if (!"RESOLVED".equalsIgnoreCase(alert.getStatus())) {
                        alert.setStatus("RESOLVED");
                        alert.setResolvedBy("AI_MODEL");
                        alert.setResolvedAt(Instant.now());
                        alert.setComment("Retour à la normale détecté par le modèle IA");
                        alertRepository.save(alert);
                        realtimeMessageService.broadcastAlert(alert, machineCode);
                    }
                });
            }

            log.info("Anomalie IA [{}] résolue automatiquement pour {} (retour à la normale)", anomaly.getId(), machineCode);
        }
    }

    /**
     * Crée ou met à jour l'alerte liée à une anomalie IA.
     */
    private Alert createOrUpdateAiAlert(Anomaly anomaly, Machine machine,
                                        String severity, double confidence,
                                        String message, Instant timestamp) {
        String machineId = machine.getId();
        String machineCode = machine.getCode();

        // Déduplication : chercher une alerte IA active
        // On utilise l'anomalyId pour retrouver l'alerte liée
        if (StringUtils.hasText(anomaly.getAlertId())) {
            Optional<Alert> existingAlert = alertRepository.findById(anomaly.getAlertId());
            if (existingAlert.isPresent() && !"RESOLVED".equalsIgnoreCase(existingAlert.get().getStatus())) {
                Alert alert = existingAlert.get();
                alert.setSeverity(severity);
                alert.setValue(confidence);
                alert.setMessage(message);
                alert.setTimestamp(timestamp);
                Alert updated = alertRepository.save(alert);
                realtimeMessageService.broadcastAlert(updated, machineCode);
                return updated;
            }
        }

        // Créer une nouvelle alerte
        Alert newAlert = new Alert(
                machineId,
                machineCode,
                null,  // sensorId — analyse multi-capteur
                null,  // sensorType — idem
                anomaly.getId(),
                severity,
                confidence,
                anomaly.getThresholdValue(), // anomalyScore comme seuil de référence
                message,
                timestamp
        );

        Alert saved = alertRepository.save(newAlert);

        // Liaison bidirectionnelle
        anomaly.setAlertId(saved.getId());
        anomalyRepository.save(anomaly);

        // Diffusion WebSocket
        realtimeMessageService.broadcastAlert(saved, machineCode);

        // Notification FCM / in-app
        try {
            notificationService.notifyAlert(saved);
        } catch (Exception ex) {
            log.error("Échec non-bloquant de notification pour alerte IA {} : {}", saved.getId(), ex.getMessage());
        }

        log.info("Alerte IA créée [{}] pour machine {} : {}", severity, machineCode, message);
        return saved;
    }

    /**
     * Diffuse la prédiction IA via WebSocket STOMP vers les clients connectés.
     * Topics : /topic/ai-predictions et /topic/machines/{machineId}/ai-prediction
     */
    private void broadcastAiPrediction(Machine machine, String predictedStatus,
                                       double confidence, double anomalyScore, String severity,
                                       Double temperatureMean, Double vibrationRms,
                                       Double currentMean, Double rpmMean, Instant timestamp,
                                       Double estimatedHoursToEvent,
                                       java.util.List<java.util.Map<String, Object>> drivingSensors,
                                       String recommendedFix) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventType", "AI_PREDICTION");
        payload.put("machineId", machine.getId());
        payload.put("machineCode", machine.getCode());
        payload.put("predictedStatus", predictedStatus);
        payload.put("confidence", confidence);
        payload.put("anomalyScore", anomalyScore);
        payload.put("severity", severity);
        payload.put("temperatureMean", temperatureMean);
        payload.put("vibrationRms", vibrationRms);
        payload.put("currentMean", currentMean);
        payload.put("rpmMean", rpmMean);
        payload.put("timestamp", timestamp != null ? timestamp.toString() : Instant.now().toString());
        // Sprint 9 — estimation temporelle et capteurs moteurs
        payload.put("estimatedHoursToEvent", estimatedHoursToEvent);
        payload.put("drivingSensors", drivingSensors);
        // Sprint 10 — suggestion de réparation actionnable
        payload.put("recommendedFix", recommendedFix);

        try {
            // Topic global pour toutes les prédictions IA
            realtimeMessageService.getMessagingTemplate()
                    .convertAndSend("/topic/ai-predictions", payload);

            // Topic spécifique à la machine
            realtimeMessageService.getMessagingTemplate()
                    .convertAndSend("/topic/machines/" + machine.getId() + "/ai-prediction", payload);

            if (StringUtils.hasText(machine.getCode()) && !machine.getCode().equals(machine.getId())) {
                realtimeMessageService.getMessagingTemplate()
                        .convertAndSend("/topic/machines/" + machine.getCode() + "/ai-prediction", payload);
            }

            log.debug("Prédiction IA diffusée pour {} : {} (confiance={})", machine.getCode(), predictedStatus, confidence);
        } catch (Exception ex) {
            log.error("Échec de diffusion WebSocket pour prédiction IA machine {} : {}",
                    machine.getCode(), ex.getMessage());
        }
    }

    /**
     * Construit le message descriptif de la prédiction IA.
     */
    private String buildPredictionMessage(String machineCode, String predictedStatus,
                                          double confidence, double anomalyScore,
                                          Double temperatureMean, Double vibrationRms) {
        StringBuilder sb = new StringBuilder();
        sb.append("Prédiction IA pour ").append(machineCode).append(" : ");
        sb.append(translateStatus(predictedStatus));
        sb.append(String.format(" (confiance: %.1f%%", confidence * 100));
        sb.append(String.format(", score anomalie: %.2f", anomalyScore));
        if (temperatureMean != null) {
            sb.append(String.format(", temp: %.1f°C", temperatureMean));
        }
        if (vibrationRms != null) {
            sb.append(String.format(", vibration: %.2f mm/s", vibrationRms));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Traduit le statut prédit en libellé français pour les messages.
     */
    private String translateStatus(String status) {
        return switch (status.toLowerCase()) {
            case "degradation" -> "Dégradation détectée";
            case "anomaly" -> "Anomalie détectée";
            case "failure" -> "Défaillance imminente";
            default -> status;
        };
    }

    /**
     * Résout la sévérité à partir du champ severity du payload ou du statut prédit.
     */
    private String resolveSeverity(String requestSeverity, String predictedStatus) {
        if (StringUtils.hasText(requestSeverity)) {
            String upper = requestSeverity.trim().toUpperCase();
            if (List.of("INFO", "WARNING", "HIGH", "CRITICAL").contains(upper)) {
                return upper;
            }
        }
        // Fallback : déduire du statut prédit
        return switch (predictedStatus.toLowerCase()) {
            case "degradation" -> "WARNING";
            case "anomaly" -> "HIGH";
            case "failure" -> "CRITICAL";
            default -> "INFO";
        };
    }

    /**
     * Vérifie si la nouvelle sévérité est plus élevée que l'actuelle.
     */
    private boolean isSeverityHigher(String newSeverity, String currentSeverity) {
        int newLevel = severityLevel(newSeverity);
        int currentLevel = severityLevel(currentSeverity);
        return newLevel > currentLevel;
    }

    private int severityLevel(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase()) {
            case "INFO" -> 1;
            case "WARNING" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    /**
     * Parse un timestamp ISO 8601 ou retourne l'heure courante.
     */
    private Instant parseTimestamp(String timestamp) {
        if (!StringUtils.hasText(timestamp)) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            log.warn("Format de timestamp invalide '{}', utilisation de l'heure courante", timestamp);
            return Instant.now();
        }
    }
}

