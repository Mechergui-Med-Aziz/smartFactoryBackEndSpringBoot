package com.smartfactory.controller;

import com.smartfactory.dto.request.AssignSupervisorRequest;
import com.smartfactory.dto.request.CreateGroupRequest;
import com.smartfactory.dto.request.UpdateGroupRequest;
import com.smartfactory.dto.response.GroupResponse;
import com.smartfactory.dto.response.PageResponse;
import com.smartfactory.dto.response.UserResponse;
import com.smartfactory.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<GroupResponse>> getAllGroups(
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
        PageResponse<GroupResponse> response = groupService.getAllGroups(search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroupById(@PathVariable String id) {
        GroupResponse response = groupService.getGroupById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.createGroup(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable String id,
            @Valid @RequestBody UpdateGroupRequest request) {
        GroupResponse response = groupService.updateGroup(id, request);
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
    public ResponseEntity<GroupResponse> addOperator(
            @PathVariable String groupId,
            @PathVariable String userId) {
        GroupResponse response = groupService.addOperator(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}/operators/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> removeOperator(
            @PathVariable String groupId,
            @PathVariable String userId) {
        GroupResponse response = groupService.removeOperator(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}/supervisor")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> assignSupervisor(
            @PathVariable String groupId,
            @Valid @RequestBody AssignSupervisorRequest request) {
        GroupResponse response = groupService.assignSupervisor(groupId, request.getSupervisorId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{groupId}/operators")
    public ResponseEntity<List<UserResponse>> getOperatorsByGroupId(@PathVariable String groupId) {
        List<UserResponse> operators = groupService.getOperatorsByGroupId(groupId);
        return ResponseEntity.ok(operators);
    }

    @GetMapping("/{groupId}/supervisor")
    public ResponseEntity<UserResponse> getSupervisorByGroupId(@PathVariable String groupId) {
        UserResponse supervisor = groupService.getSupervisorByGroupId(groupId);
        if (supervisor == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(supervisor);
    }
}
