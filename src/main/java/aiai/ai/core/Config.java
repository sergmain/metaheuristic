package aiai.ai.core;

import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * User: Serg
 * Date: 08.06.2017
 * Time: 17:21
 */
@Configuration
public class Config {

    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }
}
