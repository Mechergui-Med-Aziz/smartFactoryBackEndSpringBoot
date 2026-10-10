package com.smartfactory.controller;

import com.smartfactory.entity.User;
import com.smartfactory.security.Role;
import com.smartfactory.security.UserPrincipal;
import com.smartfactory.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
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
        Map<String, Object> response = userService.getAllUsers(search, role, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        User response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        User response = userService.createUser(user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(
            @PathVariable String id,
            @Valid @RequestBody User user) {
        User response = userService.updateUser(id, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/change-password/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changePassword(
            @PathVariable String id,
            @RequestParam String newPassword,
            @RequestParam String oldPassword) {
        userService.updatePassword(id, newPassword, oldPassword);
        return ResponseEntity.noContent().build();
    }

    // Self-service: any authenticated user, identity from JWT (no :id to tamper with)
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> updateOwnProfile(
            @RequestBody User user,
            @AuthenticationPrincipal UserPrincipal principal) {
        // role/status/password in the body are ignored server-side
        User response = userService.updateOwnProfile(principal.getId(), user);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changeOwnPassword(
            @RequestParam String newPassword,
            @RequestParam String oldPassword,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.updatePassword(principal.getId(), newPassword, oldPassword);
        return ResponseEntity.noContent().build();
    }
}