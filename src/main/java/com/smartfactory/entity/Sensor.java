package com.smartfactory.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "sensors")
@CompoundIndex(name = "machine_sensor_type_idx", def = "{'machineId': 1, 'type': 1}", unique = true)
public class Sensor {

    @Id
    private String id;

    @Indexed
    @NotBlank(message = "Machine ID is required")
    private String machineId;

    @NotNull(message = "Sensor type is required")
    private SensorType type;

    private String unit;

    private SensorStatus status = SensorStatus.ONLINE;

    private Instant lastSeen;

    private Long timeoutSeconds = 30L;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Sensor() {
    }

    public Sensor(String machineId, SensorType type, String unit) {
        this.machineId = machineId;
        this.type = type;
        this.unit = unit != null ? unit : (type != null ? type.getDefaultUnit() : null);
        this.status = SensorStatus.ONLINE;
        this.lastSeen = Instant.now();
        this.timeoutSeconds = 30L;
    }

    public Sensor(String machineId, SensorType type, String unit, SensorStatus status, Long timeoutSeconds) {
        this.machineId = machineId;
        this.type = type;
        this.unit = unit != null ? unit : (type != null ? type.getDefaultUnit() : null);
        this.status = status != null ? status : SensorStatus.ONLINE;
        this.lastSeen = Instant.now();
        this.timeoutSeconds = timeoutSeconds != null ? timeoutSeconds : 30L;
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

    public SensorType getType() {
        return type;
    }

    public void setType(SensorType type) {
        this.type = type;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public SensorStatus getStatus() {
        return status;
    }

    public void setStatus(SensorStatus status) {
        this.status = status;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
