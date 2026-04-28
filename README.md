# Social Network Backend (VK-like)

Spring Boot 3.2 монолит — REST API социальной сети с авторизацией, постами, комментариями, личными сообщениями, группами и дружбой.

---

## Запуск через Docker (рекомендуется)

> Нужен только **Docker Desktop** — JDK устанавливать не нужно.

### Шаг 1 — Клонируй репозиторий

```bash
git clone <url-репозитория>
cd Pet_VK
```

### Шаг 2 — Запусти всё одной командой

```bash
docker-compose up --build
```

Эта команда:
1. Собирает JAR приложения внутри Docker (многоэтапная сборка)
2. Поднимает PostgreSQL, Redis, Zookeeper, Kafka
3. Ждёт, пока все сервисы станут здоровыми (`healthcheck`)
4. Запускает Spring Boot приложение

Первый запуск занимает **3–5 минут** (загрузка зависимостей + сборка).  
Последующие запуски — **~30 секунд** (слои Docker кэшируются).

### Шаг 3 — Открой в браузере

| Что | Ссылка |
|---|---|
| **Frontend (веб-интерфейс)** | Открой файл `frontend/index.html` в браузере |
| **Swagger UI (API документация)** | http://localhost:8080/swagger-ui.html |
| **API** | http://localhost:8080/api/... |

> **Frontend** — это статический HTML-файл. Просто перетащи `frontend/index.html` в браузер или открой через File → Open. Он автоматически обращается к `http://localhost:8080`.

### Шаг 4 — Зарегистрируйся и войди

1. Открой `frontend/index.html`
2. Перейди на вкладку **Register**
3. Заполни форму — нажми **Зарегистрироваться**
4. Автоматически войдёшь в систему

---

### Остановить проект

```bash
# Остановить (данные сохраняются)
docker-compose stop

# Остановить и удалить контейнеры (данные сохраняются в volumes)
docker-compose down

# Остановить и удалить ВСЁ включая данные БД
docker-compose down -v
```

### Посмотреть логи

```bash
# Все сервисы
docker-compose logs -f

# Только приложение
docker-compose logs -f app

# Только PostgreSQL
docker-compose logs -f postgres
```

---

## Запуск локально (для разработки)

Если хочешь запускать код из IDE без Docker-сборки:

### Шаг 1 — Подними только инфраструктуру

```bash
docker-compose up -d postgres redis zookeeper kafka
```

### Шаг 2 — Запусти приложение через Gradle

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

> На Windows: `gradlew.bat bootRun --args='--spring.profiles.active=dev'`

### Шаг 3 — Открой браузер

- Swagger UI: http://localhost:8080/swagger-ui.html
- Frontend: открой `frontend/index.html` в браузере

---

## Тестирование через Postman

1. Открой Postman
2. **File → Import** → выбери оба файла из папки `postman/`:
   - `SocialNetwork.postman_collection.json`
   - `SocialNetwork.postman_environment.json`
3. Выбери окружение **SocialNetwork** в правом верхнем углу
4. Начни с папки **Auth** → запрос **Register** или **Login**
5. Токены сохраняются автоматически — все остальные запросы сразу работают

---

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

---

## Структура проекта

```
Pet_VK/
├── src/
│   ├── main/java/com/socialnetwork/
│   │   ├── controller/     # REST контроллеры
│   │   ├── service/        # Бизнес-логика
│   │   ├── entity/         # JPA сущности
│   │   ├── repository/     # Spring Data репозитории
│   │   ├── dto/            # Request / Response объекты
│   │   ├── security/       # JWT фильтр, провайдер
│   │   ├── config/         # Spring конфигурации
│   │   ├── event/          # Kafka события
│   │   └── exception/      # Обработка ошибок
│   └── main/resources/
│       ├── application.yml
│       └── db/changelog/   # Liquibase миграции
├── frontend/
│   └── index.html          # Веб-интерфейс (открывать в браузере)
├── postman/
│   ├── SocialNetwork.postman_collection.json
│   └── SocialNetwork.postman_environment.json
├── Dockerfile
├── docker-compose.yml
└── build.gradle
```

---

## API эндпоинты

Все защищённые эндпоинты требуют заголовок:
```
Authorization: Bearer <accessToken>
```

### Аутентификация (публичные)

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/auth/register` | Регистрация |
| POST | `/api/auth/login` | Логин → access + refresh токены |
| POST | `/api/auth/refresh` | Обновить токен |
| POST | `/api/auth/logout` | Логаут |

### Пользователи

| Метод | URL | Описание |
|---|---|---|
| GET | `/api/users/me` | Мой профиль |
| PATCH | `/api/users/me` | Обновить профиль |
| POST | `/api/users/me/avatar` | Загрузить аватар |
| GET | `/api/users/search?query=...` | Поиск пользователей |

### Друзья

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/friends/requests/{userId}` | Отправить заявку |
| GET | `/api/friends/requests/incoming` | Входящие заявки |
| PUT | `/api/friends/requests/{id}?action=accept\|reject` | Принять / отклонить |
| DELETE | `/api/friends/{friendId}` | Удалить из друзей |
| GET | `/api/friends` | Список друзей |

### Посты и комментарии

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/posts/wall` | Создать пост |
| GET | `/api/posts/wall/{userId}` | Посты пользователя |
| PUT | `/api/posts/{postId}` | Редактировать пост |
| DELETE | `/api/posts/{postId}` | Удалить пост |
| POST | `/api/comments` | Добавить комментарий |
| GET | `/api/comments/{postId}` | Комментарии к посту |
| DELETE | `/api/comments/{commentId}` | Удалить комментарий |

### Сообщения

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/messages` | Отправить сообщение |
| GET | `/api/messages/{userId}` | Диалог с пользователем |

### Группы

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/groups` | Создать группу |
| GET | `/api/groups/{groupId}` | Информация о группе |
| POST | `/api/groups/{groupId}/join` | Вступить |
| POST | `/api/groups/{groupId}/leave` | Выйти |
| POST | `/api/groups/{groupId}/posts` | Пост в группу |
| GET | `/api/groups/{groupId}/posts` | Посты группы |

### Администратор (`ROLE_ADMIN`)

| Метод | URL | Описание |
|---|---|---|
| GET | `/api/admin/users` | Все пользователи |
| POST | `/api/admin/users/{id}/ban` | Забанить |
| POST | `/api/admin/users/{id}/unban` | Разбанить |
| DELETE | `/api/admin/posts/{postId}` | Удалить любой пост |
| DELETE | `/api/admin/comments/{commentId}` | Удалить любой комментарий |

---

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `JWT_SECRET` | `mySecretKey...` | Секрет JWT (мин. 32 символа) |
| `GOOGLE_CLIENT_ID` | `change-me` | Google OAuth2 Client ID |
| `GOOGLE_CLIENT_SECRET` | `change-me` | Google OAuth2 Client Secret |

---

## Тесты

```bash
./gradlew test
```
