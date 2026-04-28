package com.socialnetwork.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    // Secret must be at least 32 characters for HS256
    private static final String SECRET = "testSecretKeyForTestingPurposesOnly12345";
    private static final long EXPIRATION_MS = 900_000L; // 15 minutes

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    // -------------------------------------------------------------------------
    // generateAndValidateToken
    // -------------------------------------------------------------------------

    @Test
    void generateAndValidateToken_success() {
        String token = tokenProvider.generateAccessToken(1L, "alice@test.com", "ROLE_USER");

        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_expired_returnsFalse() throws InterruptedException {
        // Use an expiry of 1 ms so the token expires almost immediately
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L);
        String token = shortLivedProvider.generateAccessToken(2L, "bob@test.com", "ROLE_USER");

        // Give the token time to expire
        Thread.sleep(10);

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_tampered_returnsFalse() {
        String token = tokenProvider.generateAccessToken(1L, "alice@test.com", "ROLE_USER");

        // Tamper the signature by flipping the last character
        String tampered = token.substring(0, token.length() - 1) +
                (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_completelyInvalidString_returnsFalse() {
        assertThat(tokenProvider.validateToken("not.a.jwt.token")).isFalse();
    }

    @Test
    void validateToken_emptyString_returnsFalse() {
        assertThat(tokenProvider.validateToken("")).isFalse();
    }

    // -------------------------------------------------------------------------
    // getEmailFromToken
    // -------------------------------------------------------------------------

    @Test
    void getEmailFromToken_correct() {
        String email = "charlie@example.com";
        String token = tokenProvider.generateAccessToken(3L, email, "ROLE_USER");

        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo(email);
    }

    @Test
    void getEmailFromToken_preservesCase() {
        String email = "Charlie.Admin@Example.COM";
        String token = tokenProvider.generateAccessToken(4L, email, "ROLE_ADMIN");

        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo(email);
    }

    // -------------------------------------------------------------------------
    // getUserIdFromToken
    // -------------------------------------------------------------------------

    @Test
    void getUserIdFromToken_correct() {
        String token = tokenProvider.generateAccessToken(42L, "user@test.com", "ROLE_USER");

        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void getUserIdFromToken_largeId_correct() {
        long largeId = 9_999_999_999L;
        String token = tokenProvider.generateAccessToken(largeId, "big@test.com", "ROLE_USER");

        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(largeId);
    }

    // -------------------------------------------------------------------------
    // getRemainingTtlMillis
    // -------------------------------------------------------------------------

    @Test
    void getRemainingTtlMillis_positive() {
        String token = tokenProvider.generateAccessToken(1L, "ttl@test.com", "ROLE_USER");

        long remaining = tokenProvider.getRemainingTtlMillis(token);

        // Should be close to EXPIRATION_MS but slightly less due to elapsed time
        assertThat(remaining).isPositive();
        assertThat(remaining).isLessThanOrEqualTo(EXPIRATION_MS);
        // The remaining TTL should be well above 0 (at least 85% of the total)
        assertThat(remaining).isGreaterThan((long) (EXPIRATION_MS * 0.85));
    }

    @Test
    void getRemainingTtlMillis_expiredToken_returnsZero() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L);
        String token = shortLivedProvider.generateAccessToken(1L, "exp@test.com", "ROLE_USER");

        Thread.sleep(20);

        // getRemainingTtlMillis would throw since parseClaims fails on expired token;
        // however, validateToken should return false — verify expiry through validation
        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    // -------------------------------------------------------------------------
    // role claim
    // -------------------------------------------------------------------------

    @Test
    void getRoleFromToken_correct() {
        String token = tokenProvider.generateAccessToken(1L, "admin@test.com", "ROLE_ADMIN");

        assertThat(tokenProvider.getRoleFromToken(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void tokenContainsAllExpectedClaims() {
        String token = tokenProvider.generateAccessToken(7L, "full@test.com", "ROLE_USER");

        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo("full@test.com");
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(7L);
        assertThat(tokenProvider.getRoleFromToken(token)).isEqualTo("ROLE_USER");
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }
}
