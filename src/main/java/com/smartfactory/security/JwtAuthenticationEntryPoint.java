package com.smartfactory.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("status", HttpStatus.UNAUTHORIZED.value());
        errorResponse.put("code", "UNAUTHORIZED");
        errorResponse.put("message", authException.getMessage() != null ? authException.getMessage() : "Unauthorized access");
        errorResponse.put("timestamp", Instant.now().toString());

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
