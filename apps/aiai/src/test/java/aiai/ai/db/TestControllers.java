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

import aiai.ai.launchpad.beans.LogData;
import aiai.ai.launchpad.beans.Dataset;
import aiai.ai.launchpad.repositories.DatasetRepository;
import aiai.ai.launchpad.repositories.LogDataRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestControllers {


    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private LogDataRepository logDataRepository;

    @Test
    public void testDataset() {

        Optional<Dataset> datasetOptional = datasetRepository.findById(-1L);
        Assert.assertFalse(datasetOptional.isPresent());

        Dataset ds = new Dataset();
        ds.setDescription("Dataset for testing");
        ds.setEditable(false);
        ds.setName("ds #42");

        Dataset newDataset= datasetRepository.save(ds);
        Assert.assertNotNull(newDataset);

        datasetOptional = datasetRepository.findById(newDataset.getId());
        Assert.assertTrue(datasetOptional.isPresent());

        Dataset datasetWithLogs = datasetOptional.get();

        datasetRepository.delete(datasetWithLogs);

        datasetOptional = datasetRepository.findById(newDataset.getId());
        Assert.assertFalse(datasetOptional.isPresent());

    }

    @Test
    public void testLogData(){

        LogData logData = logDataRepository.findById(-1L).orElse(null);
        Assert.assertNull(logData);

        LogData logData1 = new LogData();
        logData1.setLogData("This is log data");
        logData1.setType(LogData.Type.ASSEMBLING);
        logData1.setRefId(42L);
        LogData newlogData = logDataRepository.save(logData1);
        Assert.assertNotNull(newlogData);

        LogData datasetWithLogs = logDataRepository.findById(newlogData.getId()).orElse(null);
        Assert.assertNotNull(datasetWithLogs);

        logDataRepository.delete(datasetWithLogs);

        logData1 = logDataRepository.findById(newlogData.getId()).orElse(null);
        Assert.assertNull(logData1);

    }
}
