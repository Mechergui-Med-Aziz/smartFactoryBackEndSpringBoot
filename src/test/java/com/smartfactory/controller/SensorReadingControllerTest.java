package com.smartfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.entity.*;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.SensorReadingRepository;
import com.smartfactory.repository.SensorRepository;
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

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SensorReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository readingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Machine machine;
    private Sensor sensor;

    @BeforeEach
    void setUp() {
        readingRepository.deleteAll();
        sensorRepository.deleteAll();
        machineRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(new User("Operator", "User", "op@factory.com", passwordEncoder.encode("pass"), Role.OPERATOR, "ACTIVE"));
        token = jwtService.generateToken(user);

        machine = machineRepository.save(new Machine("Fraiseuse CNC", "CNC-024", "Fraiseuse", "zone-1", MachineStatus.RUNNING, "Desc", List.of()));
        sensor = sensorRepository.save(new Sensor(machine.getId(), SensorType.TEMPERATURE, "°C"));
    }

    @Test
    @DisplayName("US18: GET /api/machines/{id}/readings returns paginated sensor readings")
    void testGetMachineReadings() throws Exception {
        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 42.0, "°C", Instant.parse("2026-09-28T10:00:00Z")));
        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 43.5, "°C", Instant.parse("2026-09-28T11:00:00Z")));

        mockMvc.perform(get("/api/machines/" + machine.getId() + "/readings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].value", is(43.5)))
                .andExpect(jsonPath("$.content[0].sensorType", is("TEMPERATURE")));
    }

    @Test
    @DisplayName("US18: GET /api/machines/{id}/readings filters by sensorType and date range")
    void testGetMachineReadingsFiltered() throws Exception {
        Sensor vibSensor = sensorRepository.save(new Sensor(machine.getId(), SensorType.VIBRATION, "mm/s"));

        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 42.0, "°C", Instant.parse("2026-09-28T10:00:00Z")));
        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 45.0, "°C", Instant.parse("2026-09-28T11:00:00Z")));
        readingRepository.save(new SensorReading(machine.getId(), vibSensor.getId(), SensorType.VIBRATION, 2.5, "mm/s", Instant.parse("2026-09-28T11:00:00Z")));
        readingRepository.save(new SensorReading(machine.getId(), sensor.getId(), SensorType.TEMPERATURE, 48.0, "°C", Instant.parse("2026-09-28T12:00:00Z")));

        mockMvc.perform(get("/api/machines/" + machine.getId() + "/readings")
                        .header("Authorization", "Bearer " + token)
                        .param("sensorType", "TEMPERATURE")
                        .param("from", "2026-09-28T10:30:00Z")
                        .param("to", "2026-09-28T11:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].value", is(45.0)));
    }

    @Test
    @DisplayName("US18: GET /api/machines/{id}/readings on non-existent machine returns 404")
    void testGetMachineReadingsNotFound() throws Exception {
        mockMvc.perform(get("/api/machines/non-existent-id/readings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("MACHINE_NOT_FOUND")));
    }

    @Test
    @DisplayName("US18: GET /api/machines/{id}/readings with invalid date returns 400")
    void testGetMachineReadingsInvalidDate() throws Exception {
        mockMvc.perform(get("/api/machines/" + machine.getId() + "/readings")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "invalid-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("US18: GET /api/machines/{id}/readings with invalid sensorType returns 400")
    void testGetMachineReadingsInvalidSensorType() throws Exception {
        mockMvc.perform(get("/api/machines/" + machine.getId() + "/readings")
                        .header("Authorization", "Bearer " + token)
                        .param("sensorType", "INVALID_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("US18: Unauthenticated access returns 401 UNAUTHORIZED")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/machines/" + machine.getId() + "/readings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("US08 & Sprint 2: GET /api/machines/{id}/sensors returns real list of associated sensors")
    void testGetMachineSensors() throws Exception {
        sensorRepository.save(new Sensor(machine.getId(), SensorType.VIBRATION, "mm/s"));

        mockMvc.perform(get("/api/machines/" + machine.getId() + "/sensors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("TEMPERATURE", "VIBRATION")));
    }
}
