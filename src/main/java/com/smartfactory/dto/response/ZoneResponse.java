package com.smartfactory.dto.response;

import java.time.Instant;

public class ZoneResponse {

    private String id;
    private String name;
    private String description;
    private String location;
    private long machineCount;
    private Instant createdAt;
    private Instant updatedAt;

    public ZoneResponse() {
    }

    public ZoneResponse(String id, String name, String description, String location, long machineCount, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.location = location;
        this.machineCount = machineCount;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public long getMachineCount() {
        return machineCount;
    }

    public void setMachineCount(long machineCount) {
        this.machineCount = machineCount;
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
