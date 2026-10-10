package com.smartfactory.controller;

import com.smartfactory.entity.Group;
import com.smartfactory.entity.User;
import com.smartfactory.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllGroups(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sortObj = Sort.by(direction, sortParts[0]);

        Pageable pageable = PageRequest.of(page, boundedSize, sortObj);
        Map<String, Object> response = groupService.getAllGroups(search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Group> getGroupById(@PathVariable String id) {
        Group response = groupService.getGroupById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Group> createGroup(@Valid @RequestBody Group group) {
        Group response = groupService.createGroup(group);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Group> updateGroup(
            @PathVariable String id,
            @Valid @RequestBody Group group) {
        Group response = groupService.updateGroup(id, group);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/operators/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Group> addOperator(
            @PathVariable String groupId,
            @PathVariable String userId) {
        Group response = groupService.addOperator(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}/operators/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Group> removeOperator(
            @PathVariable String groupId,
            @PathVariable String userId) {
        Group response = groupService.removeOperator(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}/supervisor")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Group> assignSupervisor(
            @PathVariable String groupId,
            @RequestBody Map<String, String> request) {
        String supervisorId = request != null ? request.get("supervisorId") : null;
        if (!StringUtils.hasText(supervisorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Supervisor ID is required");
        }
        Group response = groupService.assignSupervisor(groupId, supervisorId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{groupId}/operators")
    public ResponseEntity<List<User>> getOperatorsByGroupId(@PathVariable String groupId) {
        List<User> operators = groupService.getOperatorsByGroupId(groupId);
        return ResponseEntity.ok(operators);
    }

    @GetMapping("/{groupId}/supervisor")
    public ResponseEntity<User> getSupervisorByGroupId(@PathVariable String groupId) {
        User supervisor = groupService.getSupervisorByGroupId(groupId);
        if (supervisor == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(supervisor);
    }
}
