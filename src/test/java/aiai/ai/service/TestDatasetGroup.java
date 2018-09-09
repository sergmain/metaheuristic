/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.service;

import aiai.ai.Globals;
import aiai.ai.beans.Dataset;
import aiai.ai.beans.DatasetGroup;
import aiai.ai.core.ArtifactStatus;
import aiai.ai.repositories.DatasetGroupsRepository;
import aiai.ai.repositories.DatasetRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestDatasetGroup {

    @Autowired
    private Globals globals;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetGroupsRepository datasetGroupsRepository;

    private Dataset dataset = null;
    private boolean isCorrectInit = true;

    @PostConstruct
    public void preapre_1() {
        globals.isUnitTesting = true;
    }

    @Before
    public void before() {
        try {
            // Prepare dataset
            dataset = new Dataset();
            dataset.setName("Test dataset");
            dataset.setDescription("Test dataset. must be deleted automatically");
            dataset.setLocked(true);
            dataset.setEditable(false);

            DatasetGroup dg1 = new DatasetGroup();
            dg1.setGroupNumber(1);
            dg1.setCommand("Test cmd #1. Must be deleted automatically");
            dg1.setFeature(true);
            dg1.setFeatureStatus(ArtifactStatus.OK.value);
            dg1.setDataset(dataset);

            DatasetGroup dg2 = new DatasetGroup();
            dg2.setGroupNumber(2);
            dg2.setCommand("Test cmd #2. Must be deleted automatically");
            dg2.setFeature(true);
            dg2.setFeatureStatus(ArtifactStatus.OK.value);
            dg2.setDataset(dataset);

            DatasetGroup dg3 = new DatasetGroup();
            dg3.setGroupNumber(3);
            dg3.setCommand("Test cmd #3. Must be deleted automatically");
            dg3.setFeature(true);
            dg3.setFeatureStatus(ArtifactStatus.OK.value);
            dg3.setDataset(dataset);

            dataset.setDatasetGroups(Arrays.asList(dg1, dg2, dg3));

            datasetRepository.save(dataset);

            System.out.println("Was inited correctly");
        }
        catch (Throwable th) {
            th.printStackTrace();
            isCorrectInit = false;
        }
    }

    @After
    public void after() {

        if (dataset != null) {
            try {
                datasetRepository.deleteById(dataset.getId());
            }
            catch (EmptyResultDataAccessException e) {
                //
            }
        }


        System.out.println("Was finished correctly");
    }

    @Test
    public void test() {
        assertTrue(isCorrectInit);

        final Long datasetId = dataset.getId();
        datasetRepository.deleteById(datasetId);

        List<DatasetGroup> groups  = datasetGroupsRepository.findByDataset_Id(datasetId);
        assertNotNull(groups);
        assertTrue(groups.isEmpty());

    }


}
