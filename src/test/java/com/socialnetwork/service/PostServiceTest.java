package com.socialnetwork.service;

import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.entity.Post;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link PostService}.
 *
 * <p>Проверяет бизнес-логику создания, обновления и удаления постов.
 * Репозиторий и UserService заменены mock-объектами.
 *
 * <p>Ключевые сценарии:
 * <ul>
 *   <li>Создание поста на стене пользователя → ответ содержит id и текст</li>
 *   <li>Попытка удалить чужой пост → ForbiddenException</li>
 *   <li>Удаление своего поста → вызов delete на репозитории</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock UserService userService;

    @InjectMocks PostService postService;

    private User createUser(Long id) {
        return User.builder().id(id).email("u@test.com")
                .firstName("Test").lastName("User").role(Role.ROLE_USER).banned(false).build();
    }

    @Test
    void createWallPost_success() {
        User user = createUser(1L);
        when(userService.getUserById(1L)).thenReturn(user);
        Post saved = Post.builder().id(10L).author(user).text("Hello!").build();
        when(postRepository.save(any())).thenReturn(saved);

        PostCreateRequest req = new PostCreateRequest();
        req.setText("Hello!");

        PostResponse response = postService.createWallPost(1L, req);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getText()).isEqualTo("Hello!");
    }

    @Test
    void deletePost_notOwner_throws() {
        User owner = createUser(1L);
        Post post = Post.builder().id(5L).author(owner).text("test").build();
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(5L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletePost_owner_success() {
        User owner = createUser(1L);
        Post post = Post.builder().id(5L).author(owner).text("test").build();
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));

        postService.deletePost(5L, 1L);

        verify(postRepository).delete(post);
    }
}
