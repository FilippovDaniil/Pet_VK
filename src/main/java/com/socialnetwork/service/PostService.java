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

/**
 * Сервис управления публикациями (постами) на стене пользователя.
 *
 * <p>Поддерживает два типа постов:
 * <ul>
 *   <li><b>Посты на стене</b> — привязаны к профилю автора (поле {@code group} == null)</li>
 *   <li><b>Посты в группе</b> — привязаны к группе; создаются через {@link GroupService}</li>
 * </ul>
 *
 * <p>Права на операции:
 * <ul>
 *   <li>Создание — только незаблокированные пользователи</li>
 *   <li>Редактирование / удаление — только автор поста</li>
 *   <li>Удаление администратором — без проверки авторства</li>
 * </ul>
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для final-полей (внедрение зависимостей)
public class PostService {

    // Репозиторий JPA для операций с таблицей постов
    private final PostRepository postRepository;

    // UserService используется для получения автора поста (и проверки бана)
    private final UserService userService;

    /**
     * Создаёт новый пост на стене пользователя.
     *
     * <p>Перед созданием проверяется, что автор не заблокирован —
     * забаненные пользователи не могут публиковать контент.
     *
     * @param userId  id аутентифицированного пользователя — автора поста
     * @param request DTO с текстом поста и опциональной ссылкой на изображение
     * @return DTO созданного поста
     * @throws ForbiddenException если пользователь заблокирован
     */
    @Transactional // Обёртка в транзакцию: если что-то пойдёт не так при save — будет rollback
    public PostResponse createWallPost(Long userId, PostCreateRequest request) {
        // Загружаем автора; UserService кэширует результат — повторный вызов дёшев
        User author = userService.getUserById(userId);

        // Заблокированный пользователь не должен иметь возможность публиковать посты
        if (author.isBanned()) throw new ForbiddenException("Banned users cannot post");

        // Строим сущность поста через Builder; поле group остаётся null — это «стеновый» пост
        Post post = Post.builder()
                .author(author)              // автор поста
                .text(request.getText())     // текст публикации
                .imageUrl(request.getImageUrl()) // опциональная ссылка на изображение (может быть null)
                .build();

        // Сохраняем пост в БД и сразу конвертируем в DTO для ответа клиенту
        return PostResponse.from(postRepository.save(post));
    }

    /**
     * Возвращает постраничный список постов со стены указанного пользователя.
     *
     * <p>Выбираются только «стеновые» посты (у которых {@code group IS NULL}),
     * отсортированные от новых к старым.
     *
     * @param userId id пользователя, чья стена запрашивается
     * @param page   номер страницы (начиная с 0)
     * @param size   количество постов на странице
     * @return страница с DTO постов
     */
    public Page<PostResponse> getWallPosts(Long userId, int page, int size) {
        // Используем специализированный метод репозитория:
        // findByAuthorIdAndGroupIsNullOrderByCreatedAtDesc — JPQL по соглашению об именовании
        // groupIsNull — исключаем посты, принадлежащие группам
        // OrderByCreatedAtDesc — новые посты идут первыми
        return postRepository.findByAuthorIdAndGroupIsNullOrderByCreatedAtDesc(
                        userId, PageRequest.of(page, size))
                // Конвертируем каждую сущность Post в DTO без загрузки всего списка в память
                .map(PostResponse::from);
    }

    /**
     * Обновляет текст и/или изображение существующего поста.
     *
     * <p>Только автор поста может его редактировать — проверка через сравнение id.
     * Поля обновляются только если новое значение передано (не null и не пустое).
     *
     * @param postId  id поста, который нужно обновить
     * @param userId  id текущего пользователя (должен совпасть с id автора)
     * @param request DTO с новыми значениями (могут быть частичными)
     * @return обновлённый пост в виде DTO
     * @throws ResourceNotFoundException если пост не найден
     * @throws ForbiddenException        если текущий пользователь не является автором
     */
    @Transactional
    public PostResponse updatePost(Long postId, Long userId, PostCreateRequest request) {
        // Получаем пост из БД или бросаем 404
        Post post = getPostById(postId);

        // Проверка владельца: только автор может редактировать свой пост
        if (!post.getAuthor().getId().equals(userId))
            throw new ForbiddenException("Not your post");

        // Обновляем только те поля, которые переданы — частичное обновление
        if (StringUtils.hasText(request.getText())) post.setText(request.getText());

        // imageUrl может быть передан явно как пустая строка для удаления картинки
        if (request.getImageUrl() != null) post.setImageUrl(request.getImageUrl());

        // Сохраняем изменения и возвращаем актуальный DTO
        return PostResponse.from(postRepository.save(post));
    }

    /**
     * Удаляет пост. Доступно только автору поста.
     *
     * @param postId id удаляемого поста
     * @param userId id текущего пользователя
     * @throws ResourceNotFoundException если пост не найден
     * @throws ForbiddenException        если текущий пользователь не является автором
     */
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = getPostById(postId);

        // Проверяем авторство: удалить чужой пост обычный пользователь не может
        if (!post.getAuthor().getId().equals(userId))
            throw new ForbiddenException("Not your post");

        // Физически удаляем пост из БД (каскадное удаление комментариев настроено на уровне сущности)
        postRepository.delete(post);
    }

    /**
     * Удаляет пост администратором без проверки авторства.
     *
     * <p>Вызывается из {@link AdminService} при модерации контента.
     * Администратор может удалить любой пост вне зависимости от автора.
     *
     * @param postId id поста для удаления
     * @throws ResourceNotFoundException если пост не найден
     */
    @Transactional
    public void deletePostByAdmin(Long postId) {
        // Просто загружаем и удаляем — без проверки авторства, т.к. это admin-операция
        postRepository.delete(getPostById(postId));
    }

    /**
     * Загружает пост по id или бросает исключение, если пост не существует.
     *
     * <p>Вспомогательный метод, использующийся как внутри класса,
     * так и из других сервисов (например, {@link CommentService} при добавлении комментария).
     *
     * @param postId id поста
     * @return найденная сущность {@link Post}
     * @throws ResourceNotFoundException если пост с таким id не найден
     */
    public Post getPostById(Long postId) {
        return postRepository.findById(postId)
                // orElseThrow генерирует 404-ответ через GlobalExceptionHandler
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
    }
}
