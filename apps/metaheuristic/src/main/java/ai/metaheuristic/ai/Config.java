/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.mhbp.repositories.RefToMhbpRepositories;
import ai.metaheuristic.ai.utils.SpringHelpersUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInterceptor;
import ai.metaheuristic.standalone.StatusFileUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;

/**
 * User: Serg
 * Date: 08.06.2017
 * Time: 17:21
 */
@EnableCaching
@Configuration
@Slf4j
@EnableConfigurationProperties(Globals.class)
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class Config {

/*
    @Configuration
    @EnableWebSocketMessageBroker
    public static class MHWebSocketMessageBrokerConfigurer implements WebSocketMessageBrokerConfigurer {

        @Override
        public void configureMessageBroker(MessageBrokerRegistry config) {
            config.enableSimpleBroker("/chat");
            config.setApplicationDestinationPrefixes("/ws/");
        }

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/ws/chat"); // This will allow you to use ws://localhost:8080/test to establish websocket connection
            registry.addEndpoint("/ws/chat").withSockJS(); // This will allow you to use http://localhost:8080/test to establish websocket connection
        }
    }

    @Configuration
    @EnableWebSocket
    public static class MHWebSocketConfigurer implements WebSocketConfigurer {
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(new MyChatHandler(), "/ws/chat");
        }
    }
*/

    @SuppressWarnings("unused")
    private final SpringChecker springChecker;

    @Configuration
    @EnableAsync
    public static class SpringAsyncConfig implements AsyncConfigurer {
    }

    @Component
    public static class EOFCustomFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterchain)
                throws IOException, ServletException {
            try {
                filterchain.doFilter(request, response);
            }
            catch (Throwable e) {
                Throwable root = ExceptionUtils.getRootCause(e);
                log.error("Error with request from " + request.getRemoteAddr() + ", class: " + request.getClass().getName() + ", root: " +(root!=null ? root.getClass().getName() : null) +", ctx: " + request.getServletContext().getContextPath()+", " + e.getMessage());
                if (root!=null) {
                    if (root instanceof EOFException) {
                        if (request instanceof HttpServletRequest httpRequest) {
                            log.error("EOF with request from "+httpRequest.getRemoteAddr()+" at uri " + httpRequest.getRequestURI());
                        }
                    }
                }
                throw e;
            }
        }
    }

    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return (request, response, requestRejectedException) -> {
            if (log.isDebugEnabled()) {
                log.debug("Rejecting request due to: " + requestRejectedException.getMessage(), requestRejectedException);
            }
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        };
    }

    @Component
    @RequiredArgsConstructor(onConstructor_={@Autowired})
    public static class SpringChecker {

        private final ApplicationContext appCtx;
        private final Globals globals;

        @Value("${server.address:#{null}}")
        public String serverHost;

        @Value("${server.port:#{-1}}")
        public Integer serverPort;

        @Value("${spring.profiles.active}")
        private String activeProfiles;

        @Value("${spring.threads.virtual.enabled}")
        private boolean virtualThreads;

        @PostConstruct
        public void init() {
            checkProfiles();
            logSpring();
        }

        private void logSpring() {
            log.warn("Spring properties:");
            log.warn("'\tserver host:port: {}:{}", serverHost, serverPort);
            log.warn("'\tvirtual is enabled: {}", virtualThreads);
        }

        private void checkProfiles() {
            List<String> profiles = SpringHelpersUtils.getProfiles(activeProfiles);

            if (!profiles.isEmpty()) {
                globals.state.shutdownInProgress = true;
                log.error("\nUnknown profile(s) was encountered in property spring.profiles.active.\nNeed to be fixed.\n" +
                        "Allowed profiles are: " + SpringHelpersUtils.POSSIBLE_PROFILES);
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

    @Bean
    @Profile("dispatcher")
    public MyBeanPostProcessor myBeanPostProcessor() {
        return new MyBeanPostProcessor();
    }

    public static class MyBeanPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (MetaheuristicStatus.metaheuristicStatusFilePath==null) {
                return bean;
            }

            final String simpleName = bean.getClass().getSimpleName();
//            System.out.println("--- postProcessBeforeInitialization executed --- "+ beanName +", " + simpleName);
            if (simpleName.equals("EmbeddedTomcat")) {
                StatusFileUtils.appendStart(MetaheuristicStatus.metaheuristicStatusFilePath, "tomcat");
            }
            if (simpleName.equals("HikariDataSource")) {
                StatusFileUtils.appendStart(MetaheuristicStatus.metaheuristicStatusFilePath, "datasource");
            }
            if (simpleName.equals("SpringLiquibase")) {
                StatusFileUtils.appendStart(MetaheuristicStatus.metaheuristicStatusFilePath, "liquibase");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (MetaheuristicStatus.metaheuristicStatusFilePath==null) {
                return bean;
            }

            final String simpleName = bean.getClass().getSimpleName();
//            System.out.println("--- postProcessAfterInitialization executed ---" + beanName +", " + bean.getClass().getSimpleName());
            if (simpleName.equals("TomcatServletWebServerFactory")) {
                StatusFileUtils.appendDone(MetaheuristicStatus.metaheuristicStatusFilePath, "tomcat");
            }
            if (simpleName.equals("HikariDataSource")) {
                StatusFileUtils.appendDone(MetaheuristicStatus.metaheuristicStatusFilePath, "datasource");
            }
            if (simpleName.equals("SpringLiquibase")) {
                StatusFileUtils.appendDone(MetaheuristicStatus.metaheuristicStatusFilePath, "liquibase");
//                throw new RuntimeException("SpringLiquibase error");
            }
            return bean;
        }

    }

    @Configuration
    @ComponentScan(value={"ai.metaheuristic.ai.dispatcher", "ai.metaheuristic.ai.processor", "ai.metaheuristic.ai.mhbp"},
    excludeFilters = {
            @ComponentScan.Filter(type= FilterType.REGEX, pattern=
                    "ai\\.metaheuristic\\.(ai\\.yaml|ai\\.utils|ai\\.exceptions|ai\\.data|ai\\.dispatcher\\.(beans|data|event\\.events|repositories)|ai\\.mhbp\\.(repositories|data|yaml|events|beans)|api|commons|ww2003)\\..*"),
    })
/*
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.utils\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.exceptions\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.data\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.dispatcher\\.beans\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.dispatcher\\.data\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.dispatcher\\.event\\.events\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.dispatcher\\.repositories\\..*"),

    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.mhbp\\.repositories\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.mhbp\\.data\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.mhbp\\.yaml\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.mhbp\\.events\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ai\\.mhbp\\.beans\\..*"),

    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.api\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.commons\\..*"),
    @ComponentScan.Filter(type= FilterType.REGEX, pattern="ai\\.metaheuristic\\.ww2003\\..*")
*/
    @Profile("dispatcher")
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {RefToDispatcherRepositories.class, RefToMhbpRepositories.class} )
    public static class DispatcherConfig {
    }

}
