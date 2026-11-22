package cl.ipss.saborgourmet.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration properties for the JSON persistence layer.
 *
 * @param filePath relative or absolute path to the JSON storage file
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank(message = "Storage file path must not be blank")
        String filePath
) {
}
