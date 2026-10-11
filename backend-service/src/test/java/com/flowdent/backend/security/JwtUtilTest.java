package com.flowdent.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwt = new JwtUtil("unit-test-secret-that-is-long-enough-for-hs256!!", 60_000);

    @Test
    @DisplayName("Issued token is valid and carries the email and role")
    void roundTrip() {
        String token = jwt.generateToken("admin", "ADMIN");

        assertThat(jwt.isValid(token)).isTrue();
        assertThat(jwt.extractEmail(token)).isEqualTo("admin");
        assertThat(jwt.parse(token).get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Tampered or foreign tokens are rejected")
    void tamperedTokenRejected() {
        String token = jwt.generateToken("doctor@example.com", "CUSTOMER");
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        JwtUtil other = new JwtUtil("a-completely-different-secret-for-another-app-123", 60_000);

        assertThat(jwt.isValid(tampered)).isFalse();
        assertThat(jwt.isValid(other.generateToken("doctor@example.com", "ADMIN"))).isFalse();
        assertThat(jwt.isValid("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("Expired tokens are rejected")
    void expiredTokenRejected() {
        JwtUtil shortLived = new JwtUtil("unit-test-secret-that-is-long-enough-for-hs256!!", -1_000);
        assertThat(shortLived.isValid(shortLived.generateToken("admin", "ADMIN"))).isFalse();
    }

    @Test
    @DisplayName("Short secrets are stretched instead of crashing on startup")
    void shortSecretWorks() {
        JwtUtil weak = new JwtUtil("short", 60_000);
        assertThat(weak.isValid(weak.generateToken("x", "CUSTOMER"))).isTrue();
    }
}
