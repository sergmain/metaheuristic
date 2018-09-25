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

import aiai.ai.launchpad.beans.*;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.launchpad.experiment.ExperimentService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestFeatureWithSomeOk extends TestFeature {

    @Test
    public void testFeatureCompletionWithPartialError() {
        assertTrue(isCorrectInit);

        checkCurrentState_with10sequences();

        // this station already got sequences, so don't provide any new
        ExperimentService.SequencesAndAssignToStationResult sequences = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE, experiment.getId());
        assertNotNull(sequences);
        // sequences is empty cos we still didn't finish those sequences
        assertTrue(sequences.getSimpleSequences().isEmpty());

        finishCurrentWithError(CommandProcessor.MAX_SEQUENSE_POOL_SIZE);

        ExperimentService.SequencesAndAssignToStationResult sequences1 = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE, experiment.getId());
        assertNotNull(sequences1);
        final ExperimentFeature feature = sequences1.getFeature();
        assertNotNull(feature);
        assertNotNull(sequences1.getSimpleSequences());
        assertEquals(2, sequences1.getSimpleSequences().size());
        assertTrue(feature.isInProgress);

        finishCurrentWithOk(2);

        checkCurrentState_with10sequences();


        System.out.println();
    }

}
