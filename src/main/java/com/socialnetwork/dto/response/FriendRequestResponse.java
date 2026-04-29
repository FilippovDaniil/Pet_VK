package com.socialnetwork.dto.response;

import com.socialnetwork.entity.FriendRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO заявки в друзья для передачи клиенту.
 *
 * <p>Возвращается при отправке, получении и обработке заявок в друзья.
 * Содержит сводные данные об отправителе (id + полное имя) для отображения в UI.
 * Статус передаётся как строка (например, "PENDING", "ACCEPTED", "DECLINED"),
 * чтобы клиент не зависел от enum-типов бэкенда.
 */
@Data
@Builder
public class FriendRequestResponse {
    /** Уникальный id заявки. */
    private Long id;

    /** ID пользователя, отправившего заявку. */
    private Long requesterId;

    /** Полное имя отправителя — для отображения в списке входящих заявок. */
    private String requesterName;

    /** ID пользователя, которому адресована заявка. */
    private Long addresseeId;

    /**
     * Текстовое представление статуса заявки.
     * Значения: "PENDING" / "ACCEPTED" / "DECLINED" — из {@link com.socialnetwork.entity.FriendRequestStatus#name()}.
     */
    private String status;

    /** Дата и время создания заявки. */
    private LocalDateTime createdAt;

    /**
     * Преобразует сущность {@link FriendRequest} в DTO.
     * Обращается к lazy-полям {@code requester} и {@code addressee} —
     * вызывается только внутри транзакции.
     *
     * @param fr сущность заявки (с загруженными участниками)
     * @return DTO заявки
     */
    public static FriendRequestResponse from(FriendRequest fr) {
        return FriendRequestResponse.builder()
                .id(fr.getId())
                .requesterId(fr.getRequester().getId())
                .requesterName(fr.getRequester().getFirstName() + " " + fr.getRequester().getLastName())
                .addresseeId(fr.getAddressee().getId())
                .status(fr.getStatus().name())
                .createdAt(fr.getCreatedAt())
                .build();
    }
}
