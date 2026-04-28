# Social Network Backend (VK-like)

Spring Boot 3.2 monolith — REST API для социальной сети с авторизацией, постами, комментариями, личными сообщениями, группами и дружбой.

## Технологический стек

| Компонент | Технология |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3.2.5 |
| База данных | PostgreSQL 15 |
| Миграции | Liquibase |
| Кэш / токены | Redis 7 |
| Очередь | Apache Kafka |
| Безопасность | Spring Security + JWT (JJWT) |
| OAuth2 | Google Login |
| Документация | Swagger UI (SpringDoc) |
| Сборка | Gradle 8 |

## Быстрый старт

### 1. Предварительные требования
- JDK 21+
- Docker & Docker Compose

### 2. Запуск инфраструктуры

```bash
docker-compose up -d
```

Поднимается: PostgreSQL, Redis, Kafka, Zookeeper.

### 3. Запуск приложения

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 4. Swagger UI

```
http://localhost:8080/swagger-ui.html
```

## Основные эндпоинты

### Аутентификация (публичные)

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/auth/register` | Регистрация |
| POST | `/api/auth/login` | Логин → access + refresh токены |
| POST | `/api/auth/refresh` | Обновить токен |
| POST | `/api/auth/logout` | Логаут (blacklist токена) |

### Пользователи и друзья (JWT обязателен)

| Метод | URL |
|---|---|
| GET | `/api/users/me` |
| PATCH | `/api/users/me` |
| POST | `/api/users/me/avatar` |
| GET | `/api/users/search?query=...` |
| POST | `/api/friends/requests/{userId}` |
| GET | `/api/friends/requests/incoming` |
| PUT | `/api/friends/requests/{id}?action=accept\|reject` |
| DELETE | `/api/friends/{friendId}` |
| GET | `/api/friends` |

### Посты / Комментарии / Сообщения

| Метод | URL |
|---|---|
| POST | `/api/posts/wall` |
| GET | `/api/posts/wall/{userId}` |
| PUT | `/api/posts/{postId}` |
| DELETE | `/api/posts/{postId}` |
| POST | `/api/comments` |
| GET | `/api/comments/{postId}` |
| POST | `/api/messages` |
| GET | `/api/messages/{userId}` |

### Группы

| Метод | URL |
|---|---|
| POST | `/api/groups` |
| GET | `/api/groups/{groupId}` |
| POST | `/api/groups/{groupId}/join` |
| POST | `/api/groups/{groupId}/leave` |
| POST | `/api/groups/{groupId}/posts` |
| GET | `/api/groups/{groupId}/posts` |

### Админ (ROLE_ADMIN)

| Метод | URL |
|---|---|
| GET | `/api/admin/users` |
| POST | `/api/admin/users/{id}/ban` |
| POST | `/api/admin/users/{id}/unban` |
| DELETE | `/api/admin/posts/{postId}` |
| DELETE | `/api/admin/comments/{commentId}` |

## Конфигурация переменных окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `JWT_SECRET` | `mySecretKey...` | Секрет для подписи JWT (мин. 32 символа) |
| `GOOGLE_CLIENT_ID` | `change-me` | Google OAuth2 Client ID |
| `GOOGLE_CLIENT_SECRET` | `change-me` | Google OAuth2 Client Secret |

## Авторизация

Все защищённые эндпоинты требуют заголовок:
```
Authorization: Bearer <accessToken>
```

Access token — JWT, время жизни 15 минут.  
Refresh token — `userId:uuid`, TTL 30 дней, хранится в Redis.

## Тесты

```bash
./gradlew test
```
