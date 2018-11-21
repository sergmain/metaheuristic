/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai;

import aiai.ai.launchpad.repositories.RefToLaunchpadRepositories;
import com.google.common.cache.CacheBuilder;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.concurrent.TimeUnit;

/**
 * User: Serg
 * Date: 08.06.2017
 * Time: 17:21
 */
@Configuration
@EnableCaching
@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses = {RefToLaunchpadRepositories.class} )
public class Config {

    private final Globals globals;

    public Config(Globals globals) {
        this.globals = globals;
    }

    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }

    @Bean
    public CacheManager cacheManager() {
        //noinspection UnnecessaryLocalVariable
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager() {
            @Override
            protected Cache createConcurrentMapCache(final String name) {
                return new ConcurrentMapCache(name,
                        CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.DAYS).maximumSize(1000).build().asMap(), false);
            }
        };

        return cacheManager;
    }


    /*
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = { RefToLaunchpadRepositories.class })
    public class LaunchpadDbConfig {

        private final Environment env;

        @Autowired
        public LaunchpadDbConfig(Environment env) {
            this.env = env;
        }

        @Primary
        @Bean(name = "dataSource")
        @ConfigurationProperties(prefix = "spring.datasource")
        public DataSource customDataSource() {
            if (!globals.isLaunchpadEnabled) {
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
            if (!globals.isLaunchpadEnabled) {
                return null;
            }
            return builder
                    .dataSource(dataSource)
                    .packages("aiai.ai.launchpad.beans")
                    .persistenceUnit("launchpad")
                    .build();
        }

        @Primary
        @Bean(name = "transactionManager")
        public PlatformTransactionManager transactionManager(@Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
            if (!globals.isLaunchpadEnabled) {
                return null;
            }
            return new JpaTransactionManager(entityManagerFactory);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(entityManagerFactoryRef = "stationEntityManagerFactory", transactionManagerRef = "stationTransactionManager",
            basePackages = { "aiai.ai.station.repositories" })
    public class StationDbConfig {

        private final Environment env;

        public StationDbConfig(Environment env) {
            this.env = env;
        }

        @Bean(name = "stationDataSource")
        @ConfigurationProperties(prefix = "station.datasource")
        public DataSource dataSource() {
            if (!globals.isStationEnabled) {
                return null;
            }
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            //noinspection ConstantConditions
            dataSource.setDriverClassName(env.getProperty("station.datasource.driver-class-name"));
            dataSource.setUrl(env.getProperty("station.datasource.url"));
            dataSource.setUsername(env.getProperty("station.datasource.username"));
            dataSource.setPassword(env.getProperty("station.datasource.password"));
            return dataSource;
        }

        @Bean(name = "stationEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean stationEntityManagerFactory(EntityManagerFactoryBuilder builder, @Qualifier("stationDataSource") DataSource dataSource) {
            if (!globals.isStationEnabled) {
                return null;
            }
            return builder
                    .dataSource(dataSource)
                    .packages("aiai.ai.station.beans")
                    .persistenceUnit("station")
                    .build();
        }

        @Bean(name = "stationTransactionManager")
        public PlatformTransactionManager stationTransactionManager(@Qualifier("stationEntityManagerFactory") EntityManagerFactory stationEntityManagerFactory) {
            if (!globals.isStationEnabled) {
                return null;
            }
            return new JpaTransactionManager(stationEntityManagerFactory);
        }
    }

*/

    // https://medium.com/@joeclever/using-multiple-datasources-with-spring-boot-and-spring-data-6430b00c02e7

/*
    TODO 20018-08-24 If everything will be fine, then delete this inner class
    @Configuration
    public static class SchedulingConfigurerConfiguration implements SchedulingConfigurer {

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
            taskScheduler.setPoolSize(100);
            taskScheduler.initialize();
            taskRegistrar.setTaskScheduler(taskScheduler);
        }
    }
*/

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(globals.threadNumber);
        return threadPoolTaskScheduler;
    }







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
