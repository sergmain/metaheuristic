/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai;

import ai.metaheuristic.ai.dispatcher.batch.RefToBatchRepositories;
import ai.metaheuristic.ai.dispatcher.repositories.RefToDispatcherRepositories;
import ai.metaheuristic.ai.utils.EnvProperty;
import ai.metaheuristic.ai.utils.cleaner.CleanerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * User: Serg
 * Date: 08.06.2017
 * Time: 17:21
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class Config {

    private final Globals globals;

    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }


    @Value("${ajp.port:#{0}}")
    private int ajpPort;

    @Value("${ajp.enabled:#{false}}")
    private boolean ajpEnabled;

    // https://careydevelopment.us/2017/06/19/run-spring-boot-apache-web-server-front-end/
    @Bean
    public ConfigurableWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        if (ajpEnabled && ajpPort!=0) {
            Connector ajpConnector = new Connector("AJP/1.3");
            ajpConnector.setPort(ajpPort);
            ajpConnector.setSecure(false);
            ajpConnector.setScheme("http");
            ajpConnector.setAllowTrace(false);
            tomcat.addAdditionalTomcatConnectors(ajpConnector);
        }
        return tomcat;
    }

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        log.info("Config.threadPoolTaskScheduler() will use {} as a number of threads for an schedulers", globals.schedulerThreadNumber);
        threadPoolTaskScheduler.setPoolSize(globals.schedulerThreadNumber);
        return threadPoolTaskScheduler;
    }

    @Configuration
    @Profile("dispatcher")
    public static class MhMvcConfig implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new CleanerInterceptor());
        }
    }

    @Configuration
    @ComponentScan("ai.metaheuristic.ai.dispatcher")
    @EnableAsync
    @RequiredArgsConstructor
    @Slf4j
//    @DependsOn({"Globals"})
    public static class SpringAsyncConfig implements AsyncConfigurer {

        private final Globals globals;

        @Override
        public Executor getAsyncExecutor() {
            Integer threads = globals.eventThreadNumber;
            if (threads==null) {
                threads = Math.max(10, Runtime.getRuntime().availableProcessors()/2);
            }
            threads = EnvProperty.minMax( threads, 10, 32);
            log.info("Config.SpringAsyncConfig will use {} as a number of threads for an event processing", threads);

            ThreadPoolExecutor executor =  (ThreadPoolExecutor) Executors.newFixedThreadPool(threads);
            return new ConcurrentTaskExecutor(executor);
        }
    }

    @EnableCaching
    @Configuration
    @ComponentScan("ai.metaheuristic.ai.dispatcher")
    @Profile("dispatcher")
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {RefToDispatcherRepositories.class, RefToBatchRepositories.class} )
    public static class DispatcherConfig {
    }

}
