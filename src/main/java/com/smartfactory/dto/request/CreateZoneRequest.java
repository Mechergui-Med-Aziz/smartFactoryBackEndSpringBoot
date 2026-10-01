package com.smartfactory.dto.request;

import jakarta.validation.constraints.NotBlank;

public class CreateZoneRequest {

    @NotBlank(message = "Zone name is required")
    private String name;

    private String description;

    private String location;

    public CreateZoneRequest() {
    }

    public CreateZoneRequest(String name, String description, String location) {
        this.name = name;
        this.description = description;
        this.location = location;
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
}
