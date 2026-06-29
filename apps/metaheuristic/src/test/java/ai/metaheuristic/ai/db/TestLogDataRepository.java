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

package ai.metaheuristic.ai.db;
import ai.metaheuristic.ai.MhSharedItTest;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.LogData;
import ai.metaheuristic.ai.dispatcher.repositories.LogDataRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.spi.MhSpi;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestLogDataRepository extends MhSharedItTest {

    @Autowired private LogDataRepository logDataRepository;
    @Autowired private TxSupportForTestingService txSupportForTestingService;

    private @Nullable LogData logData = null;

    @BeforeEach
    public void before() {
        LogData logDataTemp = logDataRepository.findById(42L).orElse(null);
        assertNull(logDataTemp);

        logData = new LogData();
        logData.setLogData("This is log data");
        logData.setType(Enums.LogType.ASSEMBLING);
        logData.setRefId(42L);
        logData.setUpdateTs(new Timestamp(System.currentTimeMillis()));
        logData = txSupportForTestingService.saveLog(logData);
        assertNotNull(logData);
    }

    @AfterEach
    public void after() {
        if (logData!=null) {
            try {
                txSupportForTestingService.deleteLog(logData.getId());
            } catch (EmptyResultDataAccessException e) {
                //
            }
        }
    }

    @Test
    public void testLogData(){

        LogData logDataTemp = logDataRepository.findById(-1L).orElse(null);
        assertNull(logDataTemp);

        assertNotNull(logData);


        LogData datasetWithLogs = logDataRepository.findById(logData.getId()).orElse(null);
        assertNotNull(datasetWithLogs);

        txSupportForTestingService.deleteLog(datasetWithLogs.id);

        LogData newlogData = logDataRepository.findById(datasetWithLogs.getId()).orElse(null);
        assertNull(newlogData);

    }
}
