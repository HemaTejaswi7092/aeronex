package com.aeronex.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.aeronex.user.Role;
import com.aeronex.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-not-used-anywhere-else-32bytes-minimum";

    private AeronexUserDetails userDetails(Role role) {
        User user = new User("alice", "hashed-password-not-relevant-here", role, true);
        return new AeronexUserDetails(user);
    }

    @Test
    void generatesAndParsesTokenRoundTrip() {
        JwtService jwtService = new JwtService(SECRET, 60);

        JwtService.GeneratedToken generated = jwtService.generateToken(userDetails(Role.OPS));
        Claims claims = jwtService.parseClaims(generated.token());

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get(JwtService.ROLE_CLAIM, String.class)).isEqualTo("OPS");
        assertThat(generated.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsExpiredToken() {
        JwtService issuer = new JwtService(SECRET, -1);

        JwtService.GeneratedToken generated = issuer.generateToken(userDetails(Role.VIEWER));

        assertThatThrownBy(() -> issuer.parseClaims(generated.token()))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService issuer = new JwtService(SECRET, 60);
        JwtService otherIssuer = new JwtService("a-completely-different-jwt-secret-32bytes-minimum-length", 60);

        JwtService.GeneratedToken generated = issuer.generateToken(userDetails(Role.ADMIN));

        assertThatThrownBy(() -> otherIssuer.parseClaims(generated.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMalformedToken() {
        JwtService jwtService = new JwtService(SECRET, 60);

        assertThatThrownBy(() -> jwtService.parseClaims("not-a-jwt-at-all"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesToConstructWithNullSecret() {
        assertThatThrownBy(() -> new JwtService(null, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AERONEX_JWT_SECRET");
    }

    @Test
    void refusesToConstructWithBlankSecret() {
        assertThatThrownBy(() -> new JwtService("   ", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AERONEX_JWT_SECRET");
    }
}
