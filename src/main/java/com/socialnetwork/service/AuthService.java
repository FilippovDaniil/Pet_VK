package com.socialnetwork.service;

import com.socialnetwork.dto.request.LoginRequest;
import com.socialnetwork.dto.request.RefreshTokenRequest;
import com.socialnetwork.dto.request.RegisterRequest;
import com.socialnetwork.dto.response.AuthResponse;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.UserRepository;
import com.socialnetwork.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final BlacklistService blacklistService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already in use: " + request.getEmail());
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.ROLE_USER)
                .build();
        user = userRepository.save(user);
        log.info("Registered new user: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (user.isBanned()) {
            throw new BadRequestException("Account is banned");
        }
        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        if (!refreshTokenService.isValid(rawToken)) {
            throw new BadRequestException("Refresh token is invalid or expired");
        }
        Long userId = refreshTokenService.getUserIdFromToken(rawToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        refreshTokenService.delete(rawToken);
        return buildAuthResponse(user);
    }

    public void logout(String accessToken) {
        long ttl = tokenProvider.getRemainingTtlMillis(accessToken);
        blacklistService.blacklist(accessToken, ttl);
        log.info("User logged out, token blacklisted");
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .build();
    }
}
