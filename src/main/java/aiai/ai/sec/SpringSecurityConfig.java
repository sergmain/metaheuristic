package aiai.ai.sec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 15:12
 */
@Configuration
public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    // roles admin allow to access /admin/**
    // roles user allow to access /user/**
    // custom 403 access denied handler
    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http.headers().frameOptions().sameOrigin();

/*
           <intercept-url pattern="/passwd/**" access="isAuthenticated()" />
           <intercept-url pattern="/2fa/**" access="isAuthenticated()" />
           &lt;!&ndash;<intercept-url pattern="/choices/**" access="hasRole('ROLE_ADMIN')" />&ndash;&gt;
*/

        // Disabled until I'll figure out how to use REST-request with csrf
//        http.csrf().disable();
        http.csrf().csrfTokenRepository(csrfTokenRepository());

        http
                .authorizeRequests()
                .antMatchers("/css/**", "/js/**", "/webjars/**").permitAll()
                .antMatchers("/", "/index", "/about", "/login", "/jssc", "/srv/**").permitAll()
                .antMatchers("/example*").permitAll()
                .antMatchers("/login").anonymous()
                .antMatchers("/logout", "/launchpad/**", "/stations/**", "/station/**").authenticated()
                .antMatchers("/admin/**").hasAnyRole("ADMIN")
                .antMatchers("/user/**").hasAnyRole("USER")
                //.antMatchers("/**/*").denyAll()

                .anyRequest().authenticated()
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
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true);
    }
}