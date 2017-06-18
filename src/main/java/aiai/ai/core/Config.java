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


/*
https://github.com/ozimov/spring-boot-email-tools


spring.boot properties
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=3000
spring.mail.properties.mail.smtp.writetimeout=5000


e-mail template sample

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
