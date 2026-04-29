package com.socialnetwork.entity;

// Импорты JPA для объявления сущности, уникальных ограничений и связей
import jakarta.persistence.*;
// Аннотации Lombok для генерации шаблонного кода
import lombok.*;
// Hibernate-аннотации для автоматической простановки временных меток
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

// LocalDateTime для хранения даты и времени без информации о часовом поясе
import java.time.LocalDateTime;

/**
 * Сущность заявки на добавление в друзья.
 *
 * <p>Представляет запрос от одного пользователя ({@code requester}) другому ({@code addressee})
 * на установление дружеских отношений. Аналог заявки в друзья в VK.</p>
 *
 * <p>Жизненный цикл заявки:</p>
 * <ol>
 *   <li>{@code PENDING} — заявка отправлена, ожидает ответа адресата</li>
 *   <li>{@code ACCEPTED} — адресат принял заявку, пользователи стали друзьями</li>
 *   <li>{@code DECLINED} — адресат отклонил заявку</li>
 * </ol>
 *
 * <p>Уникальное ограничение {@code (requester_id, addressee_id)} на уровне БД
 * предотвращает отправку дублирующих заявок от одного пользователя другому.</p>
 *
 * <p>Таблица БД: {@code friend_requests}</p>
 *
 * @see FriendRequestStatus
 * @see User
 */
@Entity
// Объявляет класс управляемой JPA-сущностью. Hibernate отслеживает изменения
// объектов этого типа и синхронизирует с БД при коммите транзакции.
@Table(
    name = "friend_requests",
    // Явное имя таблицы "friend_requests" в snake_case
    uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"})
    // Составное уникальное ограничение: пара (requester_id, addressee_id) уникальна.
    // Это означает: пользователь A может отправить заявку пользователю B только один раз.
    // Без этого ограничения возможны дубли: несколько записей с одинаковой парой отправитель-адресат.
    // @UniqueConstraint на @Table — это единственный способ задать многоколоночное ограничение UNIQUE в JPA.
)
@Getter
// Lombok: генерирует геттеры для всех полей
@Setter
// Lombok: генерирует сеттеры для всех полей
@Builder
// Lombok: паттерн Builder для создания заявок:
// FriendRequest.builder().requester(user1).addressee(user2).build()
@NoArgsConstructor
// Lombok: конструктор без аргументов — обязателен для JPA
@AllArgsConstructor
// Lombok: конструктор со всеми полями — необходим для @Builder
public class FriendRequest {

    /**
     * Уникальный идентификатор заявки, генерируется базой данных автоматически.
     */
    @Id
    // Первичный ключ таблицы "friend_requests"
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // IDENTITY: значение генерирует БД при INSERT (AUTO_INCREMENT / SERIAL).
    // Несмотря на составное уникальное ограничение, первичный ключ остаётся суррогатным (id),
    // что упрощает ссылки на эту сущность из других таблиц и API-ответов.
    private Long id;

    /**
     * Инициатор заявки — пользователь, отправивший запрос на дружбу.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    // @ManyToOne: один пользователь может отправить много заявок (N:1).
    // fetch = FetchType.LAZY: данные отправителя загружаются только при обращении к полю requester.
    // При обработке списка входящих заявок не нужно автоматически подгружать данные каждого отправителя.
    @JoinColumn(name = "requester_id", nullable = false)
    // requester_id: внешний ключ на таблицу "users".
    // nullable = false: заявка без инициатора невозможна (NOT NULL в БД).
    private User requester;

    /**
     * Адресат заявки — пользователь, которому отправлен запрос на дружбу.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    // @ManyToOne: один пользователь может получить много заявок от разных людей.
    // LAZY-загрузка предотвращает лишние запросы при получении списка заявок.
    @JoinColumn(name = "addressee_id", nullable = false)
    // addressee_id: внешний ключ на таблицу "users" (другой пользователь, не отправитель).
    // nullable = false: заявка без адресата не имеет смысла.
    private User addressee;

    /**
     * Текущий статус заявки на дружбу.
     * Отражает, принята, отклонена или ожидает рассмотрения.
     *
     * @see FriendRequestStatus
     */
    @Enumerated(EnumType.STRING)
    // EnumType.STRING: статус хранится как строка ("PENDING", "ACCEPTED", "DECLINED").
    // Это читаемо в самой БД и устойчиво к изменению порядка констант enum.
    @Column(nullable = false, length = 20)
    // nullable = false: статус обязателен — NOT NULL в БД. Без явного статуса
    // нельзя определить, является ли заявка активной или обработанной.
    // length = 20: достаточно для "DECLINED" (8 символов) и остальных значений.
    @Builder.Default
    // @Builder.Default: при создании через Builder обеспечивает значение по умолчанию.
    // Новая заявка всегда создаётся со статусом PENDING.
    private FriendRequestStatus status = FriendRequestStatus.PENDING; // По умолчанию заявка ожидает ответа

    /**
     * Дата и время отправки заявки на дружбу.
     * Устанавливается автоматически Hibernate при создании записи.
     * Неизменна после создания.
     */
    @CreationTimestamp
    // Hibernate автоматически простанавливает текущее время при INSERT —
    // это момент, когда пользователь нажал "добавить в друзья".
    @Column(name = "created_at", updatable = false)
    // updatable = false: дата отправки заявки — неизменный исторический факт.
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего изменения статуса заявки.
     * Обновляется автоматически Hibernate при принятии или отклонении заявки.
     * Позволяет отслеживать, когда именно была обработана заявка.
     */
    @UpdateTimestamp
    // Hibernate обновляет это поле текущим временем при каждом UPDATE.
    // Фиксирует момент, когда адресат принял или отклонил заявку (изменение статуса).
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
