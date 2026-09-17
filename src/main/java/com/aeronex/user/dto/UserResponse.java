package com.aeronex.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.aeronex.user.Role;

public record UserResponse(
        UUID id,
        String username,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
