package com.easypan.auth;

public record JwtIdentity(
        Long userId,
        String sessionId
) {
}
