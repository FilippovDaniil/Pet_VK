package com.socialnetwork.repository;

import com.socialnetwork.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с сущностью {@link Group} (сообщество).
 *
 * <p>Spring Data JPA автоматически генерирует реализацию этого интерфейса при старте приложения.
 * Наследование {@link JpaRepository} предоставляет стандартные CRUD-операции:
 * {@code save}, {@code findById}, {@code findAll}, {@code delete} и другие.
 *
 * <p>На данный момент кастомные запросы не нужны — поиск группы всегда идёт по id.
 * При необходимости здесь можно добавить {@code findByName}, {@code findByOwner} и т.д.
 */
public interface GroupRepository extends JpaRepository<Group, Long> {
}
