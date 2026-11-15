package com.smartfactory.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO pour la réception des prédictions du modèle IA SmartFactory (Sprint 8/9 - US AI).
 *
 * Payload envoyé par le service Python d'inférence temps réel
 * (health-monitor-model) vers le backend Spring Boot.
 */
public class AiPredictionRequest {

    private String machineCode;

    /** Statut prédit par le modèle : normal, degradation, anomaly, failure */
    private String predictedStatus;

    /** Confiance du classifieur RandomForest [0..1] */
    private Double confidence;

    /** Score d'anomalie IsolationForest [0..1] */
    private Double anomalyScore;

    /** Sévérité mappée : INFO, WARNING, HIGH, CRITICAL */
    private String severity;

    /** Valeur moyenne de température sur la fenêtre d'analyse */
    private Double temperatureMean;

    /** RMS de vibration sur la fenêtre d'analyse */
    private Double vibrationRms;

    /** Valeur moyenne du courant sur la fenêtre d'analyse */
    private Double currentMean;

    /** Valeur moyenne du RPM sur la fenêtre d'analyse */
    private Double rpmMean;

    /** Horodatage de la prédiction (ISO 8601) */
    private String timestamp;

    /** Sprint 9 — Estimation du temps avant l'événement prédit (heures) */
    private Double estimatedHoursToEvent;

    /** Sprint 9 — Capteurs moteurs de la prédiction avec détails de tendance */
    private List<Map<String, Object>> drivingSensors;

    /** Sprint 10 — Suggestion de réparation actionnable pour les techniciens */
    private String recommendedFix;

    public AiPredictionRequest() {
    }

    // --- Getters & Setters ---

    public String getMachineCode() {
        return machineCode;
    }

    public void setMachineCode(String machineCode) {
        this.machineCode = machineCode;
    }

    public String getPredictedStatus() {
        return predictedStatus;
    }

    public void setPredictedStatus(String predictedStatus) {
        this.predictedStatus = predictedStatus;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Double getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(Double anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getTemperatureMean() {
        return temperatureMean;
    }

    public void setTemperatureMean(Double temperatureMean) {
        this.temperatureMean = temperatureMean;
    }

    public Double getVibrationRms() {
        return vibrationRms;
    }

    public void setVibrationRms(Double vibrationRms) {
        this.vibrationRms = vibrationRms;
    }

    public Double getCurrentMean() {
        return currentMean;
    }

    public void setCurrentMean(Double currentMean) {
        this.currentMean = currentMean;
    }

    public Double getRpmMean() {
        return rpmMean;
    }

    public void setRpmMean(Double rpmMean) {
        this.rpmMean = rpmMean;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Double getEstimatedHoursToEvent() {
        return estimatedHoursToEvent;
    }

    public void setEstimatedHoursToEvent(Double estimatedHoursToEvent) {
        this.estimatedHoursToEvent = estimatedHoursToEvent;
    }

    public List<Map<String, Object>> getDrivingSensors() {
        return drivingSensors;
    }

    public void setDrivingSensors(List<Map<String, Object>> drivingSensors) {
        this.drivingSensors = drivingSensors;
    }

    public String getRecommendedFix() {
        return recommendedFix;
    }

    public void setRecommendedFix(String recommendedFix) {
        this.recommendedFix = recommendedFix;
    }
}

