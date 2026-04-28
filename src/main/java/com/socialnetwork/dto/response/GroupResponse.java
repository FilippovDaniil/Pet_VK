package com.socialnetwork.dto.response;

import com.socialnetwork.entity.Group;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;

    public static GroupResponse from(Group group) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .avatarUrl(group.getAvatarUrl())
                .ownerId(group.getOwner().getId())
                .ownerName(group.getOwner().getFirstName() + " " + group.getOwner().getLastName())
                .createdAt(group.getCreatedAt())
                .build();
    }
}
