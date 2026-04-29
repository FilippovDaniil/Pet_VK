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

/**
 * Сервис административных операций платформы.
 *
 * <p>Предоставляет функциональность для модерации контента и управления пользователями.
 * Все методы этого сервиса вызываются только из {@code AdminController},
 * доступ к которому ограничен ролью {@code ROLE_ADMIN}.
 *
 * <p>Доступные административные функции:
 * <ul>
 *   <li>Блокировка/разблокировка пользователей</li>
 *   <li>Просмотр списка всех пользователей</li>
 *   <li>Удаление постов и комментариев (модерация контента)</li>
 * </ul>
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для final-полей (внедрение зависимостей)
@Slf4j              // Lombok: создаёт logger для аудита административных действий
public class AdminService {

    // Прямой доступ к репозиторию пользователей (без кэша UserService — для бана актуальные данные важны)
    private final UserRepository userRepository;

    // PostService: делегирует удаление поста без проверки авторства
    private final PostService postService;

    // CommentService: делегирует удаление комментария без проверки авторства
    private final CommentService commentService;

    /**
     * Блокирует учётную запись пользователя.
     *
     * <p>После блокировки:
     * <ul>
     *   <li>Spring Security будет отклонять попытки входа (accountLocked)</li>
     *   <li>Пользователь не сможет публиковать посты и комментарии</li>
     *   <li>Существующие JWT-токены пользователя не аннулируются автоматически —
     *       для этого потребуется отдельный механизм (например, принудительный logout)</li>
     * </ul>
     *
     * @param userId id блокируемого пользователя
     * @throws BadRequestException если пользователь не найден или уже заблокирован
     */
    @Transactional // Транзакция: сохранение флага бана атомарно
    public void banUser(Long userId) {
        // Загружаем пользователя напрямую из репозитория, минуя кэш UserService
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));

        // Повторная блокировка уже заблокированного пользователя — ошибка администратора
        if (user.isBanned()) throw new BadRequestException("User is already banned");

        // Устанавливаем флаг бана — Hibernate запишет изменение в БД при коммите транзакции
        user.setBanned(true);
        userRepository.save(user);

        // Логируем для аудита: кто, когда и кого заблокировал
        log.info("Admin banned user {}", userId);
    }

    /**
     * Снимает блокировку с учётной записи пользователя.
     *
     * <p>После разблокировки пользователь снова может войти в систему.
     *
     * @param userId id разблокируемого пользователя
     * @throws BadRequestException если пользователь не найден или не заблокирован
     */
    @Transactional
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));

        // Снять бан с незаблокированного пользователя — ошибка администратора
        if (!user.isBanned()) throw new BadRequestException("User is not banned");

        user.setBanned(false);
        userRepository.save(user);

        log.info("Admin unbanned user {}", userId);
    }

    /**
     * Возвращает постраничный список всех зарегистрированных пользователей.
     *
     * <p>Используется администратором для просмотра и управления аккаунтами.
     * Результаты сортируются по умолчанию (по id, восходящий порядок).
     *
     * @param page номер страницы (начиная с 0)
     * @param size количество пользователей на странице
     * @return страница с DTO пользователей
     */
    public Page<UserResponse> getAllUsers(int page, int size) {
        // findAll(Pageable) — встроенный метод JpaRepository для постраничной выборки всех записей
        return userRepository.findAll(PageRequest.of(page, size)).map(UserResponse::from);
    }

    /**
     * Удаляет пост от имени администратора (без проверки авторства).
     *
     * <p>Делегирует удаление в {@link PostService#deletePostByAdmin(Long)}.
     *
     * @param postId id удаляемого поста
     */
    public void deletePost(Long postId) {
        // Прокси-вызов к PostService — там реализована логика удаления и проверка существования
        postService.deletePostByAdmin(postId);
    }

    /**
     * Удаляет комментарий от имени администратора (без проверки авторства).
     *
     * <p>Делегирует удаление в {@link CommentService#deleteCommentByAdmin(Long)}.
     *
     * @param commentId id удаляемого комментария
     */
    public void deleteComment(Long commentId) {
        // Прокси-вызов к CommentService — там реализована логика удаления
        commentService.deleteCommentByAdmin(commentId);
    }
}
