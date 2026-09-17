package com.aeronex.security.dto;

import java.time.Instant;

import com.aeronex.user.Role;

public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        String username,
        Role role
) {
}
