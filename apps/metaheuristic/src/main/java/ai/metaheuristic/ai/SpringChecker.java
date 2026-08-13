/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

import ai.metaheuristic.ai.utils.SpringHelpersUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates the active profile set and logs the Spring wiring.
 *
 * <p>❗ <b>A TOP-LEVEL component on purpose — it used to be a nested class inside {@link Config},
 * and that is what broke plugin profiles.</b> A class nested in a {@code @Configuration} is
 * instantiated while the configuration class is being processed, which is before plugin
 * auto-configuration classes are loaded. Plugins register their profile names from their
 * auto-config STATIC INITIALIZER, and a static initializer runs only when the class is actually
 * loaded — Spring reads {@code AutoConfiguration.imports} through ASM metadata and does not load
 * anything at that point. So the check ran against a registry the plugins had not yet contributed
 * to, and rejected every plugin profile as unknown.
 *
 * <p>As an ordinary component it is instantiated in normal bean order, after the auto-configuration
 * classes have been loaded and their registrations made.
 *
 * <p>⚠️ This orders the two events in practice but does not make the ordering a guarantee. If a
 * plugin profile is ever rejected again, the fix is to register it from an
 * {@code EnvironmentPostProcessor} — which runs before the context exists at all — rather than to
 * shuffle bean order again.
 *
 * @author Serge
 */
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SpringChecker {

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
                    "Allowed profiles are: " + SpringHelpersUtils.getPossibleProfiles());
            System.exit(SpringApplication.exit(appCtx, () -> -501));
        }
    }

}
