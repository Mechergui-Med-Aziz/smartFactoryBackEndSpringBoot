package com.smartfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.entity.User;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.JwtService;
import com.smartfactory.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User operatorUser;
    private User technicianUser;
    private User responsableUser;

    private String adminToken;
    private String operatorToken;
    private String technicianToken;
    private String responsableToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        adminUser = userRepository.save(new User("Admin", "User", "admin@smartfactory.com", passwordEncoder.encode("pass123"), Role.ADMIN, "ACTIVE"));
        operatorUser = userRepository.save(new User("Operator", "User", "operator@smartfactory.com", passwordEncoder.encode("pass123"), Role.OPERATOR, "ACTIVE"));
        technicianUser = userRepository.save(new User("Tech", "User", "tech@smartfactory.com", passwordEncoder.encode("pass123"), Role.TECHNICIAN, "ACTIVE"));
        responsableUser = userRepository.save(new User("Resp", "User", "resp@smartfactory.com", passwordEncoder.encode("pass123"), Role.RESPONSABLE_INDUSTRIEL, "ACTIVE"));

        adminToken = jwtService.generateToken(adminUser);
        operatorToken = jwtService.generateToken(operatorUser);
        technicianToken = jwtService.generateToken(technicianUser);
        responsableToken = jwtService.generateToken(responsableUser);
    }

    @Test
    @DisplayName("US03 & US05: ADMIN can list all users with pagination")
    void testAdminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.totalElements", is(4)))
                .andExpect(jsonPath("$.page", is(0)));
    }

    @Test
    @DisplayName("US03 & US04: ADMIN can create a new user with TECHNICIAN role")
    void testAdminCanCreateUser() throws Exception {
        Map<String, Object> request = Map.of(
                "firstName", "Lucas",
                "lastName", "Martin",
                "email", "lucas.martin@smartfactory.com",
                "password", "securePass123",
                "role", Role.TECHNICIAN,
                "status", "ACTIVE"
        );

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("lucas.martin@smartfactory.com")))
                .andExpect(jsonPath("$.role", is("TECHNICIAN")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("US03: Creation fails with 400 when validation fails (invalid email)")
    void testCreateUserValidationFailure() throws Exception {
        User request = new User(
                "Invalid",
                "User",
                "not-an-email",
                "short",
                Role.OPERATOR,
                "ACTIVE"
        );

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("US03: Creation fails with 409 when email already exists")
    void testCreateUserDuplicateEmail() throws Exception {
        User request = new User(
                "Duplicate",
                "Admin",
                "admin@smartfactory.com",
                "password123",
                Role.ADMIN,
                "ACTIVE"
        );

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("USER_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("US03: ADMIN can get user by ID")
    void testGetUserById() throws Exception {
        mockMvc.perform(get("/api/users/" + operatorUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(operatorUser.getId())))
                .andExpect(jsonPath("$.email", is("operator@smartfactory.com")));
    }

    @Test
    @DisplayName("US03: Get non-existent user returns 404 USER_NOT_FOUND")
    void testGetNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/users/non-existent-id")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("US03 & US04: ADMIN can update user role and details")
    void testUpdateUser() throws Exception {
        User updateRequest = new User(
                "UpdatedOperator",
                "User",
                "operator.updated@smartfactory.com",
                null,
                Role.RESPONSABLE_INDUSTRIEL,
                "ACTIVE"
        );

        mockMvc.perform(put("/api/users/" + operatorUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("UpdatedOperator")))
                .andExpect(jsonPath("$.role", is("RESPONSABLE_INDUSTRIEL")));
    }

    @Test
    @DisplayName("US03: ADMIN can delete user")
    void testDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/users/" + operatorUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + operatorUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US03: Search user by keyword returns matching results")
    void testSearchUser() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email", is("tech@smartfactory.com")));
    }

    @Test
    @DisplayName("US05: Non-ADMIN (OPERATOR) receives 403 FORBIDDEN when accessing /api/users")
    void testOperatorForbiddenAccess() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("US05: Non-ADMIN (TECHNICIAN) receives 403 FORBIDDEN when accessing /api/users")
    void testTechnicianForbiddenAccess() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("US05: Non-ADMIN (RESPONSABLE_INDUSTRIEL) receives 403 FORBIDDEN when accessing /api/users")
    void testResponsableForbiddenAccess() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + responsableToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("US05: Unauthenticated access to /api/users returns 401 UNAUTHORIZED")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }
}
