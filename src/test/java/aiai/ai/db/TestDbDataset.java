/*
 * AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.db;

import aiai.ai.beans.LogData;
import aiai.ai.launchpad.dataset.Dataset;
import aiai.ai.repositories.DatasetsRepository;
import aiai.ai.repositories.LogDataRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

@RunWith(SpringRunner.class)
@SpringBootTest
//@Import({SpringSecurityWebAuxTestConfig.class, AuthenticationProviderForTests.class, TestRest.JsonTestController.class})
public class TestDbDataset {


    @Autowired
    private DatasetsRepository datasetsRepository;

    @Autowired
    private LogDataRepository logDataRepository;

    @Test
    public void testDataset() {

        Optional<Dataset> datasetOptional = datasetsRepository.findById(-1L);
        Assert.assertFalse(datasetOptional.isPresent());

        Dataset ds = new Dataset();
        ds.setDescription("Dataset for testing");
        ds.setEditable(false);

        Dataset newDataset= datasetsRepository.save(ds);
        Assert.assertNotNull(newDataset);

        datasetOptional = datasetsRepository.findById(newDataset.getId());
        Assert.assertTrue(datasetOptional.isPresent());

        Dataset datasetWithLogs = datasetOptional.get();

        datasetsRepository.delete(datasetWithLogs);

        datasetOptional = datasetsRepository.findById(newDataset.getId());
        Assert.assertFalse(datasetOptional.isPresent());

    }

    @Test
    public void testLogData(){

        Optional<LogData> logDataOptional = logDataRepository.findById(-1L);
        Assert.assertFalse(logDataOptional.isPresent());

        LogData logData = new LogData();
        logData.setLogData("This is log data");
        logData.setType(LogData.Type.ASSEMBLY);

        LogData newlogData = logDataRepository.save(logData);
        Assert.assertNotNull(newlogData);

        logDataOptional = logDataRepository.findById(newlogData.getId());
        Assert.assertTrue(logDataOptional.isPresent());

        LogData datasetWithLogs = logDataOptional.get();

        logDataRepository.delete(datasetWithLogs);

        logDataOptional = logDataRepository.findById(newlogData.getId());
        Assert.assertFalse(logDataOptional.isPresent());

    }
}
