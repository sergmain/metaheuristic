/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jspecify.annotations.Nullable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.security.config.Customizer.withDefaults;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 15:12
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MultiHttpSecurityConfig {

    private final Globals globals;
//    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // https://github.com/spring-guides/gs-rest-service-cors/blob/master/complete/src/test/java/hello/GreetingIntegrationTests.java
    // TODO 2019-10-13 need to investigate how to use CORS with restTemplate
/*
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return corsConfigurationSource(globals.corsAllowedOrigins);
    }
*/

    private static UrlBasedCorsConfigurationSource corsConfigurationSource(@Nullable List<String> corsAllowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of(GET.name(), POST.name(), PUT.name(), PATCH.name(), DELETE.name(), OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(AUTHORIZATION, CONTENT_TYPE, Consts.X_AUTH_TOKEN));
        configuration.setExposedHeaders(List.of(Consts.X_AUTH_TOKEN, CONTENT_DISPOSITION));
        // set max-age to 1 minute
//        configuration.setMaxAge(60L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain restFilterChain(HttpSecurity http) throws Exception {
        http
            .httpBasic(withDefaults())
            .cors((r)->	r.configurationSource(corsConfigurationSource(globals.corsAllowedOrigins)))
            .sessionManagement((sessionManagement) -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests((requests) -> requests
                    .requestMatchers(OPTIONS).permitAll() // allow CORS option calls for Swagger UI
                    .requestMatchers("/","/index.html", "/*.js", "/*.css", "/favicon.ico", "/assets/**","/resources/**", "/rest/login").permitAll()
                    .requestMatchers("/rest/v1/standalone/anon/**", "/rest/v1/dispatcher/anon/**").permitAll()
                    .requestMatchers("/rest/v1/dispatcher/status/**").hasAuthority("ROLE_MAIN_ADMIN")
                    .requestMatchers("/rest/**").authenticated()
                    .requestMatchers("/ws/**").hasAuthority("ROLE_SERVER_REST_ACCESS")
                    .anyRequest().denyAll()
            )
            .csrf(AbstractHttpConfigurer::disable)
            .headers((headers) -> headers
                .contentTypeOptions(withDefaults())
                .xssProtection(withDefaults())
                .cacheControl(withDefaults())
                .httpStrictTransportSecurity(withDefaults())
                .frameOptions(withDefaults()));

        if (globals.security.sslRequired) {
            http.requiresChannel((requiresChannel) -> requiresChannel.anyRequest().requiresSecure());
        }
        return http.build();
    }

}
