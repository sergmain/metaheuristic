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

import ai.metaheuristic.ai.dispatcher.repositories.RefToDispatcherRepositories;
import ai.metaheuristic.ai.utils.EnvProperty;
import ai.metaheuristic.ai.utils.cleaner.CleanerInterceptor;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.apache.catalina.connector.Connector;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * User: Serg
 * Date: 08.06.2017
 * Time: 17:21
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(Globals.class)
public class Config {

    private final Globals globals;
    @SuppressWarnings("unused")
    private final SpringChecker springChecker;

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
        log.info("Config.threadPoolTaskScheduler() will use {} as a number of threads for an schedulers", globals.threadNumber.getScheduler());
        threadPoolTaskScheduler.setPoolSize(globals.threadNumber.getScheduler());
        return threadPoolTaskScheduler;
    }

    @Component
    @RequiredArgsConstructor
    public static class SpringChecker {
        private static final List<String> POSSIBLE_PROFILES = List.of("dispatcher", "processor", "quickstart");

        private final ApplicationContext appCtx;

        @Value("${spring.host:#{null}}")
        public String serverHost;

        @Value("${server.port:#{-1}}")
        public Integer serverPort;

        @Value("${spring.profiles.active}")
        private String activeProfiles;

        @PostConstruct
        public void init() {
            checkProfiles();
            logSpring();
        }

        private void logSpring() {
            log.warn("Spring properties:");
            log.warn("'\tserver host:port: {}:{}", serverHost, serverPort);
        }

        private void checkProfiles() {
            List<String> profiles = Arrays.stream(StringUtils.split(activeProfiles, ", "))
                    .filter(o-> !POSSIBLE_PROFILES.contains(o))
                    .peek(o-> log.error(S.f("\n!!! Unknown profile: %s\n", o)))
                    .collect(Collectors.toList());

            if (!profiles.isEmpty()) {
                log.error("\nUnknown profile(s) was encountered in property spring.profiles.active.\nNeed to be fixed.\n" +
                        "Allowed profiles are: " + POSSIBLE_PROFILES);
                System.exit(SpringApplication.exit(appCtx, () -> -500));
            }
        }

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
    public static class SpringAsyncConfig implements AsyncConfigurer {

        private final Globals globals;

        @Override
        public Executor getAsyncExecutor() {
            int threads = globals.threadNumber.getEvent();
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
    @EnableJpaRepositories(basePackageClasses = {RefToDispatcherRepositories.class} )
    public static class DispatcherConfig {
    }

}
