package com.smartfactory.dto.response;

import com.smartfactory.entity.MachineCharacteristic;
import com.smartfactory.entity.MachineStatus;

import java.time.Instant;
import java.util.List;

public class MachineResponse {

    private String id;
    private String name;
    private String code;
    private String type;
    private String zoneId;
    private String zoneName;
    private MachineStatus status;
    private String description;
    private List<MachineCharacteristic> caracteristiques;
    private Instant createdAt;
    private Instant updatedAt;

    public MachineResponse() {
    }

    public MachineResponse(String id, String name, String code, String type, String zoneId, String zoneName,
                           MachineStatus status, String description, List<MachineCharacteristic> caracteristiques,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.type = type;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.status = status;
        this.description = description;
        this.caracteristiques = caracteristiques;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
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
