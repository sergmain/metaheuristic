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

package ai.metaheuristic.ai.profiles;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.api.ConstsApi;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static ai.metaheuristic.api.EnumsApi.DispatcherAssetMode.replicated;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
// DO NOT REMOVE THIS ACTIVE PROFILE
@ActiveProfiles("dispatcher")
@TestPropertySource(locations="classpath:test-dispatcher-profile.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
@Disabled
public class TestDispatcherProfile {

    @Autowired
    private Globals globals;

    @Test
    public void simpleTest() {
//        assertEquals(12, globals.threadNumber.getScheduler());
        assertEquals(List.of("http://localhost", "https://127.0.0.1", "http://192.168.0.1"), globals.corsAllowedOrigins);

        assertTrue(globals.dispatcher.enabled);
        assertFalse(globals.security.sslRequired);
        assertEquals("qwe321", globals.dispatcher.masterUsername);
        assertEquals("123ewq", globals.dispatcher.masterPassword);
        assertSame(globals.function.securityCheck, Enums.FunctionSecurityCheck.always);
        assertNotNull(globals.dispatcherPath);
        assertEquals(Consts.DISPATCHER_DIR, globals.dispatcherPath.getFileName().toString());

        assertNotNull(globals.publicKeyStore.key);
        assertEquals(1, globals.publicKeyStore.key.length);
        assertEquals(Consts.DEFAULT_PUBLIC_KEY_CODE, globals.publicKeyStore.key[0].code);

        assertEquals(replicated, globals.dispatcher.asset.mode);
        assertEquals("http://localhost:33377", globals.dispatcher.asset.sourceUrl);
        assertEquals("1277", globals.dispatcher.asset.password);
        assertEquals("rest_user77", globals.dispatcher.asset.username);
        assertEquals(27, globals.dispatcher.asset.syncTimeout.toSeconds());
        assertEquals(913, globals.dispatcher.chunkSize.toMegabytes());

        assertEquals(12347, globals.dispatcher.timeout.gc.toSeconds());
        assertEquals(12345, globals.dispatcher.timeout.artifactCleaner.toSeconds());
        assertEquals(12343, globals.dispatcher.timeout.updateBatchStatuses.toSeconds());
        assertEquals(8, globals.dispatcher.timeout.batchDeletion.toDays());

        assertEquals(ConstsApi.SECONDS_300.toSeconds(), globals.dispatcher.timeout.getArtifactCleaner().toSeconds());
        assertEquals(ConstsApi.SECONDS_23.toSeconds(), globals.dispatcher.timeout.getUpdateBatchStatuses().toSeconds());
        assertEquals(ConstsApi.DAYS_14.toDays(), globals.dispatcher.timeout.getBatchDeletion().toDays());

    }
}
