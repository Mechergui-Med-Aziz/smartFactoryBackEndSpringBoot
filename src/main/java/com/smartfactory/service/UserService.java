package com.smartfactory.service;

import com.smartfactory.entity.User;
import com.smartfactory.repository.GroupRepository;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupRepository groupRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       GroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.groupRepository = groupRepository;
    }

    public Map<String, Object> getAllUsers(String search, Pageable pageable) {
        return getAllUsers(search, null, pageable);
    }

    public Map<String, Object> getAllUsers(String search, Role role, Pageable pageable) {
        Page<User> usersPage;
        boolean hasSearch = StringUtils.hasText(search);

        if (role != null && hasSearch) {
            usersPage = userRepository.searchUsersByRole(search.trim(), role, pageable);
        } else if (role != null) {
            usersPage = userRepository.findByRole(role, pageable);
        } else if (hasSearch) {
            usersPage = userRepository.searchUsers(search.trim(), pageable);
        } else {
            usersPage = userRepository.findAll(pageable);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", usersPage.getContent());
        response.put("page", usersPage.getNumber());
        response.put("size", usersPage.getSize());
        response.put("totalElements", usersPage.getTotalElements());
        response.put("totalPages", usersPage.getTotalPages());
        return response;
    }

    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found with id: " + id));
    }

    public User createUser(User user) {
        String normalizedEmail = user.getEmail() != null ? user.getEmail().toLowerCase().trim() : "";
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS: Email is already registered: " + normalizedEmail);
        }

        if (!StringUtils.hasText(user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Password is required");
        }
        if (user.getPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Password must be at least 6 characters");
        }
        if (user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Role is required");
        }

        user.setFirstName(user.getFirstName() != null ? user.getFirstName().trim() : null);
        user.setLastName(user.getLastName() != null ? user.getLastName().trim() : null);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(StringUtils.hasText(user.getStatus()) ? user.getStatus().toUpperCase().trim() : "ACTIVE");

        return userRepository.save(user);
    }

    public User updatePassword(String id, String newPassword, String oldPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found with id: " + id));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Old password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    public User updateUser(String id, User request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found with id: " + id));

        String normalizedEmail = request.getEmail() != null ? request.getEmail().toLowerCase().trim() : "";
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS: Email is already registered: " + normalizedEmail);
        }

        if (!StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Status is required");
        }
        if (request.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Role is required");
        }

        user.setFirstName(request.getFirstName() != null ? request.getFirstName().trim() : user.getFirstName());
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : user.getLastName());
        user.setEmail(normalizedEmail);
        user.setRole(request.getRole());
        user.setStatus(request.getStatus().toUpperCase().trim());

        if (StringUtils.hasText(request.getPassword())) {
            if (request.getPassword().length() < 6) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Password must be at least 6 characters if provided");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return userRepository.save(user);
    }

    public User updateOwnProfile(String userId, User request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found"));

        String normalizedEmail = request.getEmail() != null ? request.getEmail().toLowerCase().trim() : "";
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS: Email is already registered: " + normalizedEmail);
        }

        user.setFirstName(request.getFirstName() != null ? request.getFirstName().trim() : user.getFirstName());
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : user.getLastName());
        user.setEmail(normalizedEmail);
        // role / status / password deliberately untouched — ADMIN-only via PUT /{id}

        return userRepository.save(user);
    }

    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found with id: " + id);
        }

        // Clean up group references for data consistency (US10)
        groupRepository.findByOperatorsContaining(id).forEach(group -> {
            group.getOperators().remove(id);
            if (id.equals(group.getSupervisorId())) {
                group.setSupervisorId(null);
            }
            groupRepository.save(group);
        });

        groupRepository.findBySupervisorId(id).forEach(group -> {
            group.setSupervisorId(null);
            groupRepository.save(group);
        });

        userRepository.deleteById(id);
    }
}
