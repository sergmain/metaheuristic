/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.experiment_result;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskParamsYaml;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 4/27/2020
 * Time: 5:33 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TransferExperimentResult {

    @Autowired
    public ExperimentResultService experimentResultService;

    @Autowired
    public ExecContextCache execContextCache;

    @Test
    @Disabled("This method is't test actually and is used for transfer an actual result of experiment, that's why it is disabled")
    public void test() {


        TaskParamsYaml taskParamsYaml = new TaskParamsYaml();
//        - feature-item: var-feature-item
//        - inline-permutation: var-inline-permutation
//        - metrics: var-metrics
//        - predicted: var-predicted
//        - inline-key: mh.hyper-params
//        - permute-inline: true
        taskParamsYaml.task.metas.add(Map.of("variables-for-permutation", "var-feature-item"));
        taskParamsYaml.task.metas.add(Map.of("inline-permutation", "var-inline-permutation"));
        taskParamsYaml.task.metas.add(Map.of("metrics", "var-metrics"));
        taskParamsYaml.task.metas.add(Map.of("predicted", "var-predicted"));
        taskParamsYaml.task.metas.add(Map.of("inline-key", "mh.hyper-params"));
        taskParamsYaml.task.metas.add(Map.of("permute-inline", "true"));

        Long execContextId = 1020L;
        final ExecContextImpl execContext = execContextCache.findById(execContextId);
        assertNotNull(execContext);

        if (true) {
            throw new IllegalStateException("experimentResultService.storeExperimentToExperimentResult must be in transaction");
        }
        OperationStatusRest status = experimentResultService.storeExperimentToExperimentResult(execContext, taskParamsYaml);
        System.out.println("status: " + status);

    }
}
