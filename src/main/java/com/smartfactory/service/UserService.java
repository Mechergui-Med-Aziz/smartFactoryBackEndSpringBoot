package com.smartfactory.service;

import com.smartfactory.dto.request.CreateUserRequest;
import com.smartfactory.dto.request.UpdateUserRequest;
import com.smartfactory.dto.response.PageResponse;
import com.smartfactory.dto.response.UserResponse;
import com.smartfactory.entity.User;
import com.smartfactory.exception.ApiException;
import com.smartfactory.exception.ErrorCode;
import com.smartfactory.mapper.UserMapper;
import com.smartfactory.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final com.smartfactory.repository.GroupRepository groupRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper,
                       com.smartfactory.repository.GroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.groupRepository = groupRepository;
    }

    public PageResponse<UserResponse> getAllUsers(String search, Pageable pageable) {
        return getAllUsers(search, null, pageable);
    }

    public PageResponse<UserResponse> getAllUsers(String search, com.smartfactory.security.Role role, Pageable pageable) {
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
        Page<UserResponse> responsePage = usersPage.map(userMapper::toResponse);
        return PageResponse.of(responsePage);
    }

    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found with id: " + id));
        return userMapper.toResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.USER_ALREADY_EXISTS, "Email is already registered: " + normalizedEmail);
        }

        User user = new User(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getRole(),
                StringUtils.hasText(request.getStatus()) ? request.getStatus().toUpperCase().trim() : "ACTIVE"
        );

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    public UserResponse updatePassword(String id, String newPassword,String oldPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found with id: " + id));

                if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Old password is incorrect");
                }
        user.setPassword(passwordEncoder.encode(newPassword));
        User updated = userRepository.save(user);
        return userMapper.toResponse(updated);
    }

    public UserResponse updateUser(String id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found with id: " + id));

        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.USER_ALREADY_EXISTS, "Email is already registered: " + normalizedEmail);
        }

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(normalizedEmail);
        user.setRole(request.getRole());
        user.setStatus(request.getStatus().toUpperCase().trim());

        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User updated = userRepository.save(user);
        return userMapper.toResponse(updated);
    }

    public UserResponse updateOwnProfile(String userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found"));

        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.USER_ALREADY_EXISTS, "Email is already registered: " + normalizedEmail);
        }

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(normalizedEmail);
        // role / status / password deliberately untouched — ADMIN-only via PUT /{id}

        return userMapper.toResponse(userRepository.save(user));
    }

    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found with id: " + id);
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
