package com.socialnetwork.repository;

import com.socialnetwork.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByAuthorIdAndGroupIsNullOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    Page<Post> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);
}
