package com.smartfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.entity.Machine;
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

import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ZoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private MachineRepository machineRepository;

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
    private Zone zone1;

    @BeforeEach
    void setUp() {
        machineRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User("Admin", "User", "admin@smartfactory.com", passwordEncoder.encode("pass"), Role.ADMIN, "ACTIVE"));
        User operator = userRepository.save(new User("Operator", "User", "op@smartfactory.com", passwordEncoder.encode("pass"), Role.OPERATOR, "ACTIVE"));

        adminToken = jwtService.generateToken(admin);
        operatorToken = jwtService.generateToken(operator);

        zone1 = zoneRepository.save(new Zone("Zone A", "Assembly Zone", "Hangar 1"));
    }

    @Test
    @DisplayName("US09: ADMIN can create a zone")
    void testCreateZone() throws Exception {
        Zone request = new Zone("Zone B", "Milling Zone", "Hangar 2");

        mockMvc.perform(post("/api/zones")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Zone B")))
                .andExpect(jsonPath("$.machineCount", is(0)));
    }

    @Test
    @DisplayName("US09: Duplicate zone name returns 409 ZONE_ALREADY_EXISTS")
    void testCreateDuplicateZone() throws Exception {
        Zone request = new Zone("Zone A", "Duplicate", "Hangar 1");

        mockMvc.perform(post("/api/zones")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ZONE_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("US09: Non-ADMIN cannot create zone (403 FORBIDDEN)")
    void testOperatorCannotCreateZone() throws Exception {
        Zone request = new Zone("Zone C", "Packaging", "Hangar 3");

        mockMvc.perform(post("/api/zones")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("US09: Authenticated user can list all zones with machine count")
    void testGetAllZones() throws Exception {
        // Add a machine to zone1
        machineRepository.save(new Machine("M1", "M1-001", "Type", zone1.getId(), MachineStatus.RUNNING, "Desc", Collections.emptyList()));

        mockMvc.perform(get("/api/zones")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Zone A")))
                .andExpect(jsonPath("$[0].machineCount", is(1)));
    }

    @Test
    @DisplayName("US09: Get zone by ID returns zone details")
    void testGetZoneById() throws Exception {
        mockMvc.perform(get("/api/zones/" + zone1.getId())
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(zone1.getId())))
                .andExpect(jsonPath("$.name", is("Zone A")));
    }

    @Test
    @DisplayName("US09: Get non-existent zone returns 404 ZONE_NOT_FOUND")
    void testGetNonExistentZone() throws Exception {
        mockMvc.perform(get("/api/zones/non-existent-zone")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ZONE_NOT_FOUND")));
    }

    @Test
    @DisplayName("US09: ADMIN can update a zone")
    void testUpdateZone() throws Exception {
        Zone request = new Zone("Zone A Prime", "Updated Description", "New Location");

        mockMvc.perform(put("/api/zones/" + zone1.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Zone A Prime")))
                .andExpect(jsonPath("$.description", is("Updated Description")));
    }

    @Test
    @DisplayName("US09: ADMIN can delete a zone")
    void testDeleteZone() throws Exception {
        mockMvc.perform(delete("/api/zones/" + zone1.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/zones/" + zone1.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US09: Get machines belonging to a zone (/api/zones/{id}/machines)")
    void testGetMachinesByZone() throws Exception {
        machineRepository.save(new Machine("M1", "M1-001", "Type1", zone1.getId(), MachineStatus.RUNNING, "Desc", Collections.emptyList()));
        machineRepository.save(new Machine("M2", "M2-002", "Type2", zone1.getId(), MachineStatus.IDLE, "Desc", Collections.emptyList()));

        mockMvc.perform(get("/api/zones/" + zone1.getId() + "/machines")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].zoneName", is("Zone A")))
                .andExpect(jsonPath("$[1].zoneName", is("Zone A")));
    }
}
