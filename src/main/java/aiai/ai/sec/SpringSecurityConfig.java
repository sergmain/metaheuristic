package aiai.ai.sec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 15:12
 */
@Configuration
public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

    private final AccessDeniedHandler accessDeniedHandler;

    @Autowired
    public SpringSecurityConfig(AccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
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

//        http.csrf().disable();

        http
                .authorizeRequests()
                .antMatchers("/", "/index", "/about", "/login", "/jssc").permitAll()
                .antMatchers("/example").permitAll()
                .antMatchers("/login").anonymous()
                .antMatchers("/logout", "launchpad", "/stations").authenticated()
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
                .invalidateHttpSession(true)
                .and()
                .exceptionHandling().accessDeniedHandler(accessDeniedHandler);
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {

        auth.inMemoryAuthentication()
                .withUser("user").password("password").roles("USER")
                .and()
                .withUser("q").password("11").roles("ADMIN")
                .and()
                .withUser("admin").password("password").roles("ADMIN");
    }

    
/*
        <beans:bean class="org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder" id="passwordEncoder">
            <beans:constructor-arg value="10"/>
        </beans:bean>

        <beans:bean class="aiai.web.auth.CustomUserDetailsService" id="customUserDetailsService"/>

        <authentication-manager alias="authenticationManager">
            <authentication-provider user-service-ref="customUserDetailsService">
                <password-encoder ref="passwordEncoder"/>
            </authentication-provider>
        </authentication-manager>

        <beans:bean class="aiai.server.security.googleauth.GoogleAuthenticator" id="googleAuthenticator">
            <beans:constructor-arg value="3"/>
        </beans:bean>
*/
}