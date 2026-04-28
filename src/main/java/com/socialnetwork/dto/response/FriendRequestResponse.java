package com.socialnetwork.dto.response;

import com.socialnetwork.entity.FriendRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FriendRequestResponse {
    private Long id;
    private Long requesterId;
    private String requesterName;
    private Long addresseeId;
    private String status;
    private LocalDateTime createdAt;

    public static FriendRequestResponse from(FriendRequest fr) {
        return FriendRequestResponse.builder()
                .id(fr.getId())
                .requesterId(fr.getRequester().getId())
                .requesterName(fr.getRequester().getFirstName() + " " + fr.getRequester().getLastName())
                .addresseeId(fr.getAddressee().getId())
                .status(fr.getStatus().name())
                .createdAt(fr.getCreatedAt())
                .build();
    }
}
