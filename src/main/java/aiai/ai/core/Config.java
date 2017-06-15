package aiai.ai.core;

import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * User: Serg
 * Date: 08.06.2017
 * Time: 17:21
 */
@Configuration
@EnableJpaRepositories(basePackages = {"aiai.ai.launchpad", "aiai.ai.station"} )
@EnableTransactionManagement
public class Config {

    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }

    @Configuration
    public static class SchedulingConfigurerConfiguration implements SchedulingConfigurer {

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
/*
            ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
            taskScheduler.setPoolSize(100);
            taskScheduler.initialize();
            taskRegistrar.setTaskScheduler(taskScheduler);
*/
        }
    }
}
