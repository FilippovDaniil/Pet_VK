package com.socialnetwork.repository;

import com.socialnetwork.entity.FriendRequest;
import com.socialnetwork.entity.FriendRequestStatus;
import com.socialnetwork.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с заявками в друзья.
 *
 * <p>Содержит кастомные JPQL-запросы для симметричного поиска заявок —
 * т.е. поиска, который работает независимо от того, кто был инициатором (requester)
 * и кто адресатом (addressee) в конкретной записи.
 */
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    /**
     * Ищет заявку от конкретного отправителя к конкретному адресату (однонаправленный поиск).
     *
     * <p>Генерируемый запрос (по имени метода):
     * {@code WHERE requester = :requester AND addressee = :addressee}
     *
     * @param requester инициатор заявки
     * @param addressee получатель заявки
     * @return Optional с заявкой, или пустой если не найдена
     */
    Optional<FriendRequest> findByRequesterAndAddressee(User requester, User addressee);

    /**
     * Возвращает все входящие заявки к пользователю с определённым статусом.
     *
     * <p>Генерируемый запрос: {@code WHERE addressee = :addressee AND status = :status}
     * Используется для получения списка входящих PENDING-заявок.
     *
     * @param addressee  пользователь-получатель
     * @param status     статус для фильтрации (обычно PENDING)
     * @return список заявок
     */
    List<FriendRequest> findByAddresseeAndStatus(User addressee, FriendRequestStatus status);

    /**
     * Возвращает все принятые заявки (дружбы), в которых участвует пользователь.
     *
     * <p>Симметричный запрос: ищет записи, где пользователь является либо
     * инициатором (requester), либо адресатом (addressee), со статусом ACCEPTED.
     * Используется для получения полного списка друзей пользователя.
     *
     * @param userId id пользователя
     * @return список всех принятых заявок (обе стороны), где участвует данный пользователь
     */
    @Query("SELECT fr FROM FriendRequest fr WHERE " +
           "(fr.requester.id = :userId OR fr.addressee.id = :userId) " +
           "AND fr.status = 'ACCEPTED'")
    List<FriendRequest> findFriendsByUserId(@Param("userId") Long userId);

    /**
     * Проверяет, являются ли два пользователя друзьями (есть ли ACCEPTED-заявка).
     *
     * <p>Симметричная проверка: порядок userId1/userId2 не важен.
     * {@code CASE WHEN COUNT > 0 THEN true ELSE false} — стандартный JPQL-способ
     * вернуть булево значение из агрегирующего запроса.
     *
     * @param userId1 id первого пользователя
     * @param userId2 id второго пользователя
     * @return {@code true} если пользователи являются друзьями
     */
    @Query("SELECT CASE WHEN COUNT(fr) > 0 THEN true ELSE false END FROM FriendRequest fr WHERE " +
           "((fr.requester.id = :userId1 AND fr.addressee.id = :userId2) OR " +
           "(fr.requester.id = :userId2 AND fr.addressee.id = :userId1)) " +
           "AND fr.status = 'ACCEPTED'")
    boolean areFriends(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Ищет любую заявку между двумя пользователями (в любом направлении, в любом статусе).
     *
     * <p>Используется при отправке заявки для проверки дублей:
     * нельзя отправить заявку, если уже есть любая запись между этими пользователями
     * (даже с DECLINED или ACCEPTED статусом).
     * Также используется при удалении дружбы для нахождения записи.
     *
     * @param userId1 id первого пользователя
     * @param userId2 id второго пользователя
     * @return Optional с заявкой (любой статус, любое направление), или пустой если не найдена
     */
    @Query("SELECT fr FROM FriendRequest fr WHERE " +
           "(fr.requester.id = :userId1 AND fr.addressee.id = :userId2) OR " +
           "(fr.requester.id = :userId2 AND fr.addressee.id = :userId1)")
    Optional<FriendRequest> findBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
