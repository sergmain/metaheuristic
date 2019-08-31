/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.ai.Globals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 15:12
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MultiHttpSecurityConfig {

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("authorization", "content-type", "x-auth-token"));
        configuration.setExposedHeaders(List.of("x-auth-token"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Configuration
    @Order(1)
    public class RestAuthSecurityConfig extends WebSecurityConfigurerAdapter {

        static final String REST_REALM = "REST realm";

        private final Globals globals;

        public RestAuthSecurityConfig(Globals globals) {
            this.globals = globals;
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            if (globals.isSecurityEnabled) {

                http
                        .antMatcher("/rest/**/**").cors()
                        .and()
                        .antMatcher("/rest/**/**").sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .and()
                        .authorizeRequests()
                        .antMatchers("/rest/login").permitAll()
                        .antMatchers("/rest/v1/srv/**/**", "/rest/v1/srv-v2/**/**", "/rest/v1/payload/**/**", "/rest/v1/upload/**/**").hasAuthority("ROLE_SERVER_REST_ACCESS")
                        .antMatchers("/rest/**/**").authenticated()
                        .and()
                        .antMatcher("/rest/**/**").httpBasic().realmName(REST_REALM)
                        .and()
                        .antMatcher("/rest/**/**").csrf().disable().headers().cacheControl();
            }
            else {
                http
                        .antMatcher("/rest/**/**").cors()
                        .and()
                        .antMatcher("/rest/**/**").sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .and()
                        .antMatcher("/rest/**/**").authorizeRequests().anyRequest().anonymous()
                        .and()
                        .antMatcher("/rest/**/**").csrf().disable().headers().cacheControl();

            }
            if (globals.isSslRequired) {
                http.requiresChannel().antMatchers("/**").requiresSecure();
            }
        }

    }

    @Configuration
    @Order
    public static class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

        final CsrfTokenRepository csrfTokenRepository;
        private final Globals globals;

        @Autowired
        public SpringSecurityConfig(CsrfTokenRepository csrfTokenRepository, Globals globals) {
            this.csrfTokenRepository = csrfTokenRepository;
            this.globals = globals;
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
                    .antMatchers("/static/**/**", "/css/**", "/js/**", "/webjars/**").permitAll()
                    .antMatchers("/favicon.ico", "/", "/index", "/about", "/login", "/jssc", "/error/**").permitAll()
                    .antMatchers("/login").anonymous()
                    .antMatchers("/logout", "/launchpad/**/**", "/registry/**").authenticated()
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
                    .logoutSuccessUrl("/index");

            if (globals.isSslRequired) {
                http.requiresChannel().antMatchers("/**").requiresSecure();
            }
        }

    }
}