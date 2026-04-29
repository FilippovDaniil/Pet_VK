package com.socialnetwork.entity;

// Импорты JPA для маппинга сущности, составного ключа и связей
import jakarta.persistence.*;
// Аннотации Lombok для генерации шаблонного кода
import lombok.*;
// Hibernate-аннотация для автоматической простановки временной метки при создании записи
import org.hibernate.annotations.CreationTimestamp;

// LocalDateTime для хранения даты и времени без привязки к часовому поясу
import java.time.LocalDateTime;

/**
 * Сущность участника группы (связь пользователь-группа).
 *
 * <p>Реализует связь многие-ко-многим между пользователями и группами
 * с дополнительными атрибутами: ролью участника и датой вступления.
 * Аналог записи о членстве пользователя в сообществе VK.</p>
 *
 * <p>Использует составной первичный ключ {@link GroupMemberId} (group_id + user_id),
 * что гарантирует уникальность пары "группа-пользователь" на уровне БД.
 * Один пользователь не может быть участником одной группы дважды.</p>
 *
 * <p>Таблица БД: {@code group_members}</p>
 *
 * @see GroupMemberId
 * @see GroupMemberRole
 * @see Group
 * @see User
 */
@Entity
// Объявляет класс управляемой JPA-сущностью.
// Hibernate будет отслеживать изменения объектов в Persistence Context.
@Table(name = "group_members")
// Явное имя таблицы. Промежуточная таблица связи M:N обычно именуется
// как комбинация имён связанных таблиц в snake_case.
@Getter
// Lombok: генерирует геттеры для всех полей
@Setter
// Lombok: генерирует сеттеры для всех полей
@Builder
// Lombok: паттерн Builder — GroupMember.builder().group(group).user(user).build()
@NoArgsConstructor
// Lombok: конструктор без аргументов, обязательный для JPA
@AllArgsConstructor
// Lombok: конструктор со всеми полями, необходимый для @Builder
public class GroupMember {

    /**
     * Составной первичный ключ участника группы (group_id + user_id).
     * Обеспечивает уникальность каждой пары "группа-пользователь".
     *
     * @see GroupMemberId
     */
    @EmbeddedId
    // @EmbeddedId: указывает, что первичный ключ сущности является составным и встроенным.
    // В отличие от @Id (простой ключ), @EmbeddedId ссылается на отдельный класс-ключ (@Embeddable),
    // который содержит все поля составного первичного ключа.
    // Это предпочтительный способ реализации составного ключа в JPA 2+
    // (альтернатива @IdClass менее наглядна).
    private GroupMemberId id;

    /**
     * Группа, членом которой является пользователь.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    // @ManyToOne: одна группа может иметь много участников (N:1 со стороны GroupMember).
    // LAZY-загрузка: данные группы загружаются только при явном обращении к полю group.
    @MapsId("groupId")
    // @MapsId: связывает поле group с полем groupId внутри составного ключа GroupMemberId.
    // Это означает, что при установке group.id значение автоматически попадает в id.groupId.
    // Это устраняет необходимость вручную синхронизировать значения ключа и ссылки на объект.
    @JoinColumn(name = "group_id")
    // Имя колонки внешнего ключа в таблице "group_members".
    // Эта же колонка является частью составного первичного ключа.
    private Group group;

    /**
     * Пользователь, являющийся участником группы.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    // @ManyToOne: один пользователь может состоять во многих группах.
    // LAZY-загрузка: данные пользователя загружаются только при обращении к полю user.
    @MapsId("userId")
    // @MapsId: связывает поле user с полем userId в GroupMemberId.
    // user.id автоматически синхронизируется с id.userId.
    @JoinColumn(name = "user_id")
    // user_id: колонка внешнего ключа и одновременно часть составного первичного ключа.
    private User user;

    /**
     * Роль пользователя внутри группы (обычный участник или администратор).
     * Определяет права пользователя в рамках данного конкретного сообщества.
     *
     * @see GroupMemberRole
     */
    @Enumerated(EnumType.STRING)
    // EnumType.STRING: хранит строковое имя константы ("MEMBER", "ADMIN") в БД.
    // Предпочтительнее EnumType.ORDINAL, который хранит числовой индекс —
    // добавление нового элемента enum изменит индексы и сломает данные в БД.
    @Column(nullable = false, length = 20)
    // nullable = false: роль обязательна для каждого участника (NOT NULL в БД).
    // length = 20: достаточно для "MEMBER" (6 символов) и "ADMIN" (5 символов).
    @Builder.Default
    // @Builder.Default: при создании через Builder обеспечивает значение по умолчанию.
    // БЕЗ этой аннотации Builder проигнорирует инициализатор и установит null.
    private GroupMemberRole role = GroupMemberRole.MEMBER; // По умолчанию новый участник получает роль обычного члена

    /**
     * Дата и время вступления пользователя в группу.
     * Устанавливается автоматически Hibernate при сохранении.
     * После создания не изменяется.
     */
    @CreationTimestamp
    // Hibernate простанавливает текущее время при INSERT — фиксирует момент вступления в группу.
    @Column(name = "joined_at", updatable = false)
    // joined_at: семантически более точное имя, чем created_at — "дата вступления".
    // updatable = false: дата вступления неизменна после записи в БД.
    private LocalDateTime joinedAt;
}
