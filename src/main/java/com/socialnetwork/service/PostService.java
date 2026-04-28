package com.socialnetwork.service;

import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.entity.Post;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserService userService;

    @Transactional
    public PostResponse createWallPost(Long userId, PostCreateRequest request) {
        User author = userService.getUserById(userId);
        if (author.isBanned()) throw new ForbiddenException("Banned users cannot post");
        Post post = Post.builder()
                .author(author)
                .text(request.getText())
                .imageUrl(request.getImageUrl())
                .build();
        return PostResponse.from(postRepository.save(post));
    }

    public Page<PostResponse> getWallPosts(Long userId, int page, int size) {
        return postRepository.findByAuthorIdAndGroupIsNullOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(PostResponse::from);
    }

    @Transactional
    public PostResponse updatePost(Long postId, Long userId, PostCreateRequest request) {
        Post post = getPostById(postId);
        if (!post.getAuthor().getId().equals(userId)) throw new ForbiddenException("Not your post");
        if (StringUtils.hasText(request.getText())) post.setText(request.getText());
        if (request.getImageUrl() != null) post.setImageUrl(request.getImageUrl());
        return PostResponse.from(postRepository.save(post));
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = getPostById(postId);
        if (!post.getAuthor().getId().equals(userId)) throw new ForbiddenException("Not your post");
        postRepository.delete(post);
    }

    @Transactional
    public void deletePostByAdmin(Long postId) {
        postRepository.delete(getPostById(postId));
    }

    public Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
    }
}
