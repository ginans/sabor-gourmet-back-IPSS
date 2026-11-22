package cl.ipss.saborgourmet.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades externalizadas para la capa de persistencia en archivo JSON.
 *
 * @param filePath ruta relativa o absoluta del archivo JSON de almacenamiento
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank(message = "Storage file path must not be blank")
        String filePath
) {
}
