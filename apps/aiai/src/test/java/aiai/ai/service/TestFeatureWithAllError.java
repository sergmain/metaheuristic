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

import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.ai.launchpad.experiment.ExperimentService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestFeatureWithAllError extends TestFeature {

    @Test
    public void testFeatureCompletionWithAllError() {
        assertTrue(isCorrectInit);

        long mills = System.currentTimeMillis();
        log.info("Start checkCurrentState_with10sequences()");
        checkCurrentState_with10sequences();
        log.info("checkCurrentState_with10sequences() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToStation()");
        // this station already got sequences, so don't provide any new
        ExperimentService.SequencesAndAssignToStationResult sequences = experimentService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getId());
        log.info("experimentService.getTaskAndAssignToStation() was finished for {}", System.currentTimeMillis() - mills);
        assertNotNull(sequences);
        // sequences is empty cos we still didn't finish those sequences
        assertTrue(sequences.getSimpleTasks().isEmpty());

        mills = System.currentTimeMillis();
        log.info("Start finishCurrentWithError()");
        finishCurrentWithError(1);
        log.info("finishCurrentWithError() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start experimentService.getTaskAndAssignToStation()");
        ExperimentService.SequencesAndAssignToStationResult sequences1 = experimentService.getTaskAndAssignToStation(
                station.getId(), false, experiment.getId());
        log.info("experimentService.getTaskAndAssignToStation() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(sequences1);
        mills = System.currentTimeMillis();
        log.info("Start sequences1.getFeature()");
        final ExperimentFeature feature = sequences1.getFeature();
        log.info("sequences1.getFeature() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(feature);
        assertNotNull(sequences1.getSimpleTasks());
        assertEquals(2, sequences1.getSimpleTasks().size());
        assertTrue(feature.isInProgress);

        mills = System.currentTimeMillis();
        log.info("Start finishCurrentWithError()");
        finishCurrentWithError(2);
        log.info("finishCurrentWithError() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start checkForCorrectFinishing_withEmpty() #1");
        checkForCorrectFinishing_withEmpty(feature);
        log.info("checkForCorrectFinishing_withEmpty() #1 was finished for {}", System.currentTimeMillis() - mills);

        // 2 more times for checking that state didn't change while next requests
        mills = System.currentTimeMillis();
        log.info("Start checkForCorrectFinishing_withEmpty() #2");
        checkForCorrectFinishing_withEmpty(feature);
        log.info("checkForCorrectFinishing_withEmpty() #2 was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start checkForCorrectFinishing_withEmpty() #3");
        checkForCorrectFinishing_withEmpty(feature);
        log.info("checkForCorrectFinishing_withEmpty() #3 was finished for {}", System.currentTimeMillis() - mills);

        System.out.println();
    }

}
