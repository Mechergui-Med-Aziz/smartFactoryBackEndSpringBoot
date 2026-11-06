package com.smartfactory.dto;

import java.time.Instant;

/**
 * DTO de réponse après traitement d'une prédiction IA (Sprint 8 - US AI).
 *
 * Retourné au service Python d'inférence pour confirmer
 * l'enregistrement de l'anomalie et de l'alerte associée.
 */
public class AiPredictionResponse {

    private String anomalyId;
    private String alertId;
    private String machineId;
    private String machineCode;
    private String severity;
    private String status;
    private String message;
    private Instant timestamp;

    public AiPredictionResponse() {
    }

    public AiPredictionResponse(String anomalyId, String alertId, String machineId,
                                String machineCode, String severity, String status,
                                String message, Instant timestamp) {
        this.anomalyId = anomalyId;
        this.alertId = alertId;
        this.machineId = machineId;
        this.machineCode = machineCode;
        this.severity = severity;
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
    }

    // --- Getters & Setters ---

    public String getAnomalyId() {
        return anomalyId;
    }

    public void setAnomalyId(String anomalyId) {
        this.anomalyId = anomalyId;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getMachineCode() {
        return machineCode;
    }

    public void setMachineCode(String machineCode) {
        this.machineCode = machineCode;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

