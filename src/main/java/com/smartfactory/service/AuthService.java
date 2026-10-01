package com.smartfactory.service;

import com.smartfactory.dto.request.LoginRequest;
import com.smartfactory.dto.response.LoginResponse;
import com.smartfactory.dto.response.UserResponse;
import com.smartfactory.entity.User;
import com.smartfactory.exception.ApiException;
import com.smartfactory.exception.ErrorCode;
import com.smartfactory.mapper.UserMapper;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_INACTIVE, "User account is inactive");
        }

        String token = jwtService.generateToken(user);
        UserResponse userResponse = userMapper.toResponse(user);

        return new LoginResponse(token, userResponse);
    }

    public UserResponse getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found"));
        return userMapper.toResponse(user);
    }
}
