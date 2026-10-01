package com.smartfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.dto.request.CreateMachineRequest;
import com.smartfactory.dto.request.UpdateMachineRequest;
import com.smartfactory.entity.Machine;
import com.smartfactory.entity.MachineCharacteristic;
import com.smartfactory.entity.MachineStatus;
import com.smartfactory.entity.User;
import com.smartfactory.entity.Zone;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.repository.ZoneRepository;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MachineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String operatorToken;
    private Zone zoneA;
    private Machine machine1;

    @BeforeEach
    void setUp() {
        machineRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User("Admin", "User", "admin@smartfactory.com", passwordEncoder.encode("pass"), Role.ADMIN, "ACTIVE"));
        User operator = userRepository.save(new User("Operator", "User", "op@smartfactory.com", passwordEncoder.encode("pass"), Role.OPERATOR, "ACTIVE"));

        adminToken = jwtService.generateToken(admin);
        operatorToken = jwtService.generateToken(operator);

        zoneA = zoneRepository.save(new Zone("Zone A", "Production line 1", "Building 1"));

        machine1 = new Machine(
                "CNC Milling Machine",
                "CNC-024",
                "CNC Milling",
                zoneA.getId(),
                MachineStatus.RUNNING,
                "Precision milling machine",
                List.of(new MachineCharacteristic("Power", "15kW", "Electrical"))
        );
        machine1 = machineRepository.save(machine1);
    }

    @Test
    @DisplayName("US06: ADMIN can create a machine with valid code (RB01)")
    void testAdminCreateMachine() throws Exception {
        CreateMachineRequest request = new CreateMachineRequest(
                "Hydraulic Press",
                "PRS-001",
                "Press",
                zoneA.getId(),
                MachineStatus.IDLE,
                "Heavy duty press",
                List.of(new MachineCharacteristic("Pressure", "200bar", "Mechanical"))
        );

        mockMvc.perform(post("/api/machines")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.code", is("PRS-001")))
                .andExpect(jsonPath("$.name", is("Hydraulic Press")))
                .andExpect(jsonPath("$.zoneName", is("Zone A")));
    }

    @Test
    @DisplayName("US06: Machine creation fails with 409 when code is duplicate (RB01)")
    void testCreateMachineDuplicateCode() throws Exception {
        CreateMachineRequest request = new CreateMachineRequest(
                "Duplicate Machine",
                "CNC-024",
                "CNC",
                zoneA.getId(),
                MachineStatus.IDLE,
                "Desc",
                null
        );

        mockMvc.perform(post("/api/machines")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("MACHINE_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("US06: Machine creation fails with 400 when validation fails")
    void testCreateMachineValidationFailure() throws Exception {
        CreateMachineRequest request = new CreateMachineRequest("", "", "", null, null, null, null);

        mockMvc.perform(post("/api/machines")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("US06: Non-ADMIN cannot create a machine (403 FORBIDDEN)")
    void testOperatorCannotCreateMachine() throws Exception {
        CreateMachineRequest request = new CreateMachineRequest(
                "New Machine",
                "NMC-001",
                "Type",
                null,
                MachineStatus.IDLE,
                null,
                null
        );

        mockMvc.perform(post("/api/machines")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("US07: ADMIN can update a machine")
    void testAdminUpdateMachine() throws Exception {
        UpdateMachineRequest request = new UpdateMachineRequest(
                "Updated CNC Machine",
                "CNC-024",
                "CNC Milling High Precision",
                zoneA.getId(),
                MachineStatus.MAINTENANCE,
                "Scheduled checkup",
                List.of(new MachineCharacteristic("Speed", "3000rpm", "Mechanical"))
        );

        mockMvc.perform(put("/api/machines/" + machine1.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated CNC Machine")))
                .andExpect(jsonPath("$.status", is("MAINTENANCE")));
    }

    @Test
    @DisplayName("US07: Non-ADMIN cannot update a machine (403 FORBIDDEN)")
    void testOperatorCannotUpdateMachine() throws Exception {
        UpdateMachineRequest request = new UpdateMachineRequest(
                "Updated CNC Machine",
                "CNC-024",
                "CNC Milling",
                zoneA.getId(),
                MachineStatus.MAINTENANCE,
                "Desc",
                null
        );

        mockMvc.perform(put("/api/machines/" + machine1.getId())
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("US08: Authorized user (OPERATOR) can view machine details")
    void testOperatorCanViewMachineDetails() throws Exception {
        mockMvc.perform(get("/api/machines/" + machine1.getId())
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(machine1.getId())))
                .andExpect(jsonPath("$.code", is("CNC-024")))
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andExpect(jsonPath("$.caracteristiques", hasSize(1)));
    }

    @Test
    @DisplayName("US08: View non-existent machine returns 404 MACHINE_NOT_FOUND")
    void testViewNonExistentMachine() throws Exception {
        mockMvc.perform(get("/api/machines/non-existent-id")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("MACHINE_NOT_FOUND")));
    }

    @Test
    @DisplayName("US08: List machines with pagination and filtering")
    void testListMachinesWithFilters() throws Exception {
        mockMvc.perform(get("/api/machines")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("status", "RUNNING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("US08: Get machine sensors returns empty list in Sprint 1")
    void testGetMachineSensors() throws Exception {
        mockMvc.perform(get("/api/machines/" + machine1.getId() + "/sensors")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
    }

    @Test
    @DisplayName("US07: ADMIN can delete machine")
    void testDeleteMachine() throws Exception {
        mockMvc.perform(delete("/api/machines/" + machine1.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/machines/" + machine1.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
