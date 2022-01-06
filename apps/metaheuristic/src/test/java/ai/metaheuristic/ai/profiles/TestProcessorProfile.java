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
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("processor")
@TestPropertySource(locations="classpath:test-processor-profile.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestProcessorProfile {

    @Autowired
    private Globals globals;

    @Test
    public void simpleTest() {
        assertEquals(13, globals.threadNumber.getScheduler());
        assertFalse(globals.dispatcher.enabled);
        assertTrue(globals.processor.enabled);

        assertNotNull(globals.processor.dir);
        assertNotNull(globals.processor.dir.dir);
        assertEquals("aiai-processor", globals.processor.dir.dir.getName());
        assertEquals(1717, globals.processor.taskConsoleOutputMaxLines);

        assertEquals(1721, globals.processor.timeout.requestDispatcher.toSeconds());
        assertEquals(1723, globals.processor.timeout.taskAssigner.toSeconds());
        assertEquals(1725, globals.processor.timeout.taskProcessor.toSeconds());

        assertEquals(1727, globals.processor.timeout.downloadFunction.toSeconds());
        assertEquals(1729, globals.processor.timeout.prepareFunctionForDownloading.toSeconds());
        assertEquals(1731, globals.processor.timeout.downloadResource.toSeconds());

        assertEquals(1733, globals.processor.timeout.uploadResultResource.toSeconds());
        assertEquals(1735, globals.processor.timeout.dispatcherContextInfo.toSeconds());
        assertEquals(1737, globals.processor.timeout.artifactCleaner.toSeconds());

        assertEquals(Globals.SECONDS_10.toSeconds(), globals.processor.timeout.getRequestDispatcher().toSeconds());
        assertEquals(Globals.SECONDS_5.toSeconds(), globals.processor.timeout.getTaskAssigner().toSeconds());
        assertEquals(Globals.SECONDS_9.toSeconds(), globals.processor.timeout.getTaskProcessor().toSeconds());
        
        assertEquals(Globals.SECONDS_11.toSeconds(), globals.processor.timeout.getDownloadFunction().toSeconds());
        assertEquals(Globals.SECONDS_31.toSeconds(), globals.processor.timeout.getPrepareFunctionForDownloading().toSeconds());
        assertEquals(Globals.SECONDS_3.toSeconds(), globals.processor.timeout.getDownloadResource().toSeconds());
        
        assertEquals(Globals.SECONDS_3.toSeconds(), globals.processor.timeout.getUploadResultResource().toSeconds());
        assertEquals(Globals.SECONDS_19.toSeconds(), globals.processor.timeout.getDispatcherContextInfo().toSeconds());
        assertEquals(Globals.SECONDS_29.toSeconds(), globals.processor.timeout.getArtifactCleaner().toSeconds());
        
    }
}
