package com.smartfactory.service;

import com.smartfactory.dto.request.CreateGroupRequest;
import com.smartfactory.dto.request.UpdateGroupRequest;
import com.smartfactory.dto.response.GroupResponse;
import com.smartfactory.dto.response.PageResponse;
import com.smartfactory.dto.response.UserResponse;
import com.smartfactory.entity.Group;
import com.smartfactory.entity.User;
import com.smartfactory.exception.ApiException;
import com.smartfactory.exception.ErrorCode;
import com.smartfactory.exception.GroupNotFoundException;
import com.smartfactory.mapper.GroupMapper;
import com.smartfactory.mapper.UserMapper;
import com.smartfactory.repository.GroupRepository;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMapper groupMapper;
    private final UserMapper userMapper;

    public GroupService(GroupRepository groupRepository,
                        UserRepository userRepository,
                        GroupMapper groupMapper,
                        UserMapper userMapper) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupMapper = groupMapper;
        this.userMapper = userMapper;
    }

    public PageResponse<GroupResponse> getAllGroups(String search, Pageable pageable) {
        Page<Group> page;
        if (StringUtils.hasText(search)) {
            page = groupRepository.searchGroups(search.trim(), pageable);
        } else {
            page = groupRepository.findAll(pageable);
        }

        // Efficient batch-fetch supervisor names to prevent N+1 queries
        Set<String> supervisorIds = page.getContent().stream()
                .map(Group::getSupervisorId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Map<String, String> supervisorNameMap = new HashMap<>();
        if (!supervisorIds.isEmpty()) {
            userRepository.findAllById(supervisorIds).forEach(user ->
                    supervisorNameMap.put(user.getId(), user.getFirstName() + " " + user.getLastName())
            );
        }

        Page<GroupResponse> responsePage = page.map(group -> {
            String supervisorName = group.getSupervisorId() != null
                    ? supervisorNameMap.get(group.getSupervisorId())
                    : null;
            return groupMapper.toResponse(group, supervisorName);
        });

        return PageResponse.of(responsePage);
    }

    public GroupResponse getGroupById(String id) {
        Group group = findGroupOrThrow(id);
        String supervisorName = resolveSupervisorName(group.getSupervisorId());
        return groupMapper.toResponse(group, supervisorName);
    }

    public GroupResponse createGroup(CreateGroupRequest request) {
        String trimmedName = request.getName().trim();
        if (groupRepository.existsByName(trimmedName)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.GROUP_ALREADY_EXISTS,
                    "Group already exists with name: " + trimmedName);
        }

        List<String> validatedOperators = validateAndCollectOperators(request.getOperators());
        String validatedSupervisorId = validateSupervisor(request.getSupervisorId(), validatedOperators);

        Group group = new Group(trimmedName, validatedOperators, validatedSupervisorId);
        Group saved = groupRepository.save(group);

        String supervisorName = resolveSupervisorName(saved.getSupervisorId());
        return groupMapper.toResponse(saved, supervisorName);
    }

    public GroupResponse updateGroup(String id, UpdateGroupRequest request) {
        Group group = findGroupOrThrow(id);

        String trimmedName = request.getName().trim();
        if (!group.getName().equalsIgnoreCase(trimmedName) && groupRepository.existsByName(trimmedName)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.GROUP_ALREADY_EXISTS,
                    "Group already exists with name: " + trimmedName);
        }

        List<String> validatedOperators = validateAndCollectOperators(request.getOperators());
        String validatedSupervisorId = validateSupervisor(request.getSupervisorId(), validatedOperators);

        group.setName(trimmedName);
        group.setOperators(validatedOperators);
        group.setSupervisorId(validatedSupervisorId);

        Group updated = groupRepository.save(group);
        String supervisorName = resolveSupervisorName(updated.getSupervisorId());
        return groupMapper.toResponse(updated, supervisorName);
    }

    public void deleteGroup(String id) {
        findGroupOrThrow(id);
        // Business rule: Users remain intact when a group is deleted
        groupRepository.deleteById(id);
    }

    public GroupResponse addOperator(String groupId, String userId) {
        Group group = findGroupOrThrow(groupId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND,
                        "Operator not found with id: " + userId));

        if (user.getRole() != Role.OPERATOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_OPERATOR_ROLE,
                    "User " + userId + " does not have OPERATOR role");
        }

        if (group.getOperators().contains(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATOR_ALREADY_IN_GROUP,
                    "Operator is already in group: " + userId);
        }

        group.getOperators().add(userId);
        Group saved = groupRepository.save(group);
        String supervisorName = resolveSupervisorName(saved.getSupervisorId());
        return groupMapper.toResponse(saved, supervisorName);
    }

    public GroupResponse removeOperator(String groupId, String userId) {
        Group group = findGroupOrThrow(groupId);

        if (!group.getOperators().contains(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.OPERATOR_NOT_IN_GROUP,
                    "Operator not found in group: " + userId);
        }

        group.getOperators().remove(userId);

        // Business rule: If removed operator was supervisor, clear supervisor to maintain consistency
        if (userId.equals(group.getSupervisorId())) {
            group.setSupervisorId(null);
        }

        Group saved = groupRepository.save(group);
        String supervisorName = resolveSupervisorName(saved.getSupervisorId());
        return groupMapper.toResponse(saved, supervisorName);
    }

    public GroupResponse assignSupervisor(String groupId, String supervisorId) {
        Group group = findGroupOrThrow(groupId);

        User user = userRepository.findById(supervisorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND,
                        "Supervisor not found with id: " + supervisorId));

        if (user.getRole() != Role.OPERATOR && user.getRole() != Role.RESPONSABLE_INDUSTRIEL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_SUPERVISOR_ROLE,
                    "User must have role OPERATOR or RESPONSABLE_INDUSTRIEL to supervise a group");
        }

        // If supervisor is an operator, ensure membership in group
        if (user.getRole() == Role.OPERATOR && !group.getOperators().contains(supervisorId)) {
            group.getOperators().add(supervisorId);
        }

        group.setSupervisorId(supervisorId);
        Group saved = groupRepository.save(group);
        String supervisorName = user.getFirstName() + " " + user.getLastName();
        return groupMapper.toResponse(saved, supervisorName);
    }

    public List<UserResponse> getOperatorsByGroupId(String groupId) {
        Group group = findGroupOrThrow(groupId);
        if (group.getOperators() == null || group.getOperators().isEmpty()) {
            return Collections.emptyList();
        }
        return userRepository.findAllById(group.getOperators()).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getSupervisorByGroupId(String groupId) {
        Group group = findGroupOrThrow(groupId);
        if (!StringUtils.hasText(group.getSupervisorId())) {
            return null;
        }
        return userRepository.findById(group.getSupervisorId())
                .map(userMapper::toResponse)
                .orElse(null);
    }

    public void cleanUserFromGroups(String userId) {
        List<Group> operatorGroups = groupRepository.findByOperatorsContaining(userId);
        for (Group group : operatorGroups) {
            group.getOperators().remove(userId);
            if (userId.equals(group.getSupervisorId())) {
                group.setSupervisorId(null);
            }
            groupRepository.save(group);
        }

        List<Group> supervisedGroups = groupRepository.findBySupervisorId(userId);
        for (Group group : supervisedGroups) {
            group.setSupervisorId(null);
            groupRepository.save(group);
        }
    }

    private Group findGroupOrThrow(String id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException("Group not found with id: " + id));
    }

    private List<String> validateAndCollectOperators(List<String> operatorIds) {
        if (operatorIds == null || operatorIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> distinctIds = operatorIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        for (String opId : distinctIds) {
            User user = userRepository.findById(opId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND,
                            "Operator not found with id: " + opId));
            if (user.getRole() != Role.OPERATOR) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_OPERATOR_ROLE,
                        "User " + opId + " does not have OPERATOR role");
            }
        }

        return distinctIds;
    }

    private String validateSupervisor(String supervisorId, List<String> operators) {
        if (!StringUtils.hasText(supervisorId)) {
            return null;
        }

        String trimmedSupervisorId = supervisorId.trim();
        User user = userRepository.findById(trimmedSupervisorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND,
                        "Supervisor not found with id: " + trimmedSupervisorId));

        if (user.getRole() != Role.OPERATOR && user.getRole() != Role.RESPONSABLE_INDUSTRIEL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_SUPERVISOR_ROLE,
                    "User must have role OPERATOR or RESPONSABLE_INDUSTRIEL to supervise a group");
        }

        // If supervisor is an operator, ensure inclusion in group operators
        if (user.getRole() == Role.OPERATOR && !operators.contains(trimmedSupervisorId)) {
            operators.add(trimmedSupervisorId);
        }

        return trimmedSupervisorId;
    }

    private String resolveSupervisorName(String supervisorId) {
        if (!StringUtils.hasText(supervisorId)) {
            return null;
        }
        return userRepository.findById(supervisorId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(null);
    }
}
