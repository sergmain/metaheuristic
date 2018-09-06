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
import aiai.ai.beans.*;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.experiment.SimpleSequenceExecResult;
import aiai.ai.launchpad.feature.FeatureExecStatus;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.repositories.*;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestFeatureWithAllError extends TestFeature {

    @Test
    public void testFeatureCompletionWithAllError() {
        assertTrue(isCorrectInit);

        checkForCorrectFinishing_with10sequences();

        // this station already got sequences, so don't provide any new
        ExperimentService.SequencesAndAssignToStationResult sequences = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE);
        assertNotNull(sequences);
        // sequences is empty cos we still didn't finish those sequences
        assertTrue(sequences.getSimpleSequences().isEmpty());

        finishCurrentWithError();

        ExperimentService.SequencesAndAssignToStationResult sequences1 = experimentService.getSequencesAndAssignToStation(station.getId(), CommandProcessor.MAX_SEQUENSE_POOL_SIZE);
        assertNotNull(sequences1);
        final ExperimentFeature feature = sequences1.getFeature();
        assertNotNull(feature);
        assertNotNull(sequences1.getSimpleSequences());
        assertEquals(2, sequences1.getSimpleSequences().size());
        assertTrue(feature.isInProgress);

        finishCurrentWithError();

        checkForCorrectFinishing_withEmpty(feature);
        // 2 more times for checking that state didn't change while next requests
        checkForCorrectFinishing_withEmpty(feature);
        checkForCorrectFinishing_withEmpty(feature);

        System.out.println();
    }

}
