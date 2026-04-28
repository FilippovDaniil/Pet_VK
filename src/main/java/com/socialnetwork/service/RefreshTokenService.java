package com.socialnetwork.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private final StringRedisTemplate redisTemplate;

    /**
     * Creates a new refresh token for the user.
     * Token format returned to client: "{userId}:{tokenId}"
     * Redis key: "refresh:{userId}:{tokenId}"
     */
    public String createRefreshToken(Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String redisKey = KEY_PREFIX + userId + ":" + tokenId;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(userId), refreshTokenExpiration, TimeUnit.MILLISECONDS);
        return userId + ":" + tokenId;
    }

    public boolean isValid(String rawToken) {
        String[] parts = rawToken.split(":", 2);
        if (parts.length != 2) return false;
        String redisKey = KEY_PREFIX + parts[0] + ":" + parts[1];
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    public Long getUserIdFromToken(String rawToken) {
        String[] parts = rawToken.split(":", 2);
        if (parts.length != 2) return null;
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + parts[0] + ":" + parts[1]);
        return value != null ? Long.parseLong(value) : null;
    }

    public void delete(String rawToken) {
        String[] parts = rawToken.split(":", 2);
        if (parts.length == 2) {
            redisTemplate.delete(KEY_PREFIX + parts[0] + ":" + parts[1]);
        }
    }

    public void deleteAllForUser(Long userId) {
        String pattern = KEY_PREFIX + userId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
