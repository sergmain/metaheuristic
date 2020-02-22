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

package ai.metaheuristic.ai.binary_data;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Serge
 * Date: 6/6/2019
 * Time: 3:14 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestBinaryDataSaveAndLoad {

    private static final String DATA_FILE_BIN = "data-file.bin";
    private static final String TRG_DATA_FILE_BIN = "trg-data-file.bin";
    private static final String TEST_VARIABLE = "test-variable";

    @Autowired
    private VariableService variableService;

    @Autowired
    private VariableRepository variableRepository;

    private static final int ARRAY_SIZE = 1_000_000;
    private static final Random r = new Random();

    @Before
    public void before() {
        try {
            variableRepository.deleteByName(TEST_VARIABLE);
        } catch (Throwable e) {
            log.error("Error", e);
        }
    }

    @After
    public void after() {
        try {
            variableRepository.deleteByName(TEST_VARIABLE);
        } catch (Throwable th) {
            log.error("Error while deleting test data", th);
        }
    }

    @Test
    public void testSaveAndLoad() throws IOException {

        byte[] bytes = new byte[ARRAY_SIZE];
        r.nextBytes(bytes);

        File tempDir = DirUtils.createTempDir("test-binary-data-");
        File dataFile = new File(tempDir, DATA_FILE_BIN);
        FileUtils.writeByteArrayToFile(dataFile, bytes);

        Variable variable = null;
        try (InputStream is = new FileInputStream(dataFile)) {
            variable = variableService.save(is, dataFile.length(), TEST_VARIABLE, DATA_FILE_BIN, 1L,  "1,2,3");
        }
        assertNotNull(variable);
        assertNotNull(variable.id);

        File trgFile = new File(tempDir, TRG_DATA_FILE_BIN);
        variableService.storeToFile(variable.id.toString(), trgFile);

        assertTrue(FileUtils.contentEquals(dataFile, trgFile));

    }
}