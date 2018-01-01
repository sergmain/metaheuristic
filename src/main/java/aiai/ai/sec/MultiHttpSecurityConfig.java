package aiai.ai.sec;

import aiai.ai.Consts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 15:12
 */
@Configuration
public class MultiHttpSecurityConfig {

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Configuration
    @Order(1)
    public class RestAuthSecurityConfig extends WebSecurityConfigurerAdapter {

        static final String REST_REALM = "REST realm";

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .antMatcher("/rest-auth/**").authorizeRequests().anyRequest().authenticated()
                    .and()
                    .httpBasic().realmName(REST_REALM)
                    .and()
                    .csrf().disable();
        }
    }

    @Configuration
    @Order(2)
    public class RestAnonSecurityConfig extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .antMatcher("/rest-anon/**").authorizeRequests().anyRequest().anonymous()
                    .and()
                    .csrf().disable();
        }
    }

    @Configuration
    @Order
    public static class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

        final CsrfTokenRepository csrfTokenRepository;

        @Autowired
        public SpringSecurityConfig(CsrfTokenRepository csrfTokenRepository) {
            this.csrfTokenRepository = csrfTokenRepository;
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http
                    .csrf().csrfTokenRepository(csrfTokenRepository)
                    .and()
                    .headers().frameOptions().sameOrigin()
                    .and()
                    .authorizeRequests()
                    .antMatchers("/manager/html" ).denyAll()
                    .antMatchers("/css/**", "/js/**", "/webjars/**").permitAll()
                    .antMatchers("/", "/index", "/about", "/login", "/jssc", "/srv/**").permitAll()
                    .antMatchers("/example*").permitAll()
                    .antMatchers("/login").anonymous()
                    .antMatchers("/logout", "/launchpad/**", "/stations/**", "/station/**").authenticated()
                    .antMatchers("/admin/**").hasAnyRole("ADMIN")
                    .antMatchers("/user/**").hasAnyRole("USER")
                    .antMatchers("/**/**").denyAll()
                    .and()
                    .formLogin()
                    .loginPage("/login")
                    .usernameParameter("j_username")
                    .passwordParameter("j_password")
                    .loginProcessingUrl("/jssc")
                    .defaultSuccessUrl("/index")
                    .and()
                    .logout()
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/index")
                    .deleteCookies(Consts.SESSIONID_NAME)
                    .invalidateHttpSession(true);
        }

    }
}