/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 1/6/2022
 * Time: 3:17 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class PreparingExperimentInitService {

    private final ExperimentService experimentService;

    public Experiment createExperiment(ExecContextImpl execContext) {
        // Prepare experiment
        Experiment experiment = new Experiment();
        experiment.setCode(PreparingConsts.TEST_EXPERIMENT_CODE_01);

        ExperimentParamsYaml epy = new ExperimentParamsYaml();
        epy.setCode(PreparingConsts.TEST_EXPERIMENT_CODE_01);
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
        experiment.execContextId = execContext.id;

        long mills = System.currentTimeMillis();
        log.info("Start experimentRepository.save()");
        experimentService.updateParamsAndSave(experiment, epy, epy.getName(), epy.description);
        log.info("experimentRepository.save() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        return experiment;
    }

    public void afterPreparingExperiment(Experiment experiment) {
        try {
            experimentService.deleteExperiment(experiment.getId());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

}
