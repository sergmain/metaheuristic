/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Globals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static ai.metaheuristic.api.EnumsApi.DispatcherAssetMode.replicated;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@TestPropertySource(locations="classpath:test-dispatcher-profile.properties")
public class TestDispatcherProfile {

    @Autowired
    private Globals globals;

    @Test
    public void simpleTest() {
        assertEquals(12, globals.threadNumber.scheduler);

        assertTrue(globals.dispatcher.enabled);
        assertEquals("qwe321", globals.dispatcher.masterUsername);
        assertEquals("123ewq", globals.dispatcher.masterPassword);
        assertTrue(globals.dispatcher.functionSignatureRequired);
        assertNotNull(globals.dispatcher.dir);
        assertNotNull(globals.dispatcher.dir.dir);
        assertEquals("aiai-dispatcher-123", globals.dispatcher.dir.dir.getName());
        assertNotNull(globals.dispatcher.publicKey);
        assertEquals(12347, globals.dispatcher.timeout.gc.toSeconds());
        assertEquals(replicated, globals.dispatcher.asset.mode);
        assertEquals("http://localhost:33377", globals.dispatcher.asset.sourceUrl);
        assertEquals("1277", globals.dispatcher.asset.password);
        assertEquals("rest_user77", globals.dispatcher.asset.username);
        assertEquals(27, globals.dispatcher.asset.syncTimeout.toSeconds());
        assertEquals(913, globals.dispatcher.chunkSize.toMegabytes());


    }
}
