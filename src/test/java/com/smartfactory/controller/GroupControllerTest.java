package com.smartfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.entity.Group;
import com.smartfactory.entity.User;
import com.smartfactory.repository.GroupRepository;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GroupRepository groupRepository;

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
    private String technicianToken;
    private String responsableToken;

    private User op1;
    private User op2;
    private User tech;
    private User resp;

    @BeforeEach
    void setUp() {
        groupRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User("Admin", "Root", "admin@factory.com", passwordEncoder.encode("pass"), Role.ADMIN, "ACTIVE"));
        op1 = userRepository.save(new User("Alice", "Op", "alice@factory.com", passwordEncoder.encode("pass"), Role.OPERATOR, "ACTIVE"));
        op2 = userRepository.save(new User("Bob", "Op", "bob@factory.com", passwordEncoder.encode("pass"), Role.OPERATOR, "ACTIVE"));
        tech = userRepository.save(new User("Charlie", "Tech", "charlie@factory.com", passwordEncoder.encode("pass"), Role.TECHNICIAN, "ACTIVE"));
        resp = userRepository.save(new User("David", "Resp", "david@factory.com", passwordEncoder.encode("pass"), Role.RESPONSABLE_INDUSTRIEL, "ACTIVE"));

        adminToken = jwtService.generateToken(admin);
        operatorToken = jwtService.generateToken(op1);
        technicianToken = jwtService.generateToken(tech);
        responsableToken = jwtService.generateToken(resp);
    }

    @Test
    @DisplayName("US10: ADMIN can create a group")
    void testCreateGroup() throws Exception {
        Group request = new Group(
                "Equipe Usinage",
                Arrays.asList(op1.getId(), op2.getId()),
                op1.getId()
        );

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Equipe Usinage")))
                .andExpect(jsonPath("$.operators", hasSize(2)))
                .andExpect(jsonPath("$.supervisorId", is(op1.getId())))
                .andExpect(jsonPath("$.supervisorName", is("Alice Op")))
                .andExpect(jsonPath("$.operatorCount", is(2)));
    }

    @Test
    @DisplayName("US10: Validation error on empty group name returns 400")
    void testCreateGroupEmptyName() throws Exception {
        Group request = new Group("", List.of(), null);

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("US10: Duplicate group name returns 409 Conflict")
    void testCreateGroupDuplicateName() throws Exception {
        groupRepository.save(new Group("Equipe Usinage", List.of(), null));

        Group request = new Group("Equipe Usinage", List.of(), null);

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("GROUP_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("US10: ADMIN can update a group")
    void testUpdateGroup() throws Exception {
        Group group = groupRepository.save(new Group("Equipe Initial", List.of(op1.getId()), op1.getId()));

        Group request = new Group(
                "Equipe Modifiée",
                Arrays.asList(op1.getId(), op2.getId()),
                op2.getId()
        );

        mockMvc.perform(put("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Equipe Modifiée")))
                .andExpect(jsonPath("$.supervisorId", is(op2.getId())))
                .andExpect(jsonPath("$.supervisorName", is("Bob Op")))
                .andExpect(jsonPath("$.operatorCount", is(2)));
    }

    @Test
    @DisplayName("US10: Update non-existent group returns 404")
    void testUpdateNonExistentGroup() throws Exception {
        Group request = new Group("Equipe A", List.of(), null);

        mockMvc.perform(put("/api/groups/non-existent-id")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("GROUP_NOT_FOUND")));
    }

    @Test
    @DisplayName("US10: ADMIN can delete a group")
    void testDeleteGroup() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", List.of(), null));

        mockMvc.perform(delete("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US10: ADMIN can add operator to group")
    void testAddOperator() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(op1.getId())), op1.getId()));

        mockMvc.perform(post("/api/groups/" + group.getId() + "/operators/" + op2.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operators", hasSize(2)))
                .andExpect(jsonPath("$.operatorCount", is(2)));
    }

    @Test
    @DisplayName("US10: Adding operator already in group returns 409 Conflict")
    void testAddOperatorAlreadyInGroup() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(op1.getId())), op1.getId()));

        mockMvc.perform(post("/api/groups/" + group.getId() + "/operators/" + op1.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("OPERATOR_ALREADY_IN_GROUP")));
    }

    @Test
    @DisplayName("US10: ADMIN can remove operator from group")
    void testRemoveOperator() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(Arrays.asList(op1.getId(), op2.getId())), op1.getId()));

        mockMvc.perform(delete("/api/groups/" + group.getId() + "/operators/" + op1.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operators", hasSize(1)))
                .andExpect(jsonPath("$.supervisorId").doesNotExist())
                .andExpect(jsonPath("$.operatorCount", is(1)));
    }

    @Test
    @DisplayName("US10: ADMIN can assign supervisor")
    void testAssignSupervisor() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(op1.getId())), null));

        Map<String, String> request = Map.of("supervisorId", op2.getId());

        mockMvc.perform(put("/api/groups/" + group.getId() + "/supervisor")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supervisorId", is(op2.getId())))
                .andExpect(jsonPath("$.supervisorName", is("Bob Op")));
    }

    @Test
    @DisplayName("US10: Get group by id returns group details")
    void testGetGroupById() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", List.of(op1.getId()), op1.getId()));

        mockMvc.perform(get("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(group.getId())))
                .andExpect(jsonPath("$.name", is("Equipe A")))
                .andExpect(jsonPath("$.supervisorName", is("Alice Op")));
    }

    @Test
    @DisplayName("US10: Get all groups with pagination and search")
    void testGetAllGroupsWithSearch() throws Exception {
        groupRepository.save(new Group("Equipe Alpha", List.of(op1.getId()), op1.getId()));
        groupRepository.save(new Group("Equipe Beta", List.of(op2.getId()), op2.getId()));

        mockMvc.perform(get("/api/groups?search=Alpha")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Equipe Alpha")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("US10: Get operators of a group returns List<UserResponse>")
    void testGetOperatorsByGroupId() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", Arrays.asList(op1.getId(), op2.getId()), null));

        mockMvc.perform(get("/api/groups/" + group.getId() + "/operators")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].email", containsInAnyOrder("alice@factory.com", "bob@factory.com")));
    }

    @Test
    @DisplayName("US10: Get supervisor of a group returns UserResponse")
    void testGetSupervisorByGroupId() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", List.of(op1.getId()), op1.getId()));

        mockMvc.perform(get("/api/groups/" + group.getId() + "/supervisor")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(op1.getId())))
                .andExpect(jsonPath("$.email", is("alice@factory.com")));
    }

    @Test
    @DisplayName("US10 RBAC: OPERATOR cannot create group (403 Forbidden)")
    void testOperatorCannotCreateGroup() throws Exception {
        Group request = new Group("Equipe Test", List.of(), null);

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US10 RBAC: TECHNICIAN cannot create group (403 Forbidden)")
    void testTechnicianCannotCreateGroup() throws Exception {
        Group request = new Group("Equipe Test", List.of(), null);

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US10 RBAC: RESPONSABLE cannot create group (403 Forbidden)")
    void testResponsableCannotCreateGroup() throws Exception {
        Group request = new Group("Equipe Test", List.of(), null);

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + responsableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US10 RBAC: OPERATOR cannot delete group (403 Forbidden)")
    void testOperatorCannotDeleteGroup() throws Exception {
        Group group = groupRepository.save(new Group("Equipe A", List.of(), null));

        mockMvc.perform(delete("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US10 RBAC: Unauthenticated user receives 401 Unauthorized")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Equipe A\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("US10: User role selector API filters users by role OPERATOR")
    void testUserRoleSelector() throws Exception {
        mockMvc.perform(get("/api/users?role=OPERATOR")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].role", everyItem(is("OPERATOR"))));
    }
}
