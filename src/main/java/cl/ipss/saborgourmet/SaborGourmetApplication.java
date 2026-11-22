package cl.ipss.saborgourmet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main entry point for the Sabor Gourmet restaurant reservation REST API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SaborGourmetApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaborGourmetApplication.class, args);
    }
}
