/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.utils.cleaner.CleanerInterceptor;
import lombok.RequiredArgsConstructor;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
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
@EnableCaching
@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses = {RefToDispatcherRepositories.class, RefToBatchRepositories.class} )
@RequiredArgsConstructor
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
        threadPoolTaskScheduler.setPoolSize(globals.threadNumber);
        return threadPoolTaskScheduler;
    }

    @Configuration
    public static class MhMvcConfig implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new CleanerInterceptor());
        }
    }

    @Configuration
    @ComponentScan("ai.metaheuristic.ai.dispatcher")
    @EnableAsync
    public static class SpringAsyncConfig implements AsyncConfigurer {
        @Override
        public Executor getAsyncExecutor() {
//            ThreadPoolExecutor executor =  (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
            ThreadPoolExecutor executor =  (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()/2));
            return new ConcurrentTaskExecutor(executor);
        }
    }

/*
    @Bean
    public Caffeine caffeineConfig() {
        return Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.MINUTES).maximumSize(10000);
    }

    @Bean
    public CacheManager cacheManager(Caffeine caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }
*/

/*
    @Bean
    public CacheManager cacheManager(CaffeineCacheManager caffeineCacheManager) {
        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }
*/

/*
    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(new SimpleAsyncTaskExecutor());
        return eventMulticaster;
    }
*/


/*
    @Bean
    public CacheManager cacheManager() {

        LoadingCache<Key, Graph> graphs = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .refreshAfterWrite(1, TimeUnit.MINUTES)
                .build(key -> createExpensiveGraph(key));            }


        //noinspection UnnecessaryLocalVariable
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager() {
            @Override
            protected Cache createConcurrentMapCache(final String name) {

                return new ConcurrentMapCache(name,
                        CacheBuilder
                                .newBuilder()
                                .expireAfterWrite(1, TimeUnit.DAYS)
                                .maximumSize(1000)
                                .build()
                                .asMap(), false);
            }
        };

        return cacheManager;
    }
*/

/*
    // https://medium.com/@joeclever/using-multiple-datasources-with-spring-boot-and-spring-data-6430b00c02e7


    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { RefToDispatcherRepositories.class })
    public class DispatcherDbConfig {

        private final Environment env;

        @Autowired
        public DispatcherDbConfig(Environment env) {
            this.env = env;
        }

        @Primary
        @Bean(name = "dataSource")
        @ConfigurationProperties(prefix = "spring.datasource")
        public DataSource customDataSource() {
            if (!globals.dispatcherEnabled) {
                return null;
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            //noinspection ConstantConditions
            dataSource.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));
            dataSource.setUrl(env.getProperty("spring.datasource.url"));
            dataSource.setUsername(env.getProperty("spring.datasource.username"));
            dataSource.setPassword(env.getProperty("spring.datasource.password"));

            return dataSource;
        }

        @Primary
        @Bean(name = "entityManagerFactory")
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder, @Qualifier("dataSource") DataSource dataSource) {
            if (!globals.dispatcherEnabled) {
                return null;
            }
            return builder
                    .dataSource(dataSource)
                    .packages("ai.metaheuristic.ai.dispatcher.beans")
                    .persistenceUnit("dispatcher")
                    .build();
        }

        @Primary
        @Bean(name = "transactionManager")
        public PlatformTransactionManager transactionManager(@Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
            if (!globals.dispatcherEnabled) {
                return null;
            }
            return new JpaTransactionManager(entityManagerFactory);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(entityManagerFactoryRef = "processorEntityManagerFactory", transactionManagerRef = "processorTransactionManager",
            basePackages = { "ai.metaheuristic.ai.processor.repositories" })
    public class ProcessorDbConfig {

        private final Environment env;

        public ProcessorDbConfig(Environment env) {
            this.env = env;
        }

        @Bean(name = "processorDataSource")
        @ConfigurationProperties(prefix = "processor.datasource")
        public DataSource dataSource() {
            if (!globals.processorEnabled) {
                return null;
            }
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            //noinspection ConstantConditions
            dataSource.setDriverClassName(env.getProperty("processor.datasource.driver-class-name"));
            dataSource.setUrl(env.getProperty("processor.datasource.url"));
            dataSource.setUsername(env.getProperty("processor.datasource.username"));
            dataSource.setPassword(env.getProperty("processor.datasource.password"));
            return dataSource;
        }

        @Bean(name = "processorEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean processorEntityManagerFactory(EntityManagerFactoryBuilder builder, @Qualifier("processorDataSource") DataSource dataSource) {
            if (!globals.processorEnabled) {
                return null;
            }
            return builder
                    .dataSource(dataSource)
                    .packages("ai.metaheuristic.ai.processor.beans")
                    .persistenceUnit("processor")
                    .build();
        }

        @Bean(name = "processorTransactionManager")
        public PlatformTransactionManager processorTransactionManager(@Qualifier("processorEntityManagerFactory") EntityManagerFactory processorEntityManagerFactory) {
            if (!globals.processorEnabled) {
                return null;
            }
            return new JpaTransactionManager(processorEntityManagerFactory);
        }
    }

*/






/*
https://github.com/ozimov/spring-boot-email-tools


spring.boot properties
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=3000
spring.mail.properties.mail.smtp.writetimeout=5000


e-mail template sample
                                                               d
    <bean class="org.springframework.mail.SimpleMailMessage" id="accountActivationMailTemplate">
        <property name="from" value="${email.from}"/>
        <property name="subject" value="Account Activation"/>
        <property name="text">
            <value><![CDATA[Hi {{NAME}}!

You had registered with us. To activate your account, please click on the following URL:

	http://localhost:8080/signup?id={{ID}}&k={{ACTIVATION_KEY}}

Thanks]]></value>
        </property>
    </bean>

    <bean class="org.springframework.mail.SimpleMailMessage" id="accountRecoveryMailTemplate">
        <property name="from" value="${email.from}"/>
        <property name="subject" value="Account Recovery"/>
        <property name="text">
            <value><![CDATA[Hi {{NAME}}!

You had requested account reset. To reset your account {{LOGIN}}, please click on the following URL:

	http://localhost:8080/forgot/set/{{KEY_ID}}/{{KEY_ID_TIME}}

This request was made from IP address {{REMOTE_ADDRESS}}.
Note: This link is valid only for 24 hours.

Thanks]]></value>
        </property>
    </bean>

     */

}
