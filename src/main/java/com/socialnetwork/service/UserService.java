package com.socialnetwork.service;

import com.socialnetwork.dto.request.UpdateProfileRequest;
import com.socialnetwork.dto.response.UserResponse;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.UserRepository;
import com.socialnetwork.search.UserDocument;
import com.socialnetwork.search.UserSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Сервис управления профилями пользователей.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>Получение пользователей по id и email (с кэшированием)</li>
 *   <li>Обновление профиля (имя, фамилия, биография)</li>
 *   <li>Загрузку аватара на файловую систему сервера</li>
 *   <li>Полнотекстовый поиск пользователей с пагинацией</li>
 * </ul>
 *
 * <p>Кэширование реализовано через Spring Cache (обычно Redis или Caffeine).
 * При обновлении данных пользователя кэш автоматически инвалидируется
 * аннотацией {@code @CacheEvict}, чтобы следующий запрос получил свежие данные.
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для всех final-полей — внедрение зависимостей
@Slf4j              // Lombok: создаёт logger (SLF4J) для логирования событий
public class UserService {

    // Путь к каталогу загрузки файлов берётся из application.properties/yml:
    // app.upload.path=/var/uploads/avatars (или иной настроенный путь)
    @Value("${app.upload.path}")
    private String uploadPath;

    // Репозиторий JPA для всех операций с таблицей пользователей
    private final UserRepository userRepository;

    // Сервис полнотекстового поиска через OpenSearch (graceful degradation: не ломает при недоступности)
    private final UserSearchService userSearchService;

    /**
     * Возвращает пользователя по id с кэшированием результата.
     *
     * <p>При первом вызове выполняет запрос к БД и сохраняет результат в кэше
     * {@code users} под ключом {@code id}. При последующих вызовах с тем же id
     * Spring возвращает объект прямо из кэша, минуя БД.
     *
     * <p>Используется другими сервисами (FriendService, PostService и т.д.)
     * как единая точка получения пользователя — благодаря кэшу повторные
     * обращения к одному пользователю в рамках одного запроса не бьют в БД.
     *
     * @param id идентификатор пользователя
     * @return найденная сущность {@link User}
     * @throws ResourceNotFoundException если пользователь с таким id не существует
     */
    @Cacheable(value = "users", key = "#id") // Кэшируем результат; ключ кэша = id пользователя
    public User getUserById(Long id) {
        // orElseThrow пробрасывает 404-исключение, если пользователь не найден
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /**
     * Возвращает пользователя по email без кэширования.
     *
     * <p>Используется при аутентификации и в Spring Security UserDetailsService.
     * Кэширование по email не применяется, так как поиск по email происходит
     * только при входе в систему и не требует оптимизации повторных запросов.
     *
     * @param email адрес электронной почты (уникальный)
     * @return найденная сущность {@link User}
     * @throws ResourceNotFoundException если пользователь не найден
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    /**
     * Обновляет профильные данные пользователя и инвалидирует кэш.
     *
     * <p>Обновляются только те поля, которые явно переданы (не пустые строки).
     * Это позволяет делать частичное обновление профиля: если клиент
     * передаёт только новую биографию, имя и фамилия остаются прежними.
     *
     * <p>{@code @CacheEvict} удаляет запись из кэша {@code users} после успешного
     * сохранения, чтобы следующий {@code getUserById} загрузил актуальные данные.
     *
     * @param userId  id пользователя, чей профиль обновляется
     * @param request DTO с новыми значениями полей (могут быть null/пустыми)
     * @return обновлённый профиль в виде {@link UserResponse}
     */
    @Transactional  // Транзакция гарантирует атомарность: сохранение и инвалидация кэша — одно целое
    @CacheEvict(value = "users", key = "#userId") // Удаляем устаревшую запись из кэша после обновления
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        // Получаем managed-сущность из БД (или кэша) для последующего обновления
        User user = getUserById(userId);

        // StringUtils.hasText() проверяет, что строка не null и не состоит только из пробелов
        // Это позволяет клиенту не передавать поле, если оно не должно меняться
        if (StringUtils.hasText(request.getFirstName())) user.setFirstName(request.getFirstName());
        if (StringUtils.hasText(request.getLastName()))  user.setLastName(request.getLastName());

        // Биография может быть намеренно очищена (пустая строка допустима),
        // поэтому проверяем только на null, а не на hasText
        if (request.getBio() != null) user.setBio(request.getBio());

        User saved = userRepository.save(user);
        userSearchService.indexUser(saved);
        return UserResponse.from(saved);
    }

