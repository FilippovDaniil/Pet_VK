package com.socialnetwork.security;

import com.socialnetwork.entity.User;
import com.socialnetwork.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Реализация контракта Spring Security для загрузки данных пользователя.
 *
 * <p><b>Роль в системе безопасности:</b><br>
 * {@link UserDetailsService} — ключевой интерфейс Spring Security. Когда фреймворку нужно
 * аутентифицировать пользователя (при логине по логину/паролю или при проверке JWT),
 * он обращается к этому сервису, чтобы получить данные из базы данных.
 *
 * <p><b>Почему "Custom"?</b><br>
 * Spring Security предоставляет встроенные реализации (InMemoryUserDetailsManager, JdbcUserDetailsManager),
 * но они не знают о нашей модели {@link User}. Мы пишем свою реализацию, чтобы:
 * <ul>
 *   <li>Искать пользователя по email, а не по username</li>
 *   <li>Передавать наш кастомный флаг {@code isBanned} как {@code accountLocked}</li>
 *   <li>Корректно обрабатывать OAuth2-пользователей, у которых нет пароля</li>
 * </ul>
 *
 * <p><b>Место вызова:</b><br>
 * {@code JwtAuthenticationFilter} вызывает {@code loadUserByUsername(email)} при каждом
 * запросе с валидным JWT, чтобы убедиться, что пользователь всё ещё существует
 * и не заблокирован.
 *
 * @see UserDetailsService
 * @see UserDetails
 */
@Service
// @Service — специализация @Component для сервисного слоя.
// Семантически указывает, что этот класс содержит бизнес-логику.
// Spring регистрирует его как Bean и внедряет туда, где требуется UserDetailsService.
@RequiredArgsConstructor
// Lombok генерирует конструктор для всех final-полей.
// Spring автоматически инжектирует UserRepository через этот конструктор.
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * Репозиторий для работы с пользователями в базе данных.
     * Spring Data JPA автоматически реализует интерфейс — нам не нужно писать SQL.
     */
    private final UserRepository userRepository;

    /**
     * Загружает данные пользователя по его email для нужд Spring Security.
     *
     * <p><b>Важный нюанс именования:</b><br>
     * Метод называется {@code loadUserByUsername}, хотя мы ищем по email.
     * В Spring Security понятие "username" абстрактно — это любой уникальный идентификатор.
     * В нашем приложении роль username выполняет email.
     *
     * <p><b>Связь с JWT-фильтром:</b><br>
     * JWT-фильтр извлекает email из токена и передаёт его сюда.
     * Мы идём в БД чтобы получить актуальное состояние (не заблокирован ли пользователь),
     * так как статус блокировки не закодирован в токене.
     *
     * @param email email пользователя (играет роль username в терминах Spring Security)
     * @return объект {@link UserDetails} с данными пользователя для аутентификации
     * @throws UsernameNotFoundException если пользователь с таким email не найден в БД
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Ищем пользователя в БД по email.
        // findByEmail() возвращает Optional<User> — безопасную обёртку над возможно-null значением.
        // orElseThrow() — если Optional пуст (пользователь не найден), выбрасывает исключение.
        // UsernameNotFoundException — специализированное исключение Spring Security,
        // которое AuthenticationManager перехватывает и превращает в BadCredentialsException
        // (чтобы не раскрывать клиенту, существует ли такой email).
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Строим объект UserDetails с помощью встроенного Builder'а Spring Security.
        // Важно: мы используем полное имя класса (FQCN), чтобы избежать конфликта имён
        // между нашим com.socialnetwork.entity.User и
        // org.springframework.security.core.userdetails.User.
        return org.springframework.security.core.userdetails.User.builder()
                // username в контексте Spring Security = email в нашей системе.
                // Именно это значение будет возвращено через authentication.getName().
                .username(user.getEmail())

                // Пароль нужен Spring Security для встроенной аутентификации по форме логина.
                // OAuth2-пользователи входят через Google и не имеют пароля в нашей БД.
                // Пустая строка "" вместо null предотвращает NPE внутри Spring Security.
                .password(user.getPassword() != null ? user.getPassword() : "")

                // GrantedAuthority — интерфейс Spring Security для представления прав/ролей.
                // SimpleGrantedAuthority — простейшая реализация, хранящая роль как строку.
                // List.of() создаёт неизменяемый список — пользователь имеет ровно одну роль.
                // role.name() возвращает строковое представление enum, например "ROLE_USER".
                // Префикс "ROLE_" обязателен для совместимости с hasRole() в SecurityConfig:
                //   hasRole("ADMIN") внутри проверяет наличие authority "ROLE_ADMIN".
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().name())))

                // accountLocked — если true, Spring Security отклонит аутентификацию
                // с исключением LockedException, даже если пароль правильный.
                // Это позволяет банить пользователей без удаления их аккаунтов.
                .accountLocked(user.isBanned())

                .build();
    }
}
