package cl.ipss.saborgourmet.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone el reloj del sistema como bean inyectable para pruebas deterministas de fechas y ventanas horarias.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
