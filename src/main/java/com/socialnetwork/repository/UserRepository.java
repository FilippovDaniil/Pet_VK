package com.socialnetwork.repository;

import com.socialnetwork.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Репозиторий для работы с пользователями в базе данных.
 *
 * <p><b>Spring Data JPA:</b><br>
 * Интерфейс расширяет {@link JpaRepository}, который предоставляет готовые методы:
 * {@code findById()}, {@code save()}, {@code delete()}, {@code findAll(Pageable)} и другие.
 * Spring Data автоматически создаёт реализацию интерфейса при запуске приложения —
 * писать SQL или HQL вручную не нужно для стандартных CRUD-операций.
 *
 * <p>Параметры типа {@code JpaRepository<User, Long>}:
 * <ul>
 *   <li>{@code User} — тип управляемой сущности</li>
 *   <li>{@code Long} — тип первичного ключа</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Ищет пользователя по адресу электронной почты.
     *
     * <p>Spring Data генерирует запрос по имени метода:
     * {@code SELECT * FROM users WHERE email = :email}
     * Возвращает {@link Optional} — безопасную обёртку, предотвращающую NPE
     * при отсутствии пользователя.
     *
     * @param email email для поиска (уникальное поле)
     * @return Optional с пользователем, или пустой Optional если не найден
     */
    Optional<User> findByEmail(String email);

    /**
     * Проверяет существование пользователя с указанным email.
     *
     * <p>Генерируемый запрос: {@code SELECT COUNT(*) > 0 FROM users WHERE email = :email}
     * Эффективнее чем {@code findByEmail().isPresent()}: не загружает объект User из БД,
     * а только проверяет существование записи.
     *
     * <p>Используется при регистрации для проверки уникальности email.
     *
     * @param email email для проверки
     * @return {@code true} если пользователь с таким email уже существует
     */
    boolean existsByEmail(String email);

    /**
     * Полнотекстовый поиск пользователей по подстроке в имени, фамилии или email.
     *
     * <p><b>JPQL-запрос:</b> ищет вхождение {@code query} (без учёта регистра) в трёх полях.
     * {@code LOWER()} + {@code LIKE '%:query%'} — стандартный способ регистронезависимого поиска.
     * Это работает медленнее на больших таблицах без индекса, но достаточно для небольших проектов.
     *
     * <p>{@code @Query} используется вместо именования метода, т.к. условие с OR
     * через имя метода стало бы нечитаемым: {@code findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase()}.
     *
     * @param query    строка поиска (часть имени, фамилии или email)
     * @param pageable параметры пагинации (номер страницы, размер, сортировка)
     * @return страница пользователей, удовлетворяющих запросу
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<User> searchByQuery(@Param("query") String query, Pageable pageable);
}
