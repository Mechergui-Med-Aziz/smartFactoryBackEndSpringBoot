package com.smartfactory.entity;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "machines")
public class Machine {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String code; // RB01 unique business code e.g. CNC-024

    private String type;

    @Indexed
    private String zoneId;

    private MachineStatus status = MachineStatus.IDLE;

    private String description;

    private List<MachineCharacteristic> caracteristiques = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Machine() {
    }

    public Machine(String name, String code, String type, String zoneId, MachineStatus status, String description, List<MachineCharacteristic> caracteristiques) {
        this.name = name;
        this.code = code;
        this.type = type;
        this.zoneId = zoneId;
        this.status = status != null ? status : MachineStatus.IDLE;
        this.description = description;
        this.caracteristiques = caracteristiques != null ? caracteristiques : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public MachineStatus getStatus() {
        return status;
    }

    public void setStatus(MachineStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<MachineCharacteristic> getCaracteristiques() {
        return caracteristiques;
    }

    public void setCaracteristiques(List<MachineCharacteristic> caracteristiques) {
        this.caracteristiques = caracteristiques;
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
