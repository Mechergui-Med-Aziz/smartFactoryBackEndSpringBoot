package com.smartfactory.repository;

import com.smartfactory.entity.Group;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GroupRepositoryTest {

    @Autowired
    private GroupRepository groupRepository;

    @BeforeEach
    void setUp() {
        groupRepository.deleteAll();
    }

    @Test
    @DisplayName("Save and findById returns group")
    void testSaveAndFindById() {
        Group group = new Group("Equipe Production A", Arrays.asList("user1", "user2"), "user1");
        Group saved = groupRepository.save(group);

        assertThat(saved.getId()).isNotNull();

        Optional<Group> found = groupRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Equipe Production A");
        assertThat(found.get().getOperators()).containsExactly("user1", "user2");
        assertThat(found.get().getSupervisorId()).isEqualTo("user1");
    }

    @Test
    @DisplayName("findByName returns group when exists")
    void testFindByName() {
        Group group = new Group("Equipe Maintenance", Arrays.asList("user3"), "user3");
        groupRepository.save(group);

        Optional<Group> found = groupRepository.findByName("Equipe Maintenance");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Equipe Maintenance");

        boolean exists = groupRepository.existsByName("Equipe Maintenance");
        assertThat(exists).isTrue();

        boolean notExists = groupRepository.existsByName("NonExistent");
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Search groups by name regex")
    void testSearchGroups() {
        groupRepository.save(new Group("Equipe Production Alpha", List.of(), null));
        groupRepository.save(new Group("Equipe Production Beta", List.of(), null));
        groupRepository.save(new Group("Equipe Nuit", List.of(), null));

        Page<Group> result = groupRepository.searchGroups("production", PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Group::getName)
                .containsExactlyInAnyOrder("Equipe Production Alpha", "Equipe Production Beta");
    }

    @Test
    @DisplayName("findByOperatorsContaining finds groups where user is member")
    void testFindByOperatorsContaining() {
        groupRepository.save(new Group("G1", Arrays.asList("op1", "op2"), "op1"));
        groupRepository.save(new Group("G2", Arrays.asList("op2", "op3"), "op3"));
        groupRepository.save(new Group("G3", Arrays.asList("op4"), "op4"));

        List<Group> groupsWithOp2 = groupRepository.findByOperatorsContaining("op2");
        assertThat(groupsWithOp2).hasSize(2);
        assertThat(groupsWithOp2).extracting(Group::getName).containsExactlyInAnyOrder("G1", "G2");
    }

    @Test
    @DisplayName("findBySupervisorId finds groups supervised by user")
    void testFindBySupervisorId() {
        groupRepository.save(new Group("G1", List.of("op1"), "sup1"));
        groupRepository.save(new Group("G2", List.of("op2"), "sup1"));
        groupRepository.save(new Group("G3", List.of("op3"), "sup2"));

        List<Group> supervised = groupRepository.findBySupervisorId("sup1");
        assertThat(supervised).hasSize(2);
        assertThat(supervised).extracting(Group::getName).containsExactlyInAnyOrder("G1", "G2");
    }

    @Test
    @DisplayName("Delete group by id")
    void testDeleteById() {
        Group group = groupRepository.save(new Group("ToDelete", List.of(), null));
        assertThat(groupRepository.existsById(group.getId())).isTrue();

        groupRepository.deleteById(group.getId());
        assertThat(groupRepository.existsById(group.getId())).isFalse();
        assertThat(groupRepository.findById(group.getId())).isEmpty();
    }

    @Test
    @DisplayName("Finding non-existent group returns empty")
    void testFindNonExistent() {
        Optional<Group> found = groupRepository.findById("non-existent-id");
        assertThat(found).isEmpty();
    }
}
