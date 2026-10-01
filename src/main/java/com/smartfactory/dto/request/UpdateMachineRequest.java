package com.smartfactory.dto.request;

import com.smartfactory.entity.MachineCharacteristic;
import com.smartfactory.entity.MachineStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class UpdateMachineRequest {

    @NotBlank(message = "Machine name is required")
    private String name;

    @NotBlank(message = "Machine code is required")
    private String code; // RB01

    @NotBlank(message = "Machine type is required")
    private String type;

    private String zoneId;

    @NotNull(message = "Machine status is required")
    private MachineStatus status;

    private String description;

    private List<MachineCharacteristic> caracteristiques = new ArrayList<>();

    public UpdateMachineRequest() {
    }

    public UpdateMachineRequest(String name, String code, String type, String zoneId, MachineStatus status, String description, List<MachineCharacteristic> caracteristiques) {
        this.name = name;
        this.code = code;
        this.type = type;
        this.zoneId = zoneId;
        this.status = status;
        this.description = description;
        this.caracteristiques = caracteristiques != null ? caracteristiques : new ArrayList<>();
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
}
