package com.socialnetwork.service;

import com.socialnetwork.dto.request.LoginRequest;
import com.socialnetwork.dto.request.RefreshTokenRequest;
import com.socialnetwork.dto.request.RegisterRequest;
import com.socialnetwork.dto.response.AuthResponse;
import com.socialnetwork.entity.Role;
import com.socialnetwork.entity.User;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.UserRepository;
import com.socialnetwork.search.UserSearchService;
import com.socialnetwork.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис аутентификации и управления сессиями пользователей.
 *
 * <p>Реализует полный цикл работы с токенами:
 * <ul>
 *   <li>Регистрация — создание аккаунта и немедленная выдача токенов</li>
 *   <li>Вход — проверка учётных данных и выдача токенов</li>
 *   <li>Обновление — ротация пары токенов через refresh-токен</li>
 *   <li>Выход — аннулирование access-токена через Redis-чёрный список</li>
 * </ul>
 *
 * <p>Архитектура токенов:
 * <ul>
 *   <li><b>Access-токен</b> — JWT, живёт 15 минут, stateless; проверяется подписью.</li>
 *   <li><b>Refresh-токен</b> — непрозрачная строка, хранится в Redis; позволяет
 *       получить новый access-токен без повторного ввода пароля.</li>
 * </ul>
 */
@Service            // Помечает класс как Spring-бин сервисного слоя; регистрируется в контексте
@RequiredArgsConstructor // Lombok: генерирует конструктор для всех final-полей (внедрение зависимостей)
@Slf4j              // Lombok: создаёт поле log = LoggerFactory.getLogger(AuthService.class)
public class AuthService {

    // Репозиторий JPA для операций с таблицей пользователей
    private final UserRepository userRepository;

    // Spring Security PasswordEncoder — кодирует пароли алгоритмом BCrypt
    // и безопасно сравнивает введённый пароль с сохранённым хэшем
    private final PasswordEncoder passwordEncoder;

    // Компонент для генерации и разбора JWT access-токенов
    private final JwtTokenProvider tokenProvider;

    // Сервис хранения refresh-токенов в Redis с автоматическим TTL
    private final RefreshTokenService refreshTokenService;

    // Сервис чёрного списка: кладёт отозванные access-токены в Redis
    private final BlacklistService blacklistService;

    // Сервис поиска: индексирует нового пользователя в OpenSearch после регистрации
    private final UserSearchService userSearchService;

    /**
     * Регистрирует нового пользователя и сразу возвращает пару токенов.
     *
     * <p>Метод транзакционен: если при сохранении возникнет ошибка БД,
     * ни одно изменение не будет зафиксировано.
     *
     * @param request данные регистрации (email, пароль, имя, фамилия)
     * @return {@link AuthResponse} с access- и refresh-токенами
     * @throws BadRequestException если email уже зарегистрирован
     */
    @Transactional // Открывает транзакцию БД; при исключении выполняет rollback
    public AuthResponse register(RegisterRequest request) {
        // Проверяем уникальность email до создания пользователя —
        // email служит логином и должен быть уникальным в системе
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already in use: " + request.getEmail());
        }

        // Создаём сущность пользователя через Builder-паттерн (Lombok @Builder)
        User user = User.builder()
                .email(request.getEmail())
                // Хэшируем пароль BCrypt'ом — в БД никогда не хранится открытый пароль
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                // Все новые пользователи получают роль USER; роль ADMIN назначается вручную
                .role(Role.ROLE_USER)
                .build();

        user = userRepository.save(user);
        userSearchService.indexUser(user);
        log.info("Registered new user: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * Аутентифицирует пользователя по email и паролю.
     *
     * <p>Метод намеренно не помечен {@code @Transactional}: он выполняет
     * только SELECT-запросы, поэтому транзакция не нужна — это экономит ресурсы.
     *
     * @param request учётные данные (email и пароль)
     * @return {@link AuthResponse} с токенами
     * @throws BadCredentialsException если email не найден или пароль неверный
     * @throws BadRequestException     если аккаунт заблокирован администратором
     */
    public AuthResponse login(LoginRequest request) {
        // Ищем пользователя по email; используем BadCredentialsException (а не 404),
        // чтобы злоумышленник не мог определить, существует ли аккаунт с таким email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // BCrypt.matches() сравнивает введённый пароль с хэшем за постоянное время —
        // это защита от timing-атак, когда по скорости ответа можно угадать пароль
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Заблокированный пользователь не должен получать токены,
        // даже если его учётные данные верны
        if (user.isBanned()) {
            throw new BadRequestException("Account is banned");
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * Обновляет пару токенов (access + refresh) по действующему refresh-токену.
     *
     * <p>Реализует паттерн «ротации токенов» (token rotation): каждый раз
     * при использовании refresh-токена он уничтожается и выпускается новый.
     * Это позволяет обнаружить кражу токена — при повторном использовании
     * уже удалённого токена метод бросит исключение.
     *
     * @param request DTO, содержащий refresh-токен клиента
     * @return новый {@link AuthResponse} со свежей парой токенов
     * @throws BadRequestException       если refresh-токен истёк или не существует
     * @throws ResourceNotFoundException если пользователь не найден (аккаунт удалён)
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();

        // Проверяем существование ключа в Redis — если TTL истёк, Redis удалил его автоматически
        if (!refreshTokenService.isValid(rawToken)) {
            throw new BadRequestException("Refresh token is invalid or expired");
        }

        // Извлекаем id пользователя из значения Redis-ключа
        Long userId = refreshTokenService.getUserIdFromToken(rawToken);

        // Загружаем свежую версию пользователя из БД —
        // его роль или статус бана могли измениться с момента последнего входа
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Удаляем использованный refresh-токен — ротация токенов предотвращает replay-атаки
        refreshTokenService.delete(rawToken);

        // Выпускаем новую пару: новый refresh-токен будет записан в Redis
        return buildAuthResponse(user);
    }

    /**
     * Выполняет выход из системы, добавляя access-токен в чёрный список.
     *
     * <p>JWT-токен по природе stateless и остаётся криптографически валидным
     * до истечения срока действия. Чтобы немедленно «аннулировать» токен,
     * мы кладём его в Redis на оставшееся время жизни. Фильтр JwtAuthFilter
     * проверяет каждый входящий токен по этому списку.
     *
     * @param accessToken строка JWT-токена (без префикса «Bearer »)
     */
    public void logout(String accessToken) {
        // Вычисляем, сколько миллисекунд токен ещё «живёт»,
        // чтобы не хранить его в Redis дольше необходимого
        long ttl = tokenProvider.getRemainingTtlMillis(accessToken);

        // Записываем токен в Redis с точным TTL — после истечения Redis удалит запись сам
        blacklistService.blacklist(accessToken, ttl);

        log.info("User logged out, token blacklisted");
    }

    /**
     * Вспомогательный метод: создаёт {@link AuthResponse} для заданного пользователя.
     *
     * <p>Единственное место в сервисе, где генерируются токены — исключает
     * дублирование кода в register(), login() и refresh().
     *
     * @param user пользователь, для которого выпускаются токены
     * @return DTO с access-токеном, refresh-токеном, типом и временем жизни
     */
    private AuthResponse buildAuthResponse(User user) {
        // Генерируем подписанный JWT с claims: sub=userId, email, role
        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        // Создаём refresh-токен и сохраняем его в Redis с настроенным TTL
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Тип "Bearer" — стандарт RFC 6750 для передачи токенов в заголовке Authorization
        // expiresIn=900 секунд = 15 минут — время жизни access-токена
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .build();
    }
}
