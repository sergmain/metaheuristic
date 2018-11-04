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

import aiai.ai.core.ArtifactStatus;
import aiai.ai.launchpad.beans.Dataset;
import aiai.ai.launchpad.beans.Feature;
import aiai.ai.launchpad.experiment.dataset.DatasetCache;
import aiai.ai.launchpad.repositories.FeatureRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestFeature_1 {

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private DatasetCache datasetCache;

    private Dataset dataset = null;
    private boolean isCorrectInit = true;

    @PostConstruct
    public void preapre_1() {
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

            Feature dg1 = new Feature();
            dg1.setFeatureOrder(1);
            dg1.setDescription("Test cmd #1. Must be deleted automatically");
            dg1.setFeatureStatus(ArtifactStatus.OK.value);
            dg1.setDataset(dataset);

            Feature dg2 = new Feature();
            dg2.setFeatureOrder(2);
            dg2.setDescription("Test cmd #2. Must be deleted automatically");
            dg2.setFeatureStatus(ArtifactStatus.OK.value);
            dg2.setDataset(dataset);

            Feature dg3 = new Feature();
            dg3.setFeatureOrder(3);
            dg3.setDescription("Test cmd #3. Must be deleted automatically");
            dg3.setFeatureStatus(ArtifactStatus.OK.value);
            dg3.setDataset(dataset);

            dataset.setFeatures(Arrays.asList(dg1, dg2, dg3));

            datasetCache.save(dataset);

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
                datasetCache.delete(dataset.getId());
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
        datasetCache.delete(datasetId);

        List<Feature> features  = featureRepository.findByDataset_Id(datasetId);
        assertNotNull(features);
        assertTrue(features.isEmpty());

    }


}
