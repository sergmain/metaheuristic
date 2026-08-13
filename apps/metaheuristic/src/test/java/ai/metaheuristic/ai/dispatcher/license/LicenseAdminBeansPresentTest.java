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

package ai.metaheuristic.ai.dispatcher.license;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhWebItTest;
import ai.metaheuristic.ai.dispatcher.rest.v1.LicenseRestController;
import ai.metaheuristic.commons.spi.license.SignedFileLicenseSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The licence-admin beans are registered under an OFFLINE backend.
 *
 * <p>❗ Worth an explicit test because they are gated by {@code @ConditionalOnBean}, which fails
 * SILENTLY: if the condition is evaluated before {@code SignedFileLicenseSource} is registered, the
 * beans are simply dropped and the admin page 404s at runtime with nothing in the log. Compilation
 * cannot catch it and no other test touches these beans.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class LicenseAdminBeansPresentTest extends MhWebItTest {

    @Autowired
    private ApplicationContext appCtx;

    @Test
    public void test_offlineBackendIsPresent() {
        assertNotNull(appCtx.getBean(SignedFileLicenseSource.class));
    }

    @Test
    public void test_adminBeansAreRegisteredAlongsideIt() {
        // all three inject the concrete SignedFileLicenseSource, so they must appear exactly when
        // it does - and must NOT appear under an external authority, where nothing is installable.
        assertNotNull(appCtx.getBean(LicenseInfoService.class));
        assertNotNull(appCtx.getBean(LicenseArtifactService.class));
        assertNotNull(appCtx.getBean(LicenseRestController.class));
    }
}
