package com.smartfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.dto.request.LoginRequest;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

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

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = new User(
                "John",
                "Doe",
                "john.doe@smartfactory.com",
                passwordEncoder.encode("Password123!"),
                Role.OPERATOR,
                "ACTIVE"
        );
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("US01: Successful login returns 200 with JWT and user profile")
    void testSuccessfulLogin() throws Exception {
        LoginRequest request = new LoginRequest("john.doe@smartfactory.com", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("john.doe@smartfactory.com")))
                .andExpect(jsonPath("$.user.role", is("OPERATOR")))
                .andExpect(jsonPath("$.user.firstName", is("John")))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("US01: Login with invalid email returns 401 INVALID_CREDENTIALS")
    void testLoginWithWrongEmail() throws Exception {
        LoginRequest request = new LoginRequest("wrong.email@smartfactory.com", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("US01: Login with invalid password returns 401 INVALID_CREDENTIALS")
    void testLoginWithWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest("john.doe@smartfactory.com", "WrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("US01: Access protected route without token returns 401 UNAUTHORIZED")
    void testAccessProtectedRouteWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("US01: Access protected route with invalid token returns 401 UNAUTHORIZED")
    void testAccessProtectedRouteWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("US01: Access protected route with valid token returns current user")
    void testAccessProtectedRouteWithValidToken() throws Exception {
        String token = jwtService.generateToken(testUser);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("john.doe@smartfactory.com")))
                .andExpect(jsonPath("$.role", is("OPERATOR")));
    }

    @Test
    @DisplayName("US02: Logout returns 200 with success message")
    void testLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Successfully logged out")));
    }
}
