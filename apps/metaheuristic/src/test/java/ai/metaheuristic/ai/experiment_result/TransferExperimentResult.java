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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 4/27/2020
 * Time: 5:33 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
public class TransferExperimentResult {

    @Autowired
    public ExperimentResultService experimentResultService;

    @Test
    public void test() {

        TaskParamsYaml taskParamsYaml = new TaskParamsYaml();

//        - key: feature-item
//          value: var-feature-item
//        - key: inline-permutation
//          value: var-inline-permutation
//        - key: metrics
//          value: var-metrics
//        - key: predicted
//          value: var-predicted
//        - key: inline-key
//          value: mh.hyper-params
//        - key: permute-inline
//          value: true
        taskParamsYaml.task.metas.add(new Meta("feature-item", "var-feature-item", null));
        taskParamsYaml.task.metas.add(new Meta("inline-permutation", "var-inline-permutation", null));
        taskParamsYaml.task.metas.add(new Meta("metrics", "var-metrics", null));
        taskParamsYaml.task.metas.add(new Meta("predicted", "var-predicted", null));
        taskParamsYaml.task.metas.add(new Meta("inline-key", "mh.hyper-params", null));
        taskParamsYaml.task.metas.add(new Meta("permute-inline", "true", null));

        ExecContextParamsYaml.VariableDeclaration variableDeclaration = new ExecContextParamsYaml.VariableDeclaration();
//        seed: '42'
//        batch_size: '[20]'
//        time_steps: '[30]'
//        RNN: '[GRU]'
//        epoch: '10'
//        activation: '[relu]'
//        optimizer: '[rmsprop]'
        variableDeclaration.inline.put("mh.hyper-params", Map.of(
                "seed", "42",
                "batch_size", "[20]",
                "time_steps", "[30]",
                "RNN", "[GRU]",
                "epoch", "[relu]",
                "optimizer", "[rmsprop]"
        ));

        Long execContextId = 1019L;

        OperationStatusRest status = experimentResultService.storeExperimentToExperimentResult(execContextId, taskParamsYaml, variableDeclaration);
        System.out.println("status: " + status);

    }
}