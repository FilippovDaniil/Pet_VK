package com.socialnetwork.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI (Swagger) для документирования REST API.
 *
 * <p><b>Что такое OpenAPI / Swagger?</b><br>
 * OpenAPI (ранее Swagger) — стандарт описания REST API в машиночитаемом формате (JSON/YAML).
 * SpringDoc автоматически сканирует контроллеры и генерирует спецификацию API.
 * Swagger UI (доступен по {@code /swagger-ui.html}) отображает документацию
 * в виде интерактивного веб-интерфейса, где можно тестировать эндпоинты прямо из браузера.
 *
 * <p><b>Зачем настраивать авторизацию?</b><br>
 * По умолчанию Swagger UI не знает об аутентификации нашего приложения.
 * Эта конфигурация добавляет в UI кнопку "Authorize", позволяя указать Bearer-токен
 * один раз и автоматически подставлять его в заголовок {@code Authorization} всех запросов.
 */
@Configuration
// @Configuration объявляет класс источником Spring Beans (методы с @Bean)
public class OpenApiConfig {

    /**
     * Создаёт главный объект OpenAPI-спецификации с метаданными и настройкой безопасности.
     *
     * @return настроенный объект {@link OpenAPI} для SpringDoc
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // Метаданные API, отображаемые в шапке Swagger UI
                .info(new Info()
                        .title("Social Network API")          // название API
                        .description("VK-like social network backend") // описание
                        .version("1.0.0"))                    // текущая версия

                // Добавляем требование безопасности ко всем эндпоинтам по умолчанию.
                // Это означает, что Swagger UI будет отправлять Bearer-токен во всех запросах.
                // Имя "Bearer Authentication" должно совпадать с именем схемы ниже.
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))

                // Регистрируем схему безопасности: тип HTTP Bearer с JWT
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        // HTTP — тип схемы (в отличие от apiKey или oauth2)
                                        .type(SecurityScheme.Type.HTTP)
                                        // bearer — подсхема, означает: токен в заголовке Authorization: Bearer ...
                                        .scheme("bearer")
                                        // bearerFormat подсказывает Swagger UI, что это JWT (влияет только на UI)
                                        .bearerFormat("JWT")));
    }
}
