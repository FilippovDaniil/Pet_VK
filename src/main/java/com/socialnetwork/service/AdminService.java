package com.socialnetwork.service;

import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final PostService postService;
    private final CommentService commentService;

    @Transactional
    public void banUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        if (user.isBanned()) throw new BadRequestException("User is already banned");
        user.setBanned(true);
        userRepository.save(user);
        log.info("Admin banned user {}", userId);
    }

    @Transactional
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        if (!user.isBanned()) throw new BadRequestException("User is not banned");
        user.setBanned(false);
        userRepository.save(user);
        log.info("Admin unbanned user {}", userId);
    }

    public Page<UserResponse> getAllUsers(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size)).map(UserResponse::from);
    }

    public void deletePost(Long postId) {
        postService.deletePostByAdmin(postId);
    }

    public void deleteComment(Long commentId) {
        commentService.deleteCommentByAdmin(commentId);
    }
}
