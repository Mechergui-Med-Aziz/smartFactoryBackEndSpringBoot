package com.smartfactory.entity;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "alerts")
public class Alert {

    @Id
    private String id;

    @Indexed
    private String machineId;

    @Indexed
    private String sensorId;

    private SensorType sensorType;

    private String severity; // WARNING, CRITICAL

    private Double value;

    private Double threshold;

    private String status = "ACTIVE"; // ACTIVE, ACKNOWLEDGED, RESOLVED

    private String message;

    private Instant timestamp;

    @CreatedDate
    private Instant createdAt;

    public Alert() {
    }

    public Alert(String machineId, String sensorId, SensorType sensorType, String severity, Double value, Double threshold, String message, Instant timestamp) {
        this.machineId = machineId;
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.severity = severity;
        this.value = value;
        this.threshold = threshold;
        this.message = message;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.status = "ACTIVE";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public void setSensorType(SensorType sensorType) {
        this.sensorType = sensorType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
