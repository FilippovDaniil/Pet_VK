package com.socialnetwork.service;

import com.socialnetwork.dto.request.UpdateProfileRequest;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    @Value("${app.upload.path}")
    private String uploadPath;

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);
        if (StringUtils.hasText(request.getFirstName())) user.setFirstName(request.getFirstName());
        if (StringUtils.hasText(request.getLastName())) user.setLastName(request.getLastName());
        if (request.getBio() != null) user.setBio(request.getBio());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public UserResponse uploadAvatar(Long userId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String ext = getExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + ext;
        Path dir = Paths.get(uploadPath);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename));

        User user = getUserById(userId);
        user.setAvatarUrl("/uploads/avatars/" + filename);
        return UserResponse.from(userRepository.save(user));
    }

    public Page<UserResponse> searchUsers(String query, int page, int size) {
        return userRepository.searchByQuery(query, PageRequest.of(page, size))
                .map(UserResponse::from);
    }

    private String getExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return "";
    }
}
