package com.socialnetwork.search;

import com.socialnetwork.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchService {

    private static final String INDEX = "users";

    private final OpenSearchClient openSearchClient;

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = openSearchClient.indices().exists(r -> r.index(INDEX)).value();
            if (!exists) {
                openSearchClient.indices().create(r -> r.index(INDEX));
                log.info("OpenSearch index '{}' created", INDEX);
            }
        } catch (Exception e) {
            log.warn("OpenSearch unavailable at startup: {}", e.getMessage());
        }
    }

    public void indexUser(User user) {
        try {
            UserDocument doc = toDocument(user);
            openSearchClient.index(r -> r
                    .index(INDEX)
                    .id(doc.getId())
                    .document(doc));
        } catch (Exception e) {
            log.warn("Failed to index user id={}: {}", user.getId(), e.getMessage());
        }
    }

    public void removeUser(Long userId) {
        try {
            openSearchClient.delete(r -> r.index(INDEX).id(String.valueOf(userId)));
        } catch (Exception e) {
            log.warn("Failed to remove user id={} from index: {}", userId, e.getMessage());
        }
    }

    public void reindexAll(List<User> users) {
        users.forEach(this::indexUser);
        log.info("Reindexed {} users", users.size());
    }

    /**
     * Полнотекстовый поиск по firstName, lastName и email.
     * При недоступности OpenSearch возвращает пустой список — graceful degradation.
     */
    public List<UserDocument> search(String query, int page, int size) {
        try {
            Query finalQuery;
            if (query == null || query.isBlank()) {
                finalQuery = Query.of(q -> q.matchAll(m -> m));
            } else {
                finalQuery = Query.of(q -> q.multiMatch(m -> m
                        .fields(List.of("firstName", "lastName", "email"))
                        .query(query)
                        .fuzziness("AUTO")));
            }

            SearchRequest request = new SearchRequest.Builder()
                    .index(INDEX)
                    .from(page * size)
                    .size(size)
                    .query(finalQuery)
                    .build();

            SearchResponse<UserDocument> response =
                    openSearchClient.search(request, UserDocument.class);

            return response.hits().hits().stream()
                    .map(h -> h.source())
                    .toList();
        } catch (Exception e) {
            log.warn("OpenSearch search failed, falling back to empty result: {}", e.getMessage());
            return List.of();
        }
    }

    private UserDocument toDocument(User user) {
        return new UserDocument(
                String.valueOf(user.getId()),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.isBanned()
        );
    }
}
