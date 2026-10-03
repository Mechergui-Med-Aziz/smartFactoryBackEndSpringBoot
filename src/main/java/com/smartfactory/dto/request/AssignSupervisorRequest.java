package com.smartfactory.dto.request;

import jakarta.validation.constraints.NotBlank;

public class AssignSupervisorRequest {

    @NotBlank(message = "Supervisor ID is required")
    private String supervisorId;

    public AssignSupervisorRequest() {
    }

    public AssignSupervisorRequest(String supervisorId) {
        this.supervisorId = supervisorId;
    }

    public String getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(String supervisorId) {
        this.supervisorId = supervisorId;
    }
}
