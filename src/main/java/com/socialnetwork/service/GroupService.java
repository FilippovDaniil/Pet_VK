package com.socialnetwork.service;

import com.socialnetwork.dto.request.GroupCreateRequest;
import com.socialnetwork.dto.request.PostCreateRequest;
import com.socialnetwork.dto.response.GroupResponse;
import com.socialnetwork.dto.response.PostResponse;
import com.socialnetwork.entity.*;

import java.util.List;
import com.socialnetwork.exception.BadRequestException;
import com.socialnetwork.exception.ForbiddenException;
import com.socialnetwork.exception.ResourceNotFoundException;
import com.socialnetwork.repository.GroupMemberRepository;
import com.socialnetwork.repository.GroupRepository;
import com.socialnetwork.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления группами (сообществами) социальной сети.
 *
 * <p>Группа — сообщество пользователей с ролевой моделью:
 * <ul>
 *   <li><b>OWNER</b> — создатель группы, единственный кто может её удалить</li>
 *   <li><b>ADMIN</b> — может публиковать посты и назначать других администраторов</li>
 *   <li><b>MEMBER</b> — обычный участник, может читать посты</li>
 * </ul>
 *
 * <p>Посты в группе хранятся в той же таблице {@code posts}, что и стеновые посты,
 * но с заполненным полем {@code group} (для стены {@code group == null}).
 */
@Service            // Регистрирует класс как Spring-бин сервисного слоя
@RequiredArgsConstructor // Lombok: конструктор для final-полей (внедрение зависимостей)
public class GroupService {

    // Репозиторий JPA для операций с таблицей групп
    private final GroupRepository groupRepository;

    // Репозиторий участников группы: вступление, выход, проверка ролей
    private final GroupMemberRepository groupMemberRepository;

    // Репозиторий постов: создание и получение постов в группе
    private final PostRepository postRepository;

    // UserService: получение пользователей с кэшированием
    private final UserService userService;

    /**
     * Создаёт новую группу и автоматически добавляет создателя как администратора.
     *
     * <p>Операция выполняется в двух шагах:
     * <ol>
     *   <li>Создаётся запись группы в таблице {@code groups}</li>
     *   <li>Создаётся запись участника в {@code group_members} с ролью ADMIN</li>
     * </ol>
     * Оба шага обёрнуты в одну транзакцию — либо оба успешны, либо rollback.
     *
     * @param userId  id пользователя-создателя (станет владельцем и администратором)
     * @param request DTO с названием, описанием и аватаром группы
     * @return DTO созданной группы
     */
    @Transactional // Транзакция: создание группы и добавление участника — атомарная операция
    public GroupResponse createGroup(Long userId, GroupCreateRequest request) {
        // Загружаем создателя — он станет владельцем (owner) группы
        User owner = userService.getUserById(userId);

        // Создаём сущность группы через Builder
        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .owner(owner)
                .build();

        // Сохраняем группу в БД — получаем объект с присвоенным id
        group = groupRepository.save(group);

        // Добавляем создателя как участника с ролью ADMIN.
        // Составной ключ GroupMemberId(groupId, userId) создаётся явно.
        GroupMember member = GroupMember.builder()
                .id(new GroupMemberId(group.getId(), userId))
                .group(group)
                .user(owner)
                .role(GroupMemberRole.ADMIN) // создатель сразу получает права администратора
                .build();
        groupMemberRepository.save(member);

        return GroupResponse.from(group);
    }

    /**
     * Возвращает информацию о группе по её id.
     *
     * @param groupId id группы
     * @return DTO группы с публичными полями
     * @throws ResourceNotFoundException если группа не найдена
     */
    public GroupResponse getGroup(Long groupId) {
        return GroupResponse.from(findGroupById(groupId));
    }

    /**
     * Вступление пользователя в группу.
     *
     * <p>При вступлении пользователь получает роль MEMBER.
     * Повторное вступление в одну группу не допускается.
     *
     * @param userId  id пользователя
     * @param groupId id группы
     * @throws BadRequestException если пользователь уже является участником
     */
    @Transactional
    public void joinGroup(Long userId, Long groupId) {
        // Проверяем, не является ли пользователь уже участником этой группы
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("Already a member");
        }

        Group group = findGroupById(groupId);
        User user = userService.getUserById(userId);