    /**
     * Загружает файл аватара на сервер и обновляет ссылку на него в профиле.
     *
     * <p>Файл сохраняется с UUID-именем, чтобы избежать коллизий имён и
     * предотвратить path-traversal атаки (злоумышленник не может управлять именем файла).
     * Расширение оригинального файла сохраняется для корректного определения типа браузером.
     *
     * <p>{@code @CacheEvict} инвалидирует кэш, так как {@code avatarUrl} изменился.
     *
     * @param userId id пользователя
     * @param file   загружаемый файл (multipart/form-data)
     * @return обновлённый профиль со ссылкой на новый аватар
     * @throws BadRequestException если файл пустой
     * @throws IOException         если произошла ошибка при записи на диск
     */
    @Transactional
    @CacheEvict(value = "users", key = "#userId") // Сбрасываем кэш, т.к. avatarUrl изменился
    public UserResponse uploadAvatar(Long userId, MultipartFile file) throws IOException {
        // Пустой файл — ошибка клиента; проверяем до попытки сохранения
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        // Извлекаем расширение файла (.jpg, .png и т.д.) для сохранения в имени
        String ext = getExtension(file.getOriginalFilename());

        // UUID гарантирует уникальность имени файла и исключает перезапись чужих аватаров
        String filename = UUID.randomUUID() + ext;

        // Получаем объект пути к каталогу загрузки (настроен в application.properties)
        Path dir = Paths.get(uploadPath);

        // Создаём каталог и все промежуточные директории, если их ещё нет
        Files.createDirectories(dir);

        // Копируем байты файла из HTTP-запроса в файл на диске
        Files.copy(file.getInputStream(), dir.resolve(filename));

        // Загружаем пользователя из БД для обновления ссылки на аватар
        User user = getUserById(userId);

        // Формируем публичный URL аватара, который будет возвращаться клиентам
        // Этот путь должен быть настроен в StaticResourceConfig как раздача статики
        user.setAvatarUrl("/uploads/avatars/" + filename);

        User saved = userRepository.save(user);
        userSearchService.indexUser(saved);
        return UserResponse.from(saved);
    }

    /**
     * Ищет пользователей по подстроке в имени или email с постраничной выборкой.
     *
     * <p>Делегирует выполнение кастомному JPQL-запросу в {@code UserRepository#searchByQuery}.
     * Результат оборачивается в {@link Page}, что позволяет клиенту получать
     * данные порционно и не загружать всех пользователей в память.
     *
     * @param query строка поиска (подстрока имени, фамилии или email)
     * @param page  номер страницы (начиная с 0)
     * @param size  количество записей на странице
     * @return страница с DTO пользователей, удовлетворяющих запросу
     */
    public Page<UserResponse> searchUsers(String query, int page, int size) {
        // Пробуем OpenSearch — полнотекстовый поиск с нечёткостью (fuzziness)
        List<UserDocument> docs = userSearchService.search(query, page, size);
        if (!docs.isEmpty()) {
            List<UserResponse> results = docs.stream()
                    .map(UserResponse::fromDocument)
                    .toList();
            return new PageImpl<>(results, PageRequest.of(page, size), results.size());
        }
        // Fallback: PostgreSQL LIKE если OpenSearch недоступен или индекс пуст
        return userRepository.searchByQuery(query, PageRequest.of(page, size))
                .map(UserResponse::from);
    }

    /**
     * Извлекает расширение файла (включая точку) из его имени.
     *
     * <p>Например, для «avatar.jpeg» вернёт «.jpeg».
     * Если файл без расширения — вернёт пустую строку.
     *
     * @param filename оригинальное имя файла из multipart-запроса
     * @return расширение с точкой или пустая строка
     */
    private String getExtension(String filename) {
        // Проверяем, что имя не null и содержит точку (иначе расширения нет)
        if (filename != null && filename.contains(".")) {
            // lastIndexOf('.') находим последнюю точку — защита от имён вида «file.tar.gz»
            return filename.substring(filename.lastIndexOf('.'));
        }
        return ""; // Файл без расширения — возвращаем пустую строку
    }
}
