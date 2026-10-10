package com.smartfactory.service;

import com.smartfactory.entity.Group;
import com.smartfactory.entity.User;
import com.smartfactory.repository.GroupRepository;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> getAllGroups(String search, Pageable pageable) {
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

        page.getContent().forEach(group -> {
            String supervisorName = group.getSupervisorId() != null
                    ? supervisorNameMap.get(group.getSupervisorId())
                    : null;
            group.setSupervisorName(supervisorName);
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", page.getContent());
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        return response;
    }

    public Group getGroupById(String id) {
        Group group = findGroupOrThrow(id);
        String supervisorName = resolveSupervisorName(group.getSupervisorId());
        group.setSupervisorName(supervisorName);
        return group;
    }

    public Group createGroup(Group group) {
        String trimmedName = group.getName() != null ? group.getName().trim() : "";
        if (groupRepository.existsByName(trimmedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "GROUP_ALREADY_EXISTS: Group already exists with name: " + trimmedName);
        }

        List<String> validatedOperators = validateAndCollectOperators(group.getOperators());
        String validatedSupervisorId = validateSupervisor(group.getSupervisorId(), validatedOperators);

        group.setName(trimmedName);
        group.setOperators(validatedOperators);
        group.setSupervisorId(validatedSupervisorId);

        Group saved = groupRepository.save(group);
        String supervisorName = resolveSupervisorName(saved.getSupervisorId());
        saved.setSupervisorName(supervisorName);
        return saved;
    }

    public Group updateGroup(String id, Group request) {
        Group group = findGroupOrThrow(id);

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (!group.getName().equalsIgnoreCase(trimmedName) && groupRepository.existsByName(trimmedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "GROUP_ALREADY_EXISTS: Group already exists with name: " + trimmedName);
        }

        List<String> validatedOperators = validateAndCollectOperators(request.getOperators());
        String validatedSupervisorId = validateSupervisor(request.getSupervisorId(), validatedOperators);

        group.setName(trimmedName);
        group.setOperators(validatedOperators);
        group.setSupervisorId(validatedSupervisorId);

        Group updated = groupRepository.save(group);
        String supervisorName = resolveSupervisorName(updated.getSupervisorId());
        updated.setSupervisorName(supervisorName);
        return updated;
    }

    public void deleteGroup(String id) {
        findGroupOrThrow(id);
        // Business rule: Users remain intact when a group is deleted
        groupRepository.deleteById(id);
    }

    public Group addOperator(String groupId, String userId) {
        Group group = findGroupOrThrow(groupId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND: Operator not found with id: " + userId));

        if (user.getRole() != Role.OPERATOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "INVALID_OPERATOR_ROLE: User " + userId + " does not have OPERATOR role");
        }

        if (group.getOperators().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "OPERATOR_ALREADY_IN_GROUP: Operator is already in group: " + userId);
        }

        group.getOperators().add(userId);
        Group saved = groupRepository.save(group);
        String supervisorName = resolveSupervisorName(saved.getSupervisorId());
        saved.setSupervisorName(supervisorName);
        return saved;
    }

    public Group removeOperator(String groupId, String userId) {
        Group group = findGroupOrThrow(groupId);

        if (!group.getOperators().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OPERATOR_NOT_IN_GROUP: Operator not found in group: " + userId);
        }

        group.getOperators().remove(userId);

        // Business rule: If removed operator was supervisor, clear supervisor to maintain consistency
        if (userId.equals(group.getSupervisorId())) {
            group.setSupervisorId(null);
        }

        Group saved = groupRepository.save(group);
        String supervisorName = resolveSupervisorName(saved.getSupervisorId());
        saved.setSupervisorName(supervisorName);
        return saved;
    }

    public Group assignSupervisor(String groupId, String supervisorId) {
        Group group = findGroupOrThrow(groupId);

        User user = userRepository.findById(supervisorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND: Supervisor not found with id: " + supervisorId));

        if (user.getRole() != Role.OPERATOR && user.getRole() != Role.RESPONSABLE_INDUSTRIEL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "INVALID_SUPERVISOR_ROLE: User must have role OPERATOR or RESPONSABLE_INDUSTRIEL to supervise a group");
        }

        // If supervisor is an operator, ensure membership in group
        if (user.getRole() == Role.OPERATOR && !group.getOperators().contains(supervisorId)) {
            group.getOperators().add(supervisorId);
        }

        group.setSupervisorId(supervisorId);
        Group saved = groupRepository.save(group);
        String supervisorName = user.getFirstName() + " " + user.getLastName();
        saved.setSupervisorName(supervisorName);
        return saved;
    }

    public List<User> getOperatorsByGroupId(String groupId) {
        Group group = findGroupOrThrow(groupId);
        if (group.getOperators() == null || group.getOperators().isEmpty()) {
            return Collections.emptyList();
        }
        return userRepository.findAllById(group.getOperators());
    }

    public User getSupervisorByGroupId(String groupId) {
        Group group = findGroupOrThrow(groupId);
        if (!StringUtils.hasText(group.getSupervisorId())) {
            return null;
        }
        return userRepository.findById(group.getSupervisorId()).orElse(null);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "GROUP_NOT_FOUND: Group not found with id: " + id));
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
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "USER_NOT_FOUND: Operator not found with id: " + opId));
            if (user.getRole() != Role.OPERATOR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "INVALID_OPERATOR_ROLE: User " + opId + " does not have OPERATOR role");
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND: Supervisor not found with id: " + trimmedSupervisorId));

        if (user.getRole() != Role.OPERATOR && user.getRole() != Role.RESPONSABLE_INDUSTRIEL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "INVALID_SUPERVISOR_ROLE: User must have role OPERATOR or RESPONSABLE_INDUSTRIEL to supervise a group");
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