        // Создаём запись участника с ролью MEMBER (обычный пользователь)
        GroupMember member = GroupMember.builder()
                .id(new GroupMemberId(groupId, userId))
                .group(group)
                .user(user)
                .role(GroupMemberRole.MEMBER)
                .build();
        groupMemberRepository.save(member);
    }

    /**
     * Выход пользователя из группы.
     *
     * <p>Владелец группы не может покинуть её — это предотвращает создание
     * группы без владельца. Перед выходом нужно передать права или удалить группу.
     *
     * @param userId  id пользователя
     * @param groupId id группы
     * @throws BadRequestException если пользователь не является участником или является владельцем
     */
    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        // Нельзя выйти из группы, если ты не участник
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("Not a member");
        }

        Group group = findGroupById(groupId);

        // Владелец не может покинуть группу — это защита от "осиротевших" групп
        if (group.getOwner().getId().equals(userId)) {
            throw new BadRequestException("Owner cannot leave the group");
        }

        // Удаляем запись участника из промежуточной таблицы group_members
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    /**
     * Назначает участника группы администратором.
     *
     * <p>Только действующий администратор группы может повысить роль
     * другого участника до ADMIN. Цель участника должна быть членом группы.
     *
     * @param requesterId  id пользователя, выполняющего действие (должен быть ADMIN)
     * @param groupId      id группы
     * @param targetUserId id участника, которого нужно сделать администратором
     * @throws ForbiddenException        если requester не является администратором группы
     * @throws ResourceNotFoundException если целевой пользователь не является участником
     */
    @Transactional
    public void addGroupAdmin(Long requesterId, Long groupId, Long targetUserId) {
        // Проверяем, что вызывающий является администратором группы
        requireGroupAdmin(requesterId, groupId);

        // Загружаем запись участника, которому повышаем роль
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this group"));

        // Меняем роль на ADMIN — Hibernate обновит запись при коммите транзакции
        member.setRole(GroupMemberRole.ADMIN);
        groupMemberRepository.save(member);
    }

    /**
     * Удаляет группу вместе со всем её содержимым.
     *
     * <p>Удаление разрешено только:
     * <ul>
     *   <li>Владельцу группы (Owner)</li>
     *   <li>Суперадминистратору системы (ROLE_ADMIN)</li>
     * </ul>
     *
     * @param requesterId id пользователя, инициирующего удаление
     * @param groupId     id группы для удаления
     * @throws ForbiddenException если нет прав на удаление
     */
    @Transactional
    public void deleteGroup(Long requesterId, Long groupId) {
        Group group = findGroupById(groupId);
        User requester = userService.getUserById(requesterId);

        // Проверяем право удаления: владелец группы ИЛИ суперадминистратор системы
        boolean isOwner = group.getOwner().getId().equals(requesterId);
        boolean isSuperAdmin = requester.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isSuperAdmin) throw new ForbiddenException("Only owner or admin can delete the group");

        // Каскадное удаление настроено на уровне БД: участники и посты удалятся автоматически
        groupRepository.delete(group);
    }

    /**
     * Создаёт пост в группе.
     *
     * <p>Публиковать посты могут только участники группы.
     * Заблокированные пользователи не могут публиковать контент.
     * В отличие от стеновых постов, поле {@code group} у созданного поста будет заполнено.
     *
     * @param userId  id автора (должен быть участником группы)
     * @param groupId id группы
     * @param request DTO с текстом поста и опциональным изображением
     * @return DTO созданного поста
     * @throws ForbiddenException если пользователь не является участником или заблокирован
     */
    @Transactional
    public PostResponse createGroupPost(Long userId, Long groupId, PostCreateRequest request) {
        // Проверяем членство в группе до создания поста
        requireGroupMember(userId, groupId);

        User author = userService.getUserById(userId);

        // Заблокированный пользователь не может публиковать контент
        if (author.isBanned()) throw new ForbiddenException("Banned users cannot post");

        Group group = findGroupById(groupId);

        // Полное имя класса Post используется, чтобы избежать конфликта с java.lang.Void
        // при наличии import com.socialnetwork.entity.*;
        com.socialnetwork.entity.Post post = com.socialnetwork.entity.Post.builder()
                .author(author)
                .group(group)             // ключевое отличие от стенового поста — group != null
                .text(request.getText())
                .imageUrl(request.getImageUrl())
                .build();

        return PostResponse.from(postRepository.save(post));
    }

    /**
     * Возвращает список групп, в которых состоит пользователь.
     *
     * @param userId id пользователя
     * @return список DTO групп
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getUserGroups(Long userId) {
        return groupMemberRepository.findByUserId(userId).stream()
                .map(member -> GroupResponse.from(member.getGroup()))
                .toList();
    }

    /**
     * Возвращает постраничный список постов группы (от новых к старым).
     *
     * @param groupId id группы
     * @param page    номер страницы (начиная с 0)
     * @param size    количество постов на странице
     * @return страница с DTO постов
     */
    public Page<PostResponse> getGroupPosts(Long groupId, int page, int size) {
        // findByGroupIdOrderByCreatedAtDesc — Spring Data генерирует запрос по имени:
        // WHERE group_id = :groupId ORDER BY created_at DESC
        return postRepository.findByGroupIdOrderByCreatedAtDesc(groupId, PageRequest.of(page, size))
                .map(PostResponse::from);
    }

    /**
     * Загружает группу по id или бросает исключение.
     * Вспомогательный метод — единая точка получения группы в этом сервисе.
     */
    private Group findGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
    }

    /**
     * Проверяет, что пользователь является администратором группы.
     * Если нет — бросает ForbiddenException (403).
     */
    private void requireGroupAdmin(Long userId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, userId, GroupMemberRole.ADMIN)) {
            throw new ForbiddenException("Group admin rights required");
        }
    }

    /**
     * Проверяет, что пользователь является участником группы (в любой роли).
     * Если нет — бросает ForbiddenException (403).
     */
    private void requireGroupMember(Long userId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("Must be a group member to post");
        }
    }
}
