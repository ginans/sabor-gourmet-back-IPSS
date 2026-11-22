package cl.ipss.saborgourmet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de metadatos OpenAPI 3 / Swagger UI para la API Sabor Gourmet.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI saborGourmetOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title(ApiConstants.OPENAPI_TITLE)
                        .version(ApiConstants.OPENAPI_VERSION)
                        .description(ApiConstants.OPENAPI_DESCRIPTION)
                        .contact(new Contact()
                                .name(ApiConstants.OPENAPI_CONTACT_NAME)));
    }
}
