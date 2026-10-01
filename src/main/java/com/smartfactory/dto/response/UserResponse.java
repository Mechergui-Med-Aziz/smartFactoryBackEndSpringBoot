package com.smartfactory.dto.response;

import com.smartfactory.security.Role;

import java.time.Instant;

public class UserResponse {

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private String status;
    private boolean emailVerified;
    private Instant createdAt;
    private Instant updatedAt;

    public UserResponse() {
    }

    public UserResponse(String id, String firstName, String lastName, String email, Role role, String status, Instant createdAt, Instant updatedAt) {
        this(id, firstName, lastName, email, role, status, true, createdAt, updatedAt);
    }

    public UserResponse(String id, String firstName, String lastName, String email, Role role, String status, boolean emailVerified, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
        this.status = status;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
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
