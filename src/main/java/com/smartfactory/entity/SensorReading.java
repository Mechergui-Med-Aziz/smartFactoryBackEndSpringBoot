package com.smartfactory.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "sensor_readings")
@CompoundIndexes({
        @CompoundIndex(name = "machine_ts_idx", def = "{'machineId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "machine_type_ts_idx", def = "{'machineId': 1, 'sensorType': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "machine_sensor_ts_idx", def = "{'machineId': 1, 'sensorId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "dedup_idx", def = "{'machineId': 1, 'sensorId': 1, 'timestamp': 1}")
})
public class SensorReading {

    @Id
    private String id;

    @Indexed
    @NotBlank(message = "Machine ID is required")
    private String machineId;

    @Indexed
    @NotBlank(message = "Sensor ID is required")
    private String sensorId;

    @NotNull(message = "Sensor type is required")
    private SensorType sensorType;

    @NotNull(message = "Measurement value is required")
    private Double value;

    @NotBlank(message = "Measurement unit is required")
    private String unit;

    @Indexed
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    private Instant serverReceivedAt;

    public SensorReading() {
    }

    public SensorReading(String machineId, String sensorId, SensorType sensorType, Double value, String unit, Instant timestamp) {
        this.machineId = machineId;
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
        this.serverReceivedAt = Instant.now();
    }

    public SensorReading(String machineId, String sensorId, SensorType sensorType, Double value, String unit, Instant timestamp, Instant serverReceivedAt) {
        this.machineId = machineId;
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
        this.serverReceivedAt = serverReceivedAt != null ? serverReceivedAt : Instant.now();
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

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getServerReceivedAt() {
        return serverReceivedAt;
    }

    public void setServerReceivedAt(Instant serverReceivedAt) {
        this.serverReceivedAt = serverReceivedAt;
    }
}
