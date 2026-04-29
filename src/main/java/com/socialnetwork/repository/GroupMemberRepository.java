package com.socialnetwork.repository;

import com.socialnetwork.entity.GroupMember;
import com.socialnetwork.entity.GroupMemberId;
import com.socialnetwork.entity.GroupMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий участников группы ({@link GroupMember}).
 *
 * <p>Использует составной первичный ключ {@link GroupMemberId} (groupId + userId).
 * Все методы генерируются Spring Data JPA по именам — явный SQL/JPQL не нужен.
 *
 * <p>Типичные сценарии использования:
 * <ul>
 *   <li>Проверка членства перед вступлением/выходом ({@code existsByGroupIdAndUserId})</li>
 *   <li>Проверка прав администратора перед привилегированными операциями ({@code existsByGroupIdAndUserIdAndRole})</li>
 *   <li>Повышение роли участника — получить запись, изменить роль, сохранить ({@code findByGroupIdAndUserId})</li>
 *   <li>Исключение участника из группы ({@code deleteByGroupIdAndUserId})</li>
 * </ul>
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    /**
     * Возвращает запись участника по id группы и id пользователя.
     * Используется для изменения роли: найти → обновить поле role → сохранить.
     */
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Проверяет, является ли пользователь членом группы.
     * Эффективнее {@code findByGroupIdAndUserId} — не загружает объект, только проверяет существование строки.
     */
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Проверяет, есть ли у пользователя конкретная роль в группе.
     * Используется для проверки прав: {@code existsByGroupIdAndUserIdAndRole(groupId, userId, GroupMemberRole.ADMIN)}.
     */
    boolean existsByGroupIdAndUserIdAndRole(Long groupId, Long userId, GroupMemberRole role);

    /**
     * Удаляет участника из группы по составному ключу (groupId + userId).
     * Используется при выходе участника или его исключении администратором.
     * Аннотация {@code @Modifying} не нужна — Spring Data автоматически оборачивает в транзакцию.
     */
    void deleteByGroupIdAndUserId(Long groupId, Long userId);
}
