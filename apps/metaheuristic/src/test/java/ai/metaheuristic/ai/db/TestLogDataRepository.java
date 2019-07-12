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

package ai.metaheuristic.ai.db;

import ai.metaheuristic.ai.launchpad.beans.LogData;
import ai.metaheuristic.ai.launchpad.repositories.LogDataRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestLogDataRepository {

    @Autowired
    private LogDataRepository logDataRepository;

    private LogData logData = null;

    @Before
    public void before() {
        logData = new LogData();
        logData.setLogData("This is log data");
        logData.setType(LogData.Type.ASSEMBLING);
        logData.setRefId(42L);
        logData = logDataRepository.saveAndFlush(logData);
        Assert.assertNotNull(logData);
    }

    @After
    public void after() {
        if (logData!=null) {
            try {
                logDataRepository.deleteById(logData.getId());
            } catch (EmptyResultDataAccessException e) {
                //
            }
        }
    }

    @Test
    public void testLogData(){

        LogData logDataTemp = logDataRepository.findById(-1L).orElse(null);
        Assert.assertNull(logDataTemp);


        LogData datasetWithLogs = logDataRepository.findById(logData.getId()).orElse(null);
        Assert.assertNotNull(datasetWithLogs);

        logDataRepository.delete(datasetWithLogs);

        LogData newlogData = logDataRepository.findById(datasetWithLogs.getId()).orElse(null);
        Assert.assertNull(newlogData);

    }
}
