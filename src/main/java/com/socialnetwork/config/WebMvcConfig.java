package com.socialnetwork.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Конфигурация Spring MVC для раздачи статических файлов (аватары пользователей).
 *
 * <p><b>Задача:</b><br>
 * Когда пользователь загружает аватар через {@code POST /api/users/me/avatar},
 * файл сохраняется на диск сервера в каталог {@code app.upload.path} (настроен в application.yml).
 * Чтобы фронтенд мог отобразить аватар, нужно «раздавать» файлы из этого каталога
 * через HTTP. Этот конфигурационный класс создаёт маппинг URL → папка на диске.
 *
 * <p><b>Пример работы:</b><br>
 * <ol>
 *   <li>Файл сохранён в {@code /var/uploads/avatars/abc123.jpg}</li>
 *   <li>UserService записывает в профиль {@code avatarUrl = "/uploads/avatars/abc123.jpg"}</li>
 *   <li>Браузер запрашивает {@code GET /uploads/avatars/abc123.jpg}</li>
 *   <li>Spring MVC находит файл через этот Resource Handler и возвращает его</li>
 * </ol>
 *
 * <p><b>Альтернатива:</b><br>
 * В production обычно используют выделенный HTTP-сервер (Nginx) или облачное хранилище (S3/MinIO)
 * для раздачи статических файлов — это эффективнее, чем Java-сервер.
 * Данный подход подходит для разработки и небольших проектов.
 */
@Configuration
// @Configuration — класс содержит конфигурацию Spring MVC
public class WebMvcConfig implements WebMvcConfigurer {
// WebMvcConfigurer — интерфейс Spring MVC для расширения конфигурации без перезаписи дефолтных настроек.
// Реализуем только нужный метод (addResourceHandlers), остальные оставляем дефолтными.

    // Путь к каталогу загрузки файлов. Берётся из application.yml: app.upload.path=/path/to/uploads
    // Это относительный или абсолютный путь файловой системы сервера.
    @Value("${app.upload.path}")
    private String uploadPath;

    /**
     * Настраивает маппинг URL-паттерна на директорию файловой системы.
     *
     * <p>После этой настройки любой GET-запрос к {@code /uploads/avatars/*}
     * будет обслуживаться как раздача статического файла из {@code uploadPath}.
     *
     * @param registry реестр обработчиков статических ресурсов Spring MVC
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Преобразуем относительный путь в абсолютный — на разных ОС и конфигурациях
        // рабочий каталог JVM может быть разным, абсолютный путь надёжнее
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().toString();

        // addResourceHandler: URL-паттерн, который будет обслуживать этот обработчик
        // "/uploads/avatars/**": любой URL, начинающийся с /uploads/avatars/ (** — любые символы)
        registry.addResourceHandler("/uploads/avatars/**")
                // addResourceLocations: физическое расположение файлов на диске.
                // Префикс "file:" указывает Spring MVC, что это путь файловой системы,
                // а не classpath-ресурс (который был бы с префиксом "classpath:").
                // Завершающий "/" обязателен — Spring требует, чтобы путь заканчивался слэшем.
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
