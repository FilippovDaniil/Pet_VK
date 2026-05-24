package com.socialnetwork.service;

import com.socialnetwork.dto.request.CommentCreateRequest;
import com.socialnetwork.dto.response.CommentResponse;
import com.socialnetwork.entity.Comment;
import com.socialnetwork.entity.Post;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления комментариями к постам.
 *
 * <p>Обеспечивает создание, чтение и удаление комментариев.
 * Каждый комментарий привязан к конкретному посту и автору.
 *
 * <p>Права на операции:
 * <ul>
 *   <li>Создание — только незаблокированные пользователи</li>
 *   <li>Удаление — только автор комментария</li>
 *   <li>Удаление администратором — без проверки авторства</li>
 * </ul>
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для final-полей (внедрение зависимостей)
public class CommentService {

    // Репозиторий JPA для операций с таблицей комментариев
    private final CommentRepository commentRepository;

    // PostService: нужен для загрузки поста, к которому добавляется комментарий
    private final PostService postService;

    // UserService: нужен для загрузки автора и проверки его статуса (бан)
    private final UserService userService;

    /**
     * Добавляет комментарий к посту.
     *
     * <p>Заблокированные пользователи не могут комментировать — это проверяется
     * до создания объекта, чтобы не делать лишних операций с БД.
     *
     * @param userId  id автора комментария (текущий аутентифицированный пользователь)
     * @param request DTO с id поста и текстом комментария
     * @return DTO созданного комментария
     * @throws ForbiddenException        если пользователь заблокирован
     * @throws ResourceNotFoundException если пост не найден
     */
    @Transactional // Обёртка в транзакцию: сохранение комментария атомарно
    public CommentResponse addComment(Long userId, Long postId, CommentCreateRequest request) {
        User author = userService.getUserById(userId);

        if (author.isBanned()) throw new ForbiddenException("Banned users cannot comment");

        Post post = postService.getPostById(postId);

        Comment comment = Comment.builder()
                .author(author)
                .post(post)
                .text(request.getText())
                .build();

        // Сохраняем в БД и сразу возвращаем DTO
        return CommentResponse.from(commentRepository.save(comment));
    }

    /**
     * Возвращает постраничный список комментариев к посту.
     *
     * <p>Комментарии отсортированы от старых к новым (ASC по createdAt),
     * что соответствует хронологическому порядку диалога под постом.
     *
     * @param postId id поста
     * @param page   номер страницы (начиная с 0)
     * @param size   количество комментариев на странице
     * @return страница с DTO комментариев
     */
    public Page<CommentResponse> getComments(Long postId, int page, int size) {
        // findByPostIdOrderByCreatedAtAsc — Spring Data генерирует запрос по имени метода:
        // WHERE post_id = :postId ORDER BY created_at ASC
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId, PageRequest.of(page, size))
                // Конвертируем каждый Comment в DTO без загрузки всего списка в память
                .map(CommentResponse::from);
    }

    /**
     * Удаляет комментарий. Доступно только автору.
     *
     * @param commentId id удаляемого комментария
     * @param userId    id текущего пользователя (должен совпасть с id автора)
     * @throws ResourceNotFoundException если комментарий не найден
     * @throws ForbiddenException        если комментарий принадлежит другому пользователю
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = getCommentById(commentId);

        // Проверяем авторство: удалить чужой комментарий нельзя
        if (!comment.getAuthor().getId().equals(userId)) throw new ForbiddenException("Not your comment");

        commentRepository.delete(comment);
    }

    /**
     * Удаляет комментарий администратором без проверки авторства.
     *
     * <p>Вызывается из {@link AdminService} при модерации контента.
     *
     * @param commentId id комментария для удаления
     * @throws ResourceNotFoundException если комментарий не найден
     */
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        // Для администратора нет проверки авторства — модератор может удалить любой комментарий
        commentRepository.delete(getCommentById(commentId));
    }

    /**
     * Загружает комментарий по id или бросает исключение.
     *
     * <p>Вспомогательный метод — единая точка получения комментария в этом сервисе.
     *
     * @param commentId id комментария
     * @return найденная сущность {@link Comment}
     * @throws ResourceNotFoundException если комментарий не найден
     */
    private Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                // orElseThrow генерирует 404 через GlobalExceptionHandler
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
    }
}
