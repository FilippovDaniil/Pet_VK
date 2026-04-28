package com.socialnetwork.dto.response;

import com.socialnetwork.entity.Post;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PostResponse {
    private Long id;
    private Long authorId;
    private String authorName;
    private Long groupId;
    private String text;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthor().getId())
                .authorName(post.getAuthor().getFirstName() + " " + post.getAuthor().getLastName())
                .groupId(post.getGroup() != null ? post.getGroup().getId() : null)
                .text(post.getText())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
