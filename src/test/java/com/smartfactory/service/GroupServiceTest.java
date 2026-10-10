package com.smartfactory.service;

import com.smartfactory.entity.Group;
import com.smartfactory.entity.User;
import com.smartfactory.repository.GroupRepository;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GroupServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    private User operator1;
    private User operator2;
    private User technician;
    private User responsable;

    @BeforeEach
    void setUp() {
        groupRepository.deleteAll();
        userRepository.deleteAll();

        operator1 = userRepository.save(new User("Alice", "Op", "alice@factory.com", "pass", Role.OPERATOR, "ACTIVE"));
        operator2 = userRepository.save(new User("Bob", "Op", "bob@factory.com", "pass", Role.OPERATOR, "ACTIVE"));
        technician = userRepository.save(new User("Charlie", "Tech", "charlie@factory.com", "pass", Role.TECHNICIAN, "ACTIVE"));
        responsable = userRepository.save(new User("David", "Resp", "david@factory.com", "pass", Role.RESPONSABLE_INDUSTRIEL, "ACTIVE"));
    }

    @Test
    @DisplayName("Create valid group with multiple operators and supervisor")
    void testCreateValidGroup() {
        Group request = new Group(
                "Equipe Alpha",
                Arrays.asList(operator1.getId(), operator2.getId()),
                operator1.getId()
        );

        Group response = groupService.createGroup(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Equipe Alpha");
        assertThat(response.getOperators()).containsExactlyInAnyOrder(operator1.getId(), operator2.getId());
        assertThat(response.getSupervisorId()).isEqualTo(operator1.getId());
        assertThat(response.getSupervisorName()).isEqualTo("Alice Op");
        assertThat(response.getOperatorCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Create group with duplicate name throws CONFLICT")
    void testCreateGroupDuplicateName() {
        groupRepository.save(new Group("Equipe Alpha", List.of(), null));

        Group request = new Group("Equipe Alpha", List.of(), null);

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("GROUP_ALREADY_EXISTS");
                });
    }

    @Test
    @DisplayName("Create group with non-existent operator throws USER_NOT_FOUND")
    void testCreateGroupNonExistentOperator() {
        Group request = new Group(
                "Equipe Beta",
                List.of("non-existent-user-id"),
                null
        );

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("USER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Create group with non-OPERATOR user as operator throws INVALID_OPERATOR_ROLE")
    void testCreateGroupInvalidOperatorRole() {
        Group request = new Group(
                "Equipe Gamma",
                List.of(technician.getId()),
                null
        );

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("INVALID_OPERATOR_ROLE");
                });
    }

    @Test
    @DisplayName("Create group with non-existent supervisor throws USER_NOT_FOUND")
    void testCreateGroupNonExistentSupervisor() {
        Group request = new Group(
                "Equipe Delta",
                List.of(operator1.getId()),
                "non-existent-supervisor-id"
        );

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("USER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Create group with TECHNICIAN as supervisor throws INVALID_SUPERVISOR_ROLE")
    void testCreateGroupInvalidSupervisorRole() {
        Group request = new Group(
                "Equipe Epsilon",
                List.of(operator1.getId()),
                technician.getId()
        );

        assertThatThrownBy(() -> groupService.createGroup(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("INVALID_SUPERVISOR_ROLE");
                });
    }

    @Test
    @DisplayName("Update existing group updates name, operators, and supervisor")
    void testUpdateExistingGroup() {
        Group group = groupRepository.save(new Group("Equipe Initial", List.of(operator1.getId()), operator1.getId()));

        Group request = new Group(
                "Equipe Renommée",
                Arrays.asList(operator1.getId(), operator2.getId()),
                operator2.getId()
        );

        Group response = groupService.updateGroup(group.getId(), request);

        assertThat(response.getName()).isEqualTo("Equipe Renommée");
        assertThat(response.getOperators()).containsExactlyInAnyOrder(operator1.getId(), operator2.getId());
        assertThat(response.getSupervisorId()).isEqualTo(operator2.getId());
        assertThat(response.getSupervisorName()).isEqualTo("Bob Op");
        assertThat(response.getOperatorCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Update non-existent group throws GroupNotFoundException")
    void testUpdateNonExistentGroup() {
        Group request = new Group("NonExistent", List.of(), null);

        assertThatThrownBy(() -> groupService.updateGroup("invalid-id", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("GROUP_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Add operator to group successfully")
    void testAddOperator() {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(operator1.getId())), operator1.getId()));

        Group response = groupService.addOperator(group.getId(), operator2.getId());

        assertThat(response.getOperators()).containsExactlyInAnyOrder(operator1.getId(), operator2.getId());
        assertThat(response.getOperatorCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Add operator who is non-existent throws USER_NOT_FOUND")
    void testAddOperatorNonExistentUser() {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(), null));

        assertThatThrownBy(() -> groupService.addOperator(group.getId(), "unknown-user"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("USER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Add user with wrong role throws INVALID_OPERATOR_ROLE")
    void testAddOperatorWrongRole() {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(), null));

        assertThatThrownBy(() -> groupService.addOperator(group.getId(), technician.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("INVALID_OPERATOR_ROLE");
                });
    }

    @Test
    @DisplayName("Add operator already in group throws OPERATOR_ALREADY_IN_GROUP")
    void testAddOperatorAlreadyInGroup() {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(operator1.getId())), null));

        assertThatThrownBy(() -> groupService.addOperator(group.getId(), operator1.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("OPERATOR_ALREADY_IN_GROUP");
                });
    }

    @Test
    @DisplayName("Remove operator from group and clear supervisor if removed user was supervisor")
    void testRemoveOperatorAndClearSupervisor() {
        Group group = groupRepository.save(new Group("Equipe A",
                new ArrayList<>(Arrays.asList(operator1.getId(), operator2.getId())),
                operator1.getId()));

        Group response = groupService.removeOperator(group.getId(), operator1.getId());

        assertThat(response.getOperators()).containsExactly(operator2.getId());
        assertThat(response.getSupervisorId()).isNull();
        assertThat(response.getSupervisorName()).isNull();
        assertThat(response.getOperatorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Remove operator absent from group throws OPERATOR_NOT_IN_GROUP")
    void testRemoveOperatorNotInGroup() {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(operator1.getId())), null));

        assertThatThrownBy(() -> groupService.removeOperator(group.getId(), operator2.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("OPERATOR_NOT_IN_GROUP");
                });
    }

    @Test
    @DisplayName("Assign supervisor to group")
    void testAssignSupervisor() {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(operator1.getId())), null));

        Group response = groupService.assignSupervisor(group.getId(), operator2.getId());

        assertThat(response.getSupervisorId()).isEqualTo(operator2.getId());
        assertThat(response.getSupervisorName()).isEqualTo("Bob Op");
        // Operator was auto-added to operators list
        assertThat(response.getOperators()).contains(operator2.getId());
    }

    @Test
    @DisplayName("Assign supervisor with invalid role throws INVALID_SUPERVISOR_ROLE")
    void testAssignSupervisorInvalidRole() {
        Group group = groupRepository.save(new Group("Equipe A", new ArrayList<>(List.of(operator1.getId())), null));

        assertThatThrownBy(() -> groupService.assignSupervisor(group.getId(), technician.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("INVALID_SUPERVISOR_ROLE");
                });
    }

    @Test
    @DisplayName("Delete group deletes group but does NOT delete users")
    void testDeleteGroup() {
        Group group = groupRepository.save(new Group("Equipe A", List.of(operator1.getId()), operator1.getId()));

        groupService.deleteGroup(group.getId());

        assertThat(groupRepository.findById(group.getId())).isEmpty();
        // Users must remain intact!
        assertThat(userRepository.findById(operator1.getId())).isPresent();
    }

    @Test
    @DisplayName("Delete non-existent group throws GroupNotFoundException")
    void testDeleteNonExistentGroup() {
        assertThatThrownBy(() -> groupService.deleteGroup("invalid-id"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("GROUP_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Get operators of a group returns List<UserResponse>")
    void testGetOperatorsByGroupId() {
        Group group = groupRepository.save(new Group("Equipe A", Arrays.asList(operator1.getId(), operator2.getId()), null));

        List<User> operators = groupService.getOperatorsByGroupId(group.getId());

        assertThat(operators).hasSize(2);
        assertThat(operators).extracting(User::getEmail)
                .containsExactlyInAnyOrder("alice@factory.com", "bob@factory.com");
    }

    @Test
    @DisplayName("Get supervisor of a group returns UserResponse")
    void testGetSupervisorByGroupId() {
        Group group = groupRepository.save(new Group("Equipe A", List.of(operator1.getId()), operator1.getId()));

        User supervisor = groupService.getSupervisorByGroupId(group.getId());

        assertThat(supervisor).isNotNull();
        assertThat(supervisor.getId()).isEqualTo(operator1.getId());
        assertThat(supervisor.getEmail()).isEqualTo("alice@factory.com");
    }

    @Test
    @DisplayName("Clean user from groups removes operator and clears supervisorId")
    void testCleanUserFromGroups() {
        Group g1 = groupRepository.save(new Group("G1", new ArrayList<>(Arrays.asList(operator1.getId(), operator2.getId())), operator1.getId()));
        Group g2 = groupRepository.save(new Group("G2", new ArrayList<>(List.of(operator1.getId())), operator1.getId()));

        groupService.cleanUserFromGroups(operator1.getId());

        Group updatedG1 = groupRepository.findById(g1.getId()).orElseThrow();
        assertThat(updatedG1.getOperators()).containsExactly(operator2.getId());
        assertThat(updatedG1.getSupervisorId()).isNull();

        Group updatedG2 = groupRepository.findById(g2.getId()).orElseThrow();
        assertThat(updatedG2.getOperators()).isEmpty();
        assertThat(updatedG2.getSupervisorId()).isNull();
    }
}
