# Pet_VK — Бэкенд социальной сети

Учебный pet-проект: полноценный REST API бэкенд социальной сети в стиле ВКонтакте, реализованный на **Java 21 + Spring Boot 3.2.5**. Проект охватывает ключевые темы современной бэкенд-разработки: аутентификация через JWT и OAuth2 Google, кэширование в Redis, асинхронные события через Apache Kafka, управление схемой БД через Liquibase и документирование API через Swagger UI.

Вместе с бэкендом поставляется небольшой **ванильный JS SPA** (`frontend/index.html`), который позволяет визуально проверить работу API прямо в браузере без Postman.

---

## Содержание

1. [Стек технологий](#1-стек-технологий)
2. [Архитектура](#2-архитектура)
3. [Структура проекта](#3-структура-проекта)
4. [Установка и запуск](#4-установка-и-запуск)
5. [Конфигурация](#5-конфигурация)
6. [Безопасность](#6-безопасность)
7. [API Reference](#7-api-reference)
8. [Схема базы данных](#8-схема-базы-данных)
9. [Apache Kafka — события дружбы](#9-apache-kafka--события-дружбы)
10. [Кэширование Redis](#10-кэширование-redis)
11. [Поиск OpenSearch](#11-поиск-opensearch)
12. [Фронтенд SPA](#12-фронтенд-spa)
13. [Swagger UI](#13-swagger-ui)
14. [Тестирование](#14-тестирование)
15. [Postman-коллекция](#15-postman-коллекция)
16. [Возможные улучшения](#16-возможные-улучшения)

---

## 1. Стек технологий

| Категория | Инструмент / Версия |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3.2.5 |
| Безопасность | Spring Security 6, JJWT 0.12.5, Spring OAuth2 Client |
| Персистентность | Spring Data JPA, Hibernate 6, PostgreSQL 15 |
| Кэш / хранилище токенов | Spring Data Redis 7 (Lettuce) |
| Очередь сообщений | Apache Kafka 7.6.1 (Confluent), Zookeeper 7.6.1 |
| Поиск | OpenSearch 2.15.0 (`opensearch-java:2.15.0`, `httpclient5`) |
| Миграции БД | Liquibase |
| Документация API | SpringDoc OpenAPI 2.3.0 (Swagger UI) |
| Утилиты | Lombok |
| Сборка | Gradle (Groovy DSL) |
| Контейнеризация | Docker, Docker Compose 3.8, Kubernetes (k3s / Rancher Desktop) |
| Тесты | JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers 1.19.7, H2 |
| JDK образ | eclipse-temurin:21-jdk-alpine |

---

## 2. Архитектура

```
┌─────────────────────────────────────────────────────────────────┐
│                          Клиент                                 │
│   Browser SPA (index.html)  │  Postman / curl  │  Swagger UI   │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTP (порт 8777 в Docker / 8080 локально)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                      │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  Controllers │→ │   Services   │→ │     Repositories     │  │
│  └──────────────┘  └──────┬───────┘  └──────────┬───────────┘  │
│                           │                     │              │
│  ┌────────────────────────┼─────────────────────┼───────────┐  │
│  │       Security Layer   │                     │           │  │
│  │  JWT Filter │ OAuth2   │                     │           │  │
│  └────────────────────────┼─────────────────────┼───────────┘  │
│                           │                     │              │
│        ┌──────────────────┼─────────┐           │              │
│        ▼                  ▼         ▼           ▼              │
│   ┌─────────┐       ┌─────────┐  ┌──────────┐  ┌───────────┐  │
│   │  Redis  │       │  Kafka  │  │PostgreSQL│  │OpenSearch │  │
│   │ :6379   │       │  :9092  │  │:5433     │  │  :9200    │  │
│   │─────────│       │─────────│  │social_   │  │───────────│  │
│   │blacklist│       │ friend- │  │network DB│  │users index│  │
│   │ JWT     │       │ events  │  └──────────┘  │full-text  │  │
│   │ refresh │       │  topic  │                │  search   │  │
│   │ tokens  │       └─────────┘                └───────────┘  │
│   │ @Cache  │                                                   │
│   └─────────┘                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Схема портов (Docker Compose)

| Сервис | Внутренний порт | Внешний порт (хост) |
|---|---|---|
| Spring Boot App | 8080 | **8777** |
| PostgreSQL | 5432 | **5433** |
| Redis | 6379 | **6379** |
| Apache Kafka | 9092 | **9092** |
| Zookeeper | 2181 | **2181** |
| OpenSearch | 9200 | **9200** |

---

## 3. Структура проекта

```
Pet_VK/
├── src/
│   ├── main/
│   │   ├── java/com/socialnetwork/
│   │   │   ├── SocialNetworkApplication.java      # точка входа
│   │   │   │
│   │   │   ├── config/                            # конфигурация бинов
│   │   │   │   ├── SecurityConfig.java            # Spring Security + JWT + OAuth2
│   │   │   │   ├── RedisConfig.java               # RedisTemplate, CacheManager
│   │   │   │   ├── KafkaConfig.java               # продюсер / консьюмер
│   │   │   │   ├── OpenSearchConfig.java          # OpenSearchClient (httpclient5)
│   │   │   │   ├── WebMvcConfig.java              # CORS, статика
│   │   │   │   └── OpenApiConfig.java             # Swagger / OpenAPI метаданные
│   │   │   │
│   │   │   ├── controller/                        # REST-контроллеры
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── UserController.java
│   │   │   │   ├── PostController.java
│   │   │   │   ├── FriendController.java
│   │   │   │   ├── MessageController.java
│   │   │   │   ├── CommentController.java
│   │   │   │   ├── GroupController.java
│   │   │   │   └── AdminController.java
│   │   │   │
│   │   │   ├── service/                           # бизнес-логика
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── PostService.java
│   │   │   │   ├── FriendService.java
│   │   │   │   ├── MessageService.java
│   │   │   │   ├── CommentService.java
│   │   │   │   ├── GroupService.java
│   │   │   │   ├── AdminService.java
│   │   │   │   ├── BlacklistService.java          # JWT → Redis blacklist
│   │   │   │   └── RefreshTokenService.java       # refresh tokens в Redis
│   │   │   │
│   │   │   ├── security/                          # JWT + OAuth2
│   │   │   │   ├── JwtTokenProvider.java          # генерация / валидация JWT
│   │   │   │   ├── JwtAuthenticationFilter.java   # OncePerRequestFilter
│   │   │   │   ├── CustomUserDetailsService.java  # loadUserByUsername
│   │   │   │   └── OAuth2SuccessHandler.java      # выдача JWT после Google OAuth2
│   │   │   │
│   │   │   ├── entity/                            # JPA-сущности
│   │   │   │   ├── User.java
│   │   │   │   ├── Post.java
│   │   │   │   ├── Comment.java
│   │   │   │   ├── Message.java
│   │   │   │   ├── Group.java
│   │   │   │   ├── GroupMember.java
│   │   │   │   ├── GroupMemberId.java             # составной PK
│   │   │   │   ├── FriendRequest.java
│   │   │   │   ├── Role.java                      # enum: ROLE_USER, ROLE_ADMIN
│   │   │   │   ├── FriendRequestStatus.java       # enum: PENDING, ACCEPTED, DECLINED
│   │   │   │   └── GroupMemberRole.java           # enum: MEMBER, ADMIN, OWNER
│   │   │   │
│   │   │   ├── repository/                        # Spring Data JPA репозитории
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── PostRepository.java
│   │   │   │   ├── CommentRepository.java
│   │   │   │   ├── MessageRepository.java
│   │   │   │   ├── GroupRepository.java
│   │   │   │   ├── GroupMemberRepository.java
│   │   │   │   └── FriendRequestRepository.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── request/                       # входящие DTO
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RefreshTokenRequest.java
│   │   │   │   │   ├── UpdateProfileRequest.java
│   │   │   │   │   ├── PostCreateRequest.java
│   │   │   │   │   ├── CommentCreateRequest.java
│   │   │   │   │   ├── MessageRequest.java
│   │   │   │   │   └── GroupCreateRequest.java
│   │   │   │   └── response/                      # исходящие DTO
│   │   │   │       ├── AuthResponse.java
│   │   │   │       ├── UserResponse.java
│   │   │   │       ├── PostResponse.java
│   │   │   │       ├── CommentResponse.java
│   │   │   │       ├── MessageResponse.java
│   │   │   │       ├── GroupResponse.java
│   │   │   │       ├── FriendRequestResponse.java
│   │   │   │       └── ErrorResponse.java
│   │   │   │
│   │   │   ├── event/                             # Kafka события
│   │   │   │   ├── FriendEvent.java               # DTO события
│   │   │   │   ├── FriendEventPublisher.java      # KafkaTemplate producer
│   │   │   │   └── FriendEventListener.java       # @KafkaListener consumer
│   │   │   │
│   │   │   ├── search/                            # OpenSearch интеграция
│   │   │   │   ├── UserDocument.java              # DTO документа в индексе
│   │   │   │   └── UserSearchService.java         # индексация и full-text поиск
│   │   │   │
│   │   │   └── exception/                         # обработка ошибок
│   │   │       ├── GlobalExceptionHandler.java    # @RestControllerAdvice
│   │   │       ├── ResourceNotFoundException.java # 404
│   │   │       ├── BadRequestException.java       # 400
│   │   │       └── ForbiddenException.java        # 403
│   │   │
│   │   └── resources/
│   │       ├── application.yml                    # конфигурация (dev / test профили)
│   │       ├── db/changelog/                      # Liquibase миграции
│   │       └── static/                            # фронтенд SPA (index.html + JS)
│   │
│   └── test/java/com/socialnetwork/
│       ├── controller/                            # интеграционные тесты контроллеров
│       │   ├── AuthControllerTest.java
│       │   ├── PostControllerTest.java
│       │   └── CommentControllerTest.java
│       ├── service/                               # unit-тесты сервисов
│       │   ├── AuthServiceTest.java
│       │   ├── PostServiceTest.java
│       │   ├── FriendServiceTest.java
│       │   ├── GroupServiceTest.java
│       │   ├── CommentServiceTest.java
│       │   └── MessageServiceTest.java
│       └── security/
│           └── JwtTokenProviderTest.java
│
├── frontend/
│   └── index.html                                 # ванильный JS SPA
├── postman/
│   ├── SocialNetwork.postman_collection.json
│   └── SocialNetwork.postman_environment.json
├── rancher/
│   ├── build-and-load.ps1                         # сборка образа + загрузка в k3s VM
│   └── k8s/                                       # Kubernetes-манифесты
│       ├── 00-namespace.yaml                      # namespace: pet-vk
│       ├── 01-secrets.yaml                        # DB / JWT секреты
│       ├── 02-postgres.yaml                       # PostgreSQL StatefulSet
│       ├── 03-redis.yaml                          # Redis Deployment
│       ├── 04-kafka.yaml                          # Kafka + Zookeeper
│       ├── 05-opensearch.yaml                     # OpenSearch Deployment
│       └── 06-app.yaml                            # приложение + ConfigMap + NodePort
├── Dockerfile
├── docker-compose.yml
├── build.gradle
├── settings.gradle
└── gradle/wrapper/gradle-wrapper.properties
```

---

## 4. Установка и запуск

### Предварительные требования

- Git
- Docker Desktop (рекомендуется) **или** JDK 21 + PostgreSQL 15 + Redis 7 + Apache Kafka

---

### Способ 1: Docker Compose (рекомендуется)

Самый простой способ запустить весь стек одной командой.

```bash
# 1. Клонировать репозиторий
git clone https://github.com/your-username/Pet_VK.git
cd Pet_VK

# 2. Собрать Docker-образ приложения
docker build -t pet_vk-app:latest .

# 3. Запустить все сервисы
docker compose up -d

# 4. Проверить статус контейнеров
docker compose ps
```

После успешного старта:

| URL | Описание |
|---|---|
| `http://localhost:8777` | REST API |
| `http://localhost:8777/swagger-ui.html` | Swagger UI |
| `http://localhost:8777/index.html` | Фронтенд SPA |

Остановить стек:
```bash
docker compose down
# или с удалением volumes (сброс БД):
docker compose down -v
```

Просмотр логов приложения:
```bash
docker compose logs -f app
```

---

### Способ 2: Kubernetes / Rancher Desktop

Полный production-like деплой на локальный k3s-кластер. Требуется **Rancher Desktop** с включённым Kubernetes.

```powershell
# 1. Клонировать репозиторий
git clone https://github.com/your-username/Pet_VK.git
cd Pet_VK

# 2. Собрать Docker-образ и загрузить его в k3s VM
.\rancher\build-and-load.ps1

# 3. Задеплоить весь стек (первый раз — все манифесты)
kubectl apply -f rancher/k8s/

# 4. Проверить состояние подов
kubectl get pods -n pet-vk
```

После успешного старта всех подов (`Running`):

| URL | Описание |
|---|---|
| `http://localhost:30777` | REST API (NodePort) |
| `http://localhost:30777/swagger-ui.html` | Swagger UI |
| `http://localhost:30777/index.html` | Фронтенд SPA |

Обновить приложение после изменений в коде:
```powershell
.\rancher\build-and-load.ps1 -Restart
```

Полезные команды для диагностики:
```powershell
# Статус всего стека
kubectl get all -n pet-vk

# Логи приложения в реальном времени
kubectl logs -n pet-vk deployment/pet-vk-app -f

# Подробности о поде (события, ошибки)
kubectl describe pod -n pet-vk <pod-name>
```

> **Примечание**: Манифесты используют `imagePullPolicy: Never` для образа приложения — он загружается напрямую в k3s VM через `rdctl shell docker load`. Все зависимости (PostgreSQL, Redis, Kafka, OpenSearch) поднимаются как отдельные поды с initContainer-ами для ожидания готовности.

---

### Способ 3: Локальный запуск без Docker

Требуется: JDK 21, PostgreSQL 15, Redis 7, Kafka (или только PostgreSQL + Redis — Kafka не обязателен для базового запуска, события не публикуются).

**Шаг 1.** Создать базу данных PostgreSQL:
```sql
CREATE DATABASE social_network;
CREATE USER postgres WITH PASSWORD '1234';
GRANT ALL PRIVILEGES ON DATABASE social_network TO postgres;
```

**Шаг 2.** Убедиться, что Redis запущен:
```bash
redis-cli ping   # должен вернуть PONG
```

**Шаг 3.** Установить переменные окружения (опционально):
```bash
export JWT_SECRET="mySecretKeyWhichShouldBeAtLeast32CharsLong!!"
export GOOGLE_CLIENT_ID="ваш-client-id"
export GOOGLE_CLIENT_SECRET="ваш-client-secret"
```

**Шаг 4.** Сборка и запуск:
```bash
# Unix / macOS
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

Приложение запустится на `http://localhost:8080`.

Liquibase автоматически применит все миграции при первом запуске. Профиль по умолчанию — `dev`.

---

## 5. Конфигурация

### Переменные окружения

| Переменная | Описание | Значение по умолчанию |
|---|---|---|
| `JWT_SECRET` | Секрет для подписи JWT (минимум 32 символа) | `mySecretKeyWhichShouldBeAtLeast32CharsLong!!` |
| `GOOGLE_CLIENT_ID` | Client ID Google OAuth2 приложения | `change-me` |
| `GOOGLE_CLIENT_SECRET` | Client Secret Google OAuth2 приложения | `change-me` |
| `SPRING_DATASOURCE_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/social_network` |
| `SPRING_DATASOURCE_USERNAME` | Пользователь PostgreSQL | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Пароль PostgreSQL | `1234` |
| `SPRING_DATA_REDIS_HOST` | Хост Redis | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Порт Redis | `6379` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Bootstrap-серверы Kafka | `localhost:9092` |
| `OPENSEARCH_HOST` | Хост OpenSearch | `localhost` |
| `OPENSEARCH_PORT` | Порт OpenSearch | `9200` |
| `OPENSEARCH_SCHEME` | Схема подключения | `http` |

> **Важно**: в production обязательно замените `JWT_SECRET` на случайно сгенерированный секрет длиной не менее 32 символов. Никогда не коммитьте настоящие секреты в репозиторий.

---

### application.yml — ключевые параметры

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:mySecretKeyWhichShouldBeAtLeast32CharsLong!!}
    access-token-expiration: 900000       # 15 минут (мс)
    refresh-token-expiration: 2592000000  # 30 дней (мс)
  upload:
    path: ./uploads/avatars              # директория для хранения аватаров

spring:
  jpa:
    hibernate:
      ddl-auto: validate                 # схема управляется Liquibase
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  cache:
    type: redis
    redis:
      time-to-live: 600000              # TTL кэша — 10 минут (мс)

server:
  port: 8080
```

### Профили Spring

| Профиль | Активируется | БД | Redis | Kafka | Кэш |
|---|---|---|---|---|---|
| `dev` | по умолчанию | PostgreSQL | localhost:6379 | localhost:9092 | Redis |
| `test` | при запуске тестов | H2 in-memory | localhost:6370 | localhost:9093 | Simple (in-memory) |

---

## 6. Безопасность

### JWT-аутентификация

Аутентификация реализована через два токена:

```
Access Token  — короткоживущий JWT (15 мин), передаётся в заголовке Authorization
Refresh Token — долгоживущий токен (30 дней), хранится в Redis
```

**Полный flow аутентификации:**

```
1. Клиент → POST /api/auth/register  (email, password, имя)
             POST /api/auth/login     (email, password)
   ↓
2. Сервер генерирует:
   - Access JWT  → подписан секретом, TTL 15 мин
   - Refresh token (UUID) → сохраняется в Redis:
     ключ: "refresh:{userId}:{tokenId}", TTL 30 дней
   ↓
3. Ответ клиенту: { "accessToken": "...", "refreshToken": "..." }
   ↓
4. Клиент сохраняет accessToken в localStorage
   Каждый запрос к защищённым эндпоинтам:
   Authorization: Bearer <accessToken>
   ↓
5. JwtAuthenticationFilter проверяет:
   - Валидность подписи JWT
   - Срок действия
   - Наличие токена в Redis blacklist (ключ: "blacklist:jwt:{token}")
   ↓
6. Обновление: POST /api/auth/refresh  { "refreshToken": "..." }
   Сервер:
   - Ищет refresh token в Redis
   - Инвалидирует старый
   - Создаёт новую пару токенов
   ↓
7. Выход: POST /api/auth/logout  (Authorization: Bearer <accessToken>)
   Сервер:
   - Помещает access token в Redis blacklist (TTL = оставшееся время жизни токена)
   - Удаляет refresh token из Redis
```

### OAuth2 — вход через Google

```
1. Клиент открывает: GET /oauth2/authorization/google
2. Редирект на страницу входа Google
3. После успешного входа: GET /login/oauth2/code/google
4. OAuth2SuccessHandler генерирует JWT-токены
5. Клиент получает ответ: { "accessToken": "...", "refreshToken": "..." }
```

Для активации OAuth2 необходимо настроить переменные `GOOGLE_CLIENT_ID` и `GOOGLE_CLIENT_SECRET`.

### Роли

| Роль | Описание |
|---|---|
| `ROLE_USER` | Обычный пользователь. Доступ ко всем пользовательским эндпоинтам. |
| `ROLE_ADMIN` | Расширенные права. Доступ к `/api/admin/**`: просмотр всех пользователей, бан/анбан, удаление любого контента. |

### Публичные эндпоинты (без токена)

```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
GET  /swagger-ui.html
GET  /v3/api-docs
GET  /oauth2/**
GET  /login/oauth2/**
```

---

## 7. API Reference

Все защищённые эндпоинты требуют заголовка:
```
Authorization: Bearer <accessToken>
```

Формат ошибок:
```json
{
  "timestamp": "2026-04-28T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Описание ошибки"
}
```

---

### Аутентификация (`/api/auth`)

#### Регистрация

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "firstName": "Иван",
    "lastName": "Иванов"
  }'
```

Ответ `200 OK`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

#### Вход

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

---

#### Обновление access token

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

---

#### Выход

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <accessToken>"
```

---

### Пользователи (`/api/users`)

#### Профиль текущего пользователя

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"
```

Ответ:
```json
{
  "id": 1,
  "email": "user@example.com",
  "firstName": "Иван",
  "lastName": "Иванов",
  "avatarUrl": null,
  "bio": null,
  "role": "ROLE_USER",
  "createdAt": "2026-04-28T10:00:00"
}
```

---

#### Обновить профиль

```bash
curl -X PATCH http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Иван",
    "lastName": "Петров",
    "bio": "Люблю программировать"
  }'
```

---

#### Загрузить аватар (multipart)

```bash
curl -X POST http://localhost:8080/api/users/me/avatar \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@/path/to/avatar.jpg"
```

---

#### Поиск пользователей

```bash
curl "http://localhost:8080/api/users/search?query=Иван" \
  -H "Authorization: Bearer <accessToken>"
```

---

### Посты (`/api/posts`)

#### Создать пост на стене

```bash
curl -X POST http://localhost:8080/api/posts/wall \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Привет всем! Мой первый пост."
  }'
```

---

#### Посты стены пользователя (с пагинацией)

```bash
curl "http://localhost:8080/api/posts/wall/1?page=0&size=10" \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Редактировать пост

```bash
curl -X PUT http://localhost:8080/api/posts/42 \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Отредактированный текст поста"
  }'
```

---

#### Удалить пост

```bash
curl -X DELETE http://localhost:8080/api/posts/42 \
  -H "Authorization: Bearer <accessToken>"
```

---

### Друзья (`/api/friends`)

#### Список друзей

```bash
curl http://localhost:8080/api/friends \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Отправить запрос в друзья

```bash
curl -X POST http://localhost:8080/api/friends/requests/5 \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Входящие запросы в друзья

```bash
curl http://localhost:8080/api/friends/requests/incoming \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Принять / отклонить запрос

```bash
# Принять
curl -X PUT "http://localhost:8080/api/friends/requests/10?action=accept" \
  -H "Authorization: Bearer <accessToken>"

# Отклонить
curl -X PUT "http://localhost:8080/api/friends/requests/10?action=reject" \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Удалить из друзей

```bash
curl -X DELETE http://localhost:8080/api/friends/5 \
  -H "Authorization: Bearer <accessToken>"
```

---

### Сообщения (`/api/messages`)

#### Отправить сообщение

```bash
curl -X POST http://localhost:8080/api/messages \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "recipientId": 5,
    "text": "Привет, как дела?"
  }'
```

---

#### Получить диалог с пользователем (с пагинацией)

```bash
curl "http://localhost:8080/api/messages/5?page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"
```

---

### Комментарии

Комментарии — вложенный ресурс поста (`/api/posts/{postId}/comments`). Удаление — через отдельный эндпоинт (`/api/comments/{commentId}`).

#### Добавить комментарий к посту

```bash
curl -X POST http://localhost:8080/api/posts/42/comments \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Отличный пост!"
  }'
```

---

#### Комментарии к посту (с пагинацией)

```bash
curl "http://localhost:8080/api/posts/42/comments?page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Удалить комментарий

```bash
curl -X DELETE http://localhost:8080/api/comments/15 \
  -H "Authorization: Bearer <accessToken>"
```

---

### Группы (`/api/groups`)

#### Создать группу

```bash
curl -X POST http://localhost:8080/api/groups \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Java Разработчики",
    "description": "Сообщество Java-разработчиков"
  }'
```

---

#### Информация о группе

```bash
curl http://localhost:8080/api/groups/3 \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Вступить / покинуть группу

Членство — отдельный sub-ресурс (`/api/groups/{groupId}/members`): POST вступает, DELETE покидает.

```bash
# Вступить
curl -X POST http://localhost:8080/api/groups/3/members \
  -H "Authorization: Bearer <accessToken>"

# Покинуть
curl -X DELETE http://localhost:8080/api/groups/3/members \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Мои группы

```bash
curl http://localhost:8080/api/groups/my \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Назначить администратора группы

```bash
curl -X POST http://localhost:8080/api/groups/3/admins/7 \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Удалить группу

```bash
curl -X DELETE http://localhost:8080/api/groups/3 \
  -H "Authorization: Bearer <accessToken>"
```

---

#### Создать пост в группе

```bash
curl -X POST http://localhost:8080/api/groups/3/posts \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Новость сообщества Java Разработчики"
  }'
```

---

#### Посты группы (с пагинацией)

```bash
curl "http://localhost:8080/api/groups/3/posts?page=0&size=10" \
  -H "Authorization: Bearer <accessToken>"
```

---

### Административные эндпоинты (`/api/admin`) — только `ROLE_ADMIN`

#### Список всех пользователей

```bash
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <adminAccessToken>"
```

---

#### Заблокировать / разблокировать пользователя

```bash
# Заблокировать
curl -X POST http://localhost:8080/api/admin/users/5/ban \
  -H "Authorization: Bearer <adminAccessToken>"

# Разблокировать
curl -X POST http://localhost:8080/api/admin/users/5/unban \
  -H "Authorization: Bearer <adminAccessToken>"
```

---

#### Удалить любой пост / комментарий

```bash
curl -X DELETE http://localhost:8080/api/admin/posts/99 \
  -H "Authorization: Bearer <adminAccessToken>"

curl -X DELETE http://localhost:8080/api/admin/comments/55 \
  -H "Authorization: Bearer <adminAccessToken>"
```

---

### Сводная таблица всех эндпоинтов

| Метод | URL | Описание | Доступ |
|---|---|---|---|
| POST | `/api/auth/register` | Регистрация | Public |
| POST | `/api/auth/login` | Вход | Public |
| POST | `/api/auth/refresh` | Обновление токена | Public |
| POST | `/api/auth/logout` | Выход | User |
| GET | `/api/users/me` | Мой профиль | User |
| PATCH | `/api/users/me` | Обновить профиль | User |
| POST | `/api/users/me/avatar` | Загрузить аватар | User |
| GET | `/api/users/search` | Поиск пользователей | User |
| POST | `/api/posts/wall` | Создать пост | User |
| GET | `/api/posts/wall/{userId}` | Посты стены | User |
| PUT | `/api/posts/{postId}` | Редактировать пост | User |
| DELETE | `/api/posts/{postId}` | Удалить свой пост | User |
| GET | `/api/friends` | Список друзей | User |
| POST | `/api/friends/requests/{userId}` | Запрос в друзья | User |
| GET | `/api/friends/requests/incoming` | Входящие запросы | User |
| PUT | `/api/friends/requests/{requestId}` | Принять/отклонить | User |
| DELETE | `/api/friends/{friendId}` | Удалить из друзей | User |
| POST | `/api/messages` | Отправить сообщение | User |
| GET | `/api/messages/{userId}` | Диалог | User |
| POST | `/api/posts/{postId}/comments` | Добавить комментарий | User |
| GET | `/api/posts/{postId}/comments` | Комментарии поста | User |
| DELETE | `/api/comments/{commentId}` | Удалить комментарий | User |
| POST | `/api/groups` | Создать группу | User |
| GET | `/api/groups/{groupId}` | Информация о группе | User |
| GET | `/api/groups/my` | Мои группы | User |
| POST | `/api/groups/{groupId}/members` | Вступить в группу | User |
| DELETE | `/api/groups/{groupId}/members` | Покинуть группу | User |
| POST | `/api/groups/{groupId}/admins/{userId}` | Назначить админа | Group Owner |
| DELETE | `/api/groups/{groupId}` | Удалить группу | Group Owner |
| POST | `/api/groups/{groupId}/posts` | Пост в группу | User |
| GET | `/api/groups/{groupId}/posts` | Посты группы | User |
| GET | `/api/admin/users` | Все пользователи | Admin |
| POST | `/api/admin/users/{id}/ban` | Заблокировать | Admin |
| POST | `/api/admin/users/{id}/unban` | Разблокировать | Admin |
| DELETE | `/api/admin/posts/{id}` | Удалить пост | Admin |
| DELETE | `/api/admin/comments/{id}` | Удалить комментарий | Admin |

---

## 8. Схема базы данных

Схема управляется через **Liquibase**. Изменения хранятся в `src/main/resources/db/changelog/`.

### Таблица `users`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | Идентификатор |
| `email` | VARCHAR UNIQUE NOT NULL | Email (используется как логин) |
| `password` | VARCHAR | Хеш пароля (BCrypt). NULL для OAuth2-пользователей |
| `first_name` | VARCHAR NOT NULL | Имя |
| `last_name` | VARCHAR NOT NULL | Фамилия |
| `avatar_url` | VARCHAR | Путь к файлу аватара |
| `bio` | TEXT | Информация о себе |
| `role` | VARCHAR | `ROLE_USER` / `ROLE_ADMIN` |
| `is_banned` | BOOLEAN | Флаг блокировки |
| `created_at` | TIMESTAMP | Дата регистрации |
| `updated_at` | TIMESTAMP | Дата обновления профиля |

---

### Таблица `posts`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | Идентификатор |
| `author_id` | BIGINT FK → users | Автор поста |
| `group_id` | BIGINT FK → groups | Группа (NULL для постов на стене) |
| `text` | TEXT NOT NULL | Текст поста |
| `image_url` | VARCHAR | Ссылка на изображение |
| `created_at` | TIMESTAMP | Дата создания |
| `updated_at` | TIMESTAMP | Дата последнего редактирования |

---

### Таблица `comments`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | Идентификатор |
| `author_id` | BIGINT FK → users | Автор комментария |
| `post_id` | BIGINT FK → posts | Комментируемый пост |
| `text` | TEXT NOT NULL | Текст комментария |
| `created_at` | TIMESTAMP | Дата создания |
| `updated_at` | TIMESTAMP | Дата редактирования |

---

### Таблица `messages`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | Идентификатор |
| `sender_id` | BIGINT FK → users | Отправитель |
| `recipient_id` | BIGINT FK → users | Получатель |
| `text` | TEXT NOT NULL | Текст сообщения |
| `is_read` | BOOLEAN | Прочитано ли сообщение |
| `created_at` | TIMESTAMP | Дата отправки |

---

### Таблица `groups`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | Идентификатор |
| `name` | VARCHAR NOT NULL | Название группы |
| `description` | TEXT | Описание группы |
| `avatar_url` | VARCHAR | Аватар группы |
| `owner_id` | BIGINT FK → users | Владелец группы |
| `created_at` | TIMESTAMP | Дата создания |

---

### Таблица `group_members`

| Колонка | Тип | Описание |
|---|---|---|
| `group_id` | BIGINT FK → groups | Часть составного PK |
| `user_id` | BIGINT FK → users | Часть составного PK |
| `role` | VARCHAR | `MEMBER` / `ADMIN` / `OWNER` |
| `joined_at` | TIMESTAMP | Дата вступления |

Составной первичный ключ: `(group_id, user_id)`.

---

### Таблица `friend_requests`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | Идентификатор |
| `requester_id` | BIGINT FK → users | Отправитель запроса |
| `addressee_id` | BIGINT FK → users | Получатель запроса |
| `status` | VARCHAR | `PENDING` / `ACCEPTED` / `DECLINED` |
| `created_at` | TIMESTAMP | Дата отправки запроса |
| `updated_at` | TIMESTAMP | Дата изменения статуса |

---

### ER-диаграмма (упрощённая)

```
users ──< posts ──< comments
  │
  │──< messages (sender / recipient)
  │
  │──< friend_requests (requester / addressee)
  │
  └──< group_members >── groups ──< posts
```

---

## 9. Apache Kafka — события дружбы

### Топик

| Топик | Партиции | Репликация |
|---|---|---|
| `friend-events` | 1 | 1 |

Топик создаётся автоматически при первом обращении (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'`).

### Структура события `FriendEvent`

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-04-28T12:00:00",
  "type": "FRIEND_REQUEST_SENT",
  "sourceUserId": 1,
  "targetUserId": 5
}
```

### Типы событий

| Тип (`type`) | Когда публикуется |
|---|---|
| `FRIEND_REQUEST_SENT` | Пользователь отправил запрос в друзья |
| `FRIEND_REQUEST_ACCEPTED` | Запрос в друзья принят |
| `FRIEND_REQUEST_DECLINED` | Запрос в друзья отклонён |
| `FRIEND_REMOVED` | Пользователь удалён из друзей |

### Архитектура событий

```
FriendService
    │
    ▼
FriendEventPublisher   →   Kafka topic: "friend-events"
                                │
                                ▼
                       FriendEventListener
                       (логирует событие / в будущем — push-уведомления)
```

`FriendEventPublisher` использует `KafkaTemplate<String, FriendEvent>` для отправки событий.
`FriendEventListener` подписан через `@KafkaListener(topics = "friend-events", groupId = "social-network-group")`.

В текущей реализации консьюмер логирует события. Архитектурно это место для подключения push-уведомлений, email-рассылок или WebSocket трансляций.

---

## 10. Кэширование Redis

Redis используется в трёх ролях:

### 10.1 Кэш данных (`@Cacheable`)

Spring Cache с бэкендом Redis (TTL 10 минут). Используется для кэширования результатов часто запрашиваемых данных (профили пользователей, посты, группы).

Конфигурация в `RedisConfig.java`:
- Сериализатор ключей: `StringRedisSerializer`
- Сериализатор значений: `GenericJackson2JsonRedisSerializer`
- TTL по умолчанию: 10 минут

### 10.2 Blacklist JWT (`BlacklistService`)

При выходе (`POST /api/auth/logout`) access token помещается в Redis:

```
Ключ:   blacklist:jwt:<token>
Значение: "true"
TTL:    оставшееся время жизни JWT
```

`JwtAuthenticationFilter` при каждом запросе проверяет, не находится ли токен в blacklist. Это позволяет инвалидировать JWT до истечения его естественного срока.

### 10.3 Refresh Tokens (`RefreshTokenService`)

Refresh token хранится в Redis:

```
Ключ:   refresh:{userId}:{tokenId}
Значение: JSON с данными токена
TTL:    30 дней
```

При обновлении токена (`POST /api/auth/refresh`):
1. Сервер находит запись в Redis по значению refresh token
2. Удаляет старую запись
3. Создаёт новую пару токенов

### Redis-ключи — сводная таблица

| Префикс ключа | Содержимое | TTL |
|---|---|---|
| `blacklist:jwt:<token>` | `"true"` | Оставшееся время жизни JWT |
| `refresh:{userId}:{tokenId}` | Данные refresh token | 30 дней |
| `<cacheName>::<key>` | Закэшированные данные | 10 минут |

---

## 11. Поиск OpenSearch

OpenSearch используется для полнотекстового поиска пользователей по имени, фамилии и email.

### Конфигурация

Клиент создаётся в `OpenSearchConfig.java` через `ApacheHttpClient5TransportBuilder` (транспорт httpclient5, не httpclient4):

```yaml
opensearch:
  host: ${OPENSEARCH_HOST:localhost}
  port: ${OPENSEARCH_PORT:9200}
  scheme: ${OPENSEARCH_SCHEME:http}
```

### Индекс `users`

Документ индекса (`UserDocument`):

| Поле | Тип | Описание |
|---|---|---|
| `id` | String | ID пользователя (совпадает с `_id` в OpenSearch) |
| `firstName` | String | Имя |
| `lastName` | String | Фамилия |
| `email` | String | Email |
| `avatarUrl` | String | Ссылка на аватар |
| `banned` | boolean | Флаг блокировки |

Индекс создаётся автоматически при старте приложения (`@PostConstruct` в `UserSearchService`). Если OpenSearch недоступен — приложение продолжает работу (graceful degradation, поиск возвращает пустой список).

### Операции

`UserSearchService` предоставляет три операции:

| Метод | Описание |
|---|---|
| `indexUser(User)` | Добавить/обновить пользователя в индексе |
| `removeUser(Long)` | Удалить пользователя из индекса |
| `search(String query, int page, int size)` | Full-text поиск с пагинацией |

Поиск использует `multi_match` запрос по полям `firstName`, `lastName`, `email` с `fuzziness: AUTO` (поддержка опечаток). При пустом запросе возвращает всех пользователей (`match_all`).

### Использование через API

```bash
# Поиск пользователей (делегирует в UserSearchService)
curl "http://localhost:8080/api/users/search?query=Иван&page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"
```

Ответ — `Page<UserResponse>` со стандартными метаданными пагинации Spring Data.

---

## 12. Фронтенд SPA

Проект включает минималистичный SPA на ванильном JavaScript (`frontend/index.html`).

Он автоматически становится доступен через Spring Boot при сборке Docker-образа и копировании в `src/main/resources/static/`.

**URL**: `http://localhost:8777/index.html` (Docker) или `http://localhost:8080/index.html` (локально)

**Возможности:**
- Регистрация и вход по email/паролю
- Просмотр и редактирование своего профиля
- Лента постов стены
- Создание постов
- Просмотр друзей и запросов дружбы
- Диалог личных сообщений
- Работа с группами

**Технические особенности:**
- Тёмная тема оформления с акцентным синим цветом (`#5b8dee`)
- Google Fonts Inter
- Access token хранится в `localStorage`
- Все запросы к API идут через `fetch` с заголовком `Authorization: Bearer <token>`
- Автоматическая отправка refresh token при 401 ошибке

---

## 13. Swagger UI

Интерактивная документация API доступна по адресу:

```
http://localhost:8777/swagger-ui.html  (Docker)
http://localhost:8080/swagger-ui.html  (локально)
```

OpenAPI JSON-схема:
```
http://localhost:8080/v3/api-docs
```

В Swagger UI можно:
- Просмотреть все эндпоинты с описаниями параметров
- Авторизоваться через кнопку **Authorize** (введите `Bearer <accessToken>`)
- Выполнять запросы к API прямо из браузера

Swagger предоставляется библиотекой `springdoc-openapi-starter-webmvc-ui:2.3.0`.

---

## 14. Тестирование

### Структура тестов

```
src/test/java/com/socialnetwork/
├── controller/
│   ├── AuthControllerTest.java       # интеграционные тесты аутентификации
│   ├── PostControllerTest.java       # интеграционные тесты постов
│   └── CommentControllerTest.java    # интеграционные тесты комментариев
├── service/
│   ├── AuthServiceTest.java          # unit-тесты AuthService
│   ├── PostServiceTest.java          # unit-тесты PostService
│   ├── FriendServiceTest.java        # unit-тесты FriendService
│   ├── GroupServiceTest.java         # unit-тесты GroupService
│   ├── CommentServiceTest.java       # unit-тесты CommentService
│   └── MessageServiceTest.java       # unit-тесты MessageService
└── security/
    └── JwtTokenProviderTest.java     # unit-тесты генерации / валидации JWT
```

### Запуск тестов

```bash
# Все тесты
./gradlew test

# Только unit-тесты сервисов
./gradlew test --tests "com.socialnetwork.service.*"

# Только тесты контроллеров
./gradlew test --tests "com.socialnetwork.controller.*"

# Конкретный тест-класс
./gradlew test --tests "com.socialnetwork.security.JwtTokenProviderTest"
```

### Профиль `test`

Тесты запускаются с профилем `test` (`application.yml`):
- **БД**: H2 in-memory (`MODE=PostgreSQL`) — Liquibase отключён, схема создаётся через `ddl-auto: create-drop`
- **Redis**: использует порт 6370 (чтобы не конфликтовать с локальным Redis)
- **Kafka**: порт 9093 + встроенный `EmbeddedKafkaBroker` через `spring-kafka-test`
- **Кэш**: `simple` (in-memory, без Redis)

### Зависимости для тестирования

| Библиотека | Назначение |
|---|---|
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, MockMvc |
| `spring-security-test` | `@WithMockUser`, `SecurityMockMvcRequestPostProcessors` |
| `spring-kafka-test` | `EmbeddedKafkaBroker` |
| `testcontainers:postgresql` | Реальный PostgreSQL в Docker для интеграционных тестов |
| `testcontainers:junit-jupiter` | Интеграция Testcontainers с JUnit 5 |
| `h2` | In-memory БД для быстрых тестов |

---

## 15. Postman-коллекция

В директории `postman/` находятся готовые файлы для импорта в Postman:

```
postman/
├── SocialNetwork.postman_collection.json  # коллекция всех запросов
└── SocialNetwork.postman_environment.json # переменные окружения
```

**Как импортировать:**
1. Открыть Postman
2. **Import** → выбрать `SocialNetwork.postman_collection.json`
3. **Import** → выбрать `SocialNetwork.postman_environment.json`
4. Активировать окружение `SocialNetwork`

**Переменные окружения Postman:**

| Переменная | Значение по умолчанию |
|---|---|
| `baseUrl` | `http://localhost:8777` |
| `accessToken` | (заполняется автоматически после login/register) |
| `refreshToken` | (заполняется автоматически после login/register) |

Запросы аутентификации (`register`, `login`) содержат скрипт в разделе **Tests**, который автоматически сохраняет токены в переменные окружения.

---

## 16. Возможные улучшения

Список функциональности, которую можно добавить для развития проекта:

### Функциональность

- **WebSocket / STOMP** — чат в реальном времени вместо polling-based сообщений
- **Лайки и реакции** — реакции на посты и комментарии (таблица `post_likes`)
- **Уведомления** — система уведомлений (Kafka-события → push или SSE)
- **Фото в постах** — загрузка изображений к постам (сейчас только `image_url`)
- **Истории** — временный контент (Stories) с автоудалением через 24 часа
- **Поиск по постам/сообщениям** — расширить индекс OpenSearch за рамки пользователей (посты, сообщения)
- **Пагинация курсором** — keyset pagination вместо offset для больших объёмов данных

### Инфраструктура

- **Многоэтапная сборка Docker (multi-stage build)** — уменьшение размера итогового образа
- **Nginx** — reverse proxy + раздача статики фронтенда
- **Spring Boot Actuator + Prometheus + Grafana** — мониторинг и метрики
- **Loki** — централизованное логирование (Grafana Loki вместо ELK)
- **Kubernetes Helm chart** — упаковать существующие k8s-манифесты в Helm для версионирования
- **CI/CD** — GitHub Actions: сборка, тесты, публикация Docker-образа в registry

### Безопасность

- **Rate limiting** — ограничение числа запросов (Bucket4j + Redis)
- **HTTPS** — TLS-сертификат (Let's Encrypt или корпоративный CA)
- **Refresh Token Rotation** — автоматическая ротация refresh токенов при каждом обновлении
- **2FA** — двухфакторная аутентификация через TOTP (Google Authenticator)
- **Audit Log** — логирование всех административных действий

### Качество кода

- **Тесты репозиториев** — тесты на уровне Spring Data JPA с Testcontainers
- **Contract тесты** — Spring Cloud Contract для проверки совместимости API
- **Покрытие кода** — JaCoCo + SonarQube
- **Валидация DTO** — расширенная валидация входящих данных (`@Valid`, кастомные аннотации)

---

## Лицензия

Проект создан в учебных целях. Свободен для использования и модификации.
