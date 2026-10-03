package com.smartfactory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 2, max = 100, message = "Group name must be between 2 and 100 characters")
    private String name;

    private List<String> operators = new ArrayList<>();

    private String supervisorId;

    public CreateGroupRequest() {
    }

    public CreateGroupRequest(String name, List<String> operators, String supervisorId) {
        this.name = name;
        this.operators = operators != null ? operators : new ArrayList<>();
        this.supervisorId = supervisorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getOperators() {
        if (operators == null) {
            operators = new ArrayList<>();
        }
        return operators;
    }

    public void setOperators(List<String> operators) {
        this.operators = operators != null ? operators : new ArrayList<>();
    }

    public String getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(String supervisorId) {
        this.supervisorId = supervisorId;
    }
}
