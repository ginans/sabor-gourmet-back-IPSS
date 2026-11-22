package cl.ipss.saborgourmet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada principal de la API REST de reservas del restaurante Sabor Gourmet.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SaborGourmetApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaborGourmetApplication.class, args);
    }
}
