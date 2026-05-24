package com.socialnetwork.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    private String id;        // _id в OpenSearch — всегда String
    private String firstName;
    private String lastName;
    private String email;
    private String avatarUrl;
    private boolean banned;
}
