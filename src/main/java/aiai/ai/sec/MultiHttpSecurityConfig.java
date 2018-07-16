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

package aiai.ai.sec;

import aiai.ai.Consts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

        @Value("${server.address:#{null}}")
        private String serverAddress;

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
                    .antMatchers("/manager/html").denyAll()
                    .antMatchers("/css/**", "/js/**", "/webjars/**").permitAll()
                    .antMatchers("/favicon.ico", "/", "/index", "/about", "/login", "/jssc", "/srv/**").permitAll()
                    .antMatchers("/", "/index", "/jssc", "/error/**").permitAll()
                    .antMatchers("/example*").permitAll()
                    .antMatchers("/login").anonymous()
                    .antMatchers("/logout", "/launchpad/**", "/station/**").authenticated()
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
//                    .deleteCookies(Consts.SESSIONID_NAME)
//                    .invalidateHttpSession(true)
            ;
            if (!"127.0.0.1".equals(serverAddress)) {
                http.requiresChannel().antMatchers("/**").requiresSecure();
            }
        }

    }
}