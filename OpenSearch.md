# OpenSearch — Полнотекстовый поиск пользователей

Документ описывает **все нюансы** подключения OpenSearch к Spring Boot 3.x проекту.  
Написан по опыту интеграции в `Pet_VK`: каждая ловушка из раздела «Сводная таблица» была реально встречена.

---

## Мотивация

PostgreSQL `LIKE '%запрос%'` не использует B-tree индексы → полный скан таблицы на каждый запрос.  
OpenSearch решает это через инвертированный индекс + анализаторы текста:

| Возможность | PostgreSQL LIKE | OpenSearch |
|---|---|---|
| Full-text поиск | ❌ медленный LIKE | ✅ анализатор + TF-IDF |
| Поиск по нескольким полям | ❌ писать AND OR вручную | ✅ `multi_match` |
| Нечёткий поиск (опечатки) | ❌ нет | ✅ `fuzziness: AUTO` |
| Релевантность | ❌ нет | ✅ по умолчанию |
| Graceful degradation | ❌ нет | ✅ API продолжает работать при падении |

В Pet_VK OpenSearch используется для поиска пользователей по `firstName`, `lastName`, `email`.  
Индекс `users` синхронизируется при каждом изменении профиля через `UserSearchService`.

---

## Зависимости (build.gradle)

```groovy
// OpenSearch Java Client — Query DSL, индексирование, поиск
implementation 'org.opensearch.client:opensearch-java:2.15.0'

// Транспорт: Apache HttpClient 5.x
// ⚠️ КРИТИЧЕСКИ ВАЖНО: НЕ указывать версию явно!
// Spring Boot 3.4.x BOM управляет версией (5.4.x).
// Если явно указать 5.3.x → NoClassDefFoundError: TlsSocketStrategy при старте приложения.
implementation 'org.apache.httpcomponents.client5:httpclient5'
```

### Почему не `RestClientTransport`

Официальные гайды и AI-ассистенты часто показывают устаревший вариант:

```java
// ❌ НЕ РАБОТАЕТ в Spring Boot 3.x:
RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();
OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
```

**Причина:** `RestClientTransport` требует `opensearch-rest-client`, который тянет `httpclient` 4.x  
(`org.apache.http`). В Spring Boot 3.x зависимость `httpclient` (4.x) **удалена из BOM**.

Правильный подход — `ApacheHttpClient5TransportBuilder` (httpclient5):

```java
// ✅ ПРАВИЛЬНО для Spring Boot 3.x:
HttpHost httpHost = new HttpHost(scheme, host, port);  // httpclient5: (scheme, host, port)!
OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
        .builder(httpHost)
        .setMapper(new JacksonJsonpMapper())
        .build();
```

---

## Конфигурация — OpenSearchConfig.java

```java
package com.socialnetwork.config;

import org.apache.hc.core5.http.HttpHost;          // ← httpclient5, не org.apache.http!
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        // ⚠️ ВАЖНО: в httpclient5 порядок параметров HttpHost — (scheme, host, port)
        // В httpclient4 было: (host, port, scheme) — легко перепутать, результат: NPE
        HttpHost httpHost = new HttpHost(scheme, host, port);

        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(httpHost)
                .setMapper(new JacksonJsonpMapper())
                .build();
        return new OpenSearchClient(transport);
    }
}
```

**Конфигурация** (`application.yml`):
```yaml
opensearch:
  host: ${OPENSEARCH_HOST:localhost}
  port: ${OPENSEARCH_PORT:9200}
  scheme: ${OPENSEARCH_SCHEME:http}
```

Spring Boot автоматически конвертирует переменные окружения:
```
OPENSEARCH_HOST → opensearch.host
OPENSEARCH_PORT → opensearch.port
```

---

## Модель документа — UserDocument.java

```java
package com.socialnetwork.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {
    private String id;          // _id в OpenSearch — ВСЕГДА String (даже если в БД Long)
    private String firstName;
    private String lastName;
    private String email;
    private String avatarUrl;
    private boolean banned;
}
```

**Это не JPA-сущность.** Обычный POJO — OpenSearch сам создаёт динамический маппинг.

Маппинг, который создаст OpenSearch автоматически:
- `firstName`, `lastName`, `email` → `text` (анализируется, участвует в `multi_match`)
- `avatarUrl` → `text`
- `banned` → `boolean` (точный фильтр через `term`)

---

## UserSearchService — ключевые паттерны

### Принцип graceful degradation

Все публичные методы оборачивают OpenSearch-вызовы в `try-catch`:

```java
public void indexUser(User user) {
    try {
        UserDocument doc = toDocument(user);
        openSearchClient.index(r -> r
                .index(INDEX)
                .id(doc.getId())
                .document(doc));
    } catch (Exception e) {
        // НЕ бросаем исключение — приложение продолжает работать без поиска
        log.warn("Failed to index user id={}: {}", user.getId(), e.getMessage());
    }
}
```

Если OpenSearch недоступен:
- `GET /api/users/me` — работает (PostgreSQL + JPA)
- `GET /api/users/search` — возвращает пустой список, не 500

### @PostConstruct ensureIndex()

```java
@PostConstruct
public void ensureIndex() {
    try {
        boolean exists = openSearchClient.indices().exists(r -> r.index("users")).value();
        if (!exists) {
            openSearchClient.indices().create(r -> r.index("users"));
            log.info("OpenSearch index 'users' created");
        }
    } catch (Exception e) {
        log.warn("OpenSearch unavailable at startup: {}", e.getMessage());
    }
}
```

### Построение поискового запроса

```java
public List<UserDocument> search(String query, int page, int size) {
    try {
        Query finalQuery;
        if (query == null || query.isBlank()) {
            finalQuery = Query.of(q -> q.matchAll(m -> m));
        } else {
            finalQuery = Query.of(q -> q.multiMatch(m -> m
                    .fields(List.of("firstName", "lastName", "email"))
                    .query(query)
                    .fuzziness("AUTO")));   // AUTO: расстояние Левенштейна по длине слова
        }

        SearchRequest request = new SearchRequest.Builder()
                .index(INDEX)
                .from(page * size)   // OFFSET: page=0,size=20 → from=0; page=1,size=20 → from=20
                .size(size)          // LIMIT
                .query(finalQuery)
                .build();

        SearchResponse<UserDocument> response =
                openSearchClient.search(request, UserDocument.class);

        return response.hits().hits().stream()
                .map(h -> h.source())
                .toList();
    } catch (Exception e) {
        log.warn("OpenSearch search failed, falling back to empty result: {}", e.getMessage());
        return List.of();   // graceful degradation
    }
}
```

**`fuzziness: AUTO`** — стандартное значение:
- длина слова 0-2: точное совпадение
- длина слова 3-5: 1 ошибка
- длина слова 6+: 2 ошибки

---

## Интеграция в UserService

При каждом изменении пользователя:

```java
// register() / updateProfile() / uploadAvatar():
User saved = userRepository.save(user);
userSearchService.indexUser(saved);   // синхронизируем с индексом

// deleteUser() (пример, если появится в будущем):
userRepository.deleteById(id);
userSearchService.removeUser(id);     // удаляем из индекса
```

---

## Docker Compose

```yaml
opensearch:
  image: opensearchproject/opensearch:2.17.0
  environment:
    - discovery.type=single-node          # без кластеризации (dev режим)
    - DISABLE_SECURITY_PLUGIN=true         # HTTP без TLS и Basic Auth — ТОЛЬКО для dev!
    - bootstrap.memory_lock=false
    - OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m   # фиксируем heap
  ports:
    - "9200:9200"
  volumes:
    - opensearch_data:/usr/share/opensearch/data
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 40s

app:
  environment:
    OPENSEARCH_HOST: opensearch    # ← имя сервиса в docker-compose сети, не localhost!
    OPENSEARCH_PORT: "9200"
    OPENSEARCH_SCHEME: http
  depends_on:
    opensearch:
      condition: service_healthy   # ждём healthcheck перед стартом приложения
```

---

## Kubernetes — 05-opensearch.yaml

### vm.max_map_count — обязательное требование

OpenSearch падает без этой настройки:
```
max virtual memory areas vm.max_map_count [65530] is too low, increase to at least [262144]
```

В Rancher Desktop (k3s в Linux VM) устанавливается через privileged initContainer:

```yaml
initContainers:
  - name: sysctl-fix
    image: busybox:1.36
    command: ["sysctl", "-w", "vm.max_map_count=262144"]
    securityContext:
      privileged: true   # требуется для изменения параметра ядра
```

### imagePullPolicy для OpenSearch

```yaml
containers:
  - name: opensearch
    image: opensearchproject/opensearch:2.17.0
    imagePullPolicy: IfNotPresent   # ← НЕ Never!
```

`imagePullPolicy: Never` — только для кастомных образов (`pet-vk-app`), которые мы загружаем вручную.  
Публичные образы (opensearch, postgres, redis) используют `IfNotPresent`.

### initContainer wait-for-opensearch в 06-app.yaml

```yaml
initContainers:
  - name: wait-for-opensearch
    image: busybox:1.36
    command:
      - sh
      - -c
      - |
        until nc -z opensearch 9200; do
          echo "Waiting for opensearch:9200..."
          sleep 3
        done
        echo "OpenSearch is ready!"
```

Без этого: Spring Boot стартует до готовности OpenSearch, `ensureIndex()` вызывается  
немедленно — индекс не создаётся, но приложение продолжает работу (graceful degradation).

### Имена сервисов

| Среда | Переменная | Значение |
|---|---|---|
| Docker Compose | `OPENSEARCH_HOST` | `opensearch` (имя сервиса) |
| Kubernetes | `OPENSEARCH_HOST` | `opensearch` (имя K8s Service в namespace `pet-vk`) |

Не `localhost` — приложение и OpenSearch в разных контейнерах.

### Ресурсы

```yaml
resources:
  requests:
    memory: "600Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

Без limits OpenSearch может занять всю память ноды и вытолкнуть другие поды.  
Фиксация heap (`-Xms512m -Xmx512m`) предотвращает динамическое расширение выше лимита.

---

## Поисковый эндпоинт

```
GET /api/users/search?query=<строка>&page=0&size=20
```

| Параметр | Тип | Обязательный | Описание |
|---|---|---|---|
| `query` | String | **да** | Полнотекстовый запрос по `firstName`, `lastName`, `email` |
| `page` | int | нет | Номер страницы (от 0), по умолчанию 0 |
| `size` | int | нет | Размер страницы, по умолчанию 20 |

Примеры:
```
GET /api/users/search?query=Иван
GET /api/users/search?query=ivanov@
GET /api/users/search?query=Петр&page=1&size=10
```

Доступ: требует `Authorization: Bearer <accessToken>`.

Реализация в `UserController` — делегирует в `UserService.searchUsers()`, который вызывает  
`UserSearchService.search()` и маппит `UserDocument` → `UserResponse`.

---

## Сводная таблица нюансов

| # | Проблема | Симптом | Решение |
|---|----------|---------|---------|
| 1 | `httpclient5:5.3.x` с явной версией | `NoClassDefFoundError: TlsSocketStrategy` | Убрать версию — BOM даёт 5.4.x |
| 2 | `RestClientTransport` (httpclient4) | `ClassNotFoundException: org.apache.http.HttpHost` | Использовать `ApacheHttpClient5TransportBuilder` |
| 3 | Неверный порядок параметров `HttpHost` | NPE или неверный хост | httpclient5: `(scheme, host, port)` — отличается от 4.x |
| 4 | `vm.max_map_count` в K8s | OpenSearch выходит с ошибкой (Exit 78) | Privileged initContainer `sysctl` |
| 5 | `imagePullPolicy: Never` для OpenSearch | `ErrImageNeverPull` | `IfNotPresent` для публичных образов |
| 6 | Нет `wait-for-opensearch` initContainer | `ensureIndex()` вызван до готовности — попытка тихо игнорируется | initContainer в `06-app.yaml` |
| 7 | `OPENSEARCH_HOST=localhost` в Docker/K8s | `Connection refused` — приложение ищет себя | Указать имя сервиса: `opensearch` |
| 8 | OpenSearch упал → поиск 500 | Пользователи не могут искать | `try-catch` в `UserSearchService` → пустой список |

---

## Проверка работоспособности

```powershell
# Прямой доступ к OpenSearch через port-forward (K8s)
kubectl port-forward -n pet-vk svc/opensearch 9200:9200

# Статус кластера — должен быть green или yellow
curl http://localhost:9200/_cluster/health

# Количество документов в индексе users
curl http://localhost:9200/users/_count

# Full-text поиск прямо в OpenSearch
curl "http://localhost:9200/users/_search?q=firstName:Иван&size=5&pretty"

# Поиск через API приложения (NodePort)
curl "http://localhost:30777/api/users/search?query=Иван" -H "Authorization: Bearer <token>"
```

Docker Compose — OpenSearch доступен напрямую:
```bash
curl http://localhost:9200/_cluster/health
curl http://localhost:9200/users/_count
```
