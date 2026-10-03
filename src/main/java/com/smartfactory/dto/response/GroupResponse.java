package com.smartfactory.dto.response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GroupResponse {

    private String id;
    private String name;
    private List<String> operators = new ArrayList<>();
    private String supervisorId;
    private String supervisorName;
    private int operatorCount;
    private Instant createdAt;
    private Instant updatedAt;

    public GroupResponse() {
    }

    public GroupResponse(String id, String name, List<String> operators, String supervisorId,
                         String supervisorName, int operatorCount, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.operators = operators != null ? operators : new ArrayList<>();
        this.supervisorId = supervisorId;
        this.supervisorName = supervisorName;
        this.operatorCount = operatorCount;
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

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }

    public int getOperatorCount() {
        return operatorCount;
    }

    public void setOperatorCount(int operatorCount) {
        this.operatorCount = operatorCount;
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
