/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 9/27/2020
 * Time: 8:52 PM
 */
@Slf4j
public abstract class PreparingExperiment extends PreparingSourceCode {

    public Experiment experiment = null;

    @BeforeEach
    public void beforePreparingExperiment() {

    }

    public void createExperiment() {
        ExecContextCreatorService.ExecContextCreationResult r = createExecContextForTest();
        assertNull(r.errorMessages, ""+r.errorMessages);

        assertNotNull(r.execContext);
        execContextForTest = r.execContext;

        // Prepare experiment
        experiment = new Experiment();
        experiment.setCode(TEST_EXPERIMENT_CODE_01);

        ExperimentParamsYaml epy = new ExperimentParamsYaml();
        epy.setCode(TEST_EXPERIMENT_CODE_01);
        epy.setName("Test experiment.");
        epy.setDescription("Test experiment. Must be deleted automatically.");

        // set hyper params for experiment
        ExperimentApiData.HyperParam ehp1 = new ExperimentApiData.HyperParam();
        ehp1.setKey("RNN");
        ehp1.setValues("[LSTM, GRU, SimpleRNN]");

        ExperimentApiData.HyperParam ehp2 = new ExperimentApiData.HyperParam();
        ehp2.setKey("batches");
        ehp2.setValues("[20, 40]");

        ExperimentApiData.HyperParam ehp3 = new ExperimentApiData.HyperParam();
        ehp3.setKey("aaa");
        ehp3.setValues("[7, 13]");

        experiment.updateParams(epy);
        experiment.execContextId = execContextForTest.id;

        long mills = System.currentTimeMillis();
        log.info("Start experimentRepository.save()");
        experimentService.updateParamsAndSave(experiment, epy, epy.getName(), epy.description);
        log.info("experimentRepository.save() was finished for {} milliseconds", System.currentTimeMillis() - mills);
    }

    @AfterEach
    public void afterPreparingExperiment() {
        long mills = System.currentTimeMillis();
        log.info("Start afterPreparingExperiment()");
        if (experiment != null) {
            try {
                experimentService.deleteExperiment(experiment.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        log.info("afterPreparingExperiment() was finished for {} milliseconds", System.currentTimeMillis() - mills);
    }

}
