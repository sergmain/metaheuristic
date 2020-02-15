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

package ai.metaheuristic.ai.launchpad.source_code.graph;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.launchpad.beans.Ids;
import ai.metaheuristic.ai.launchpad.data.SourceCodeData;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeService;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;

import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 2/14/2020
 * Time: 10:49 PM
 */
public class SourceCodeGraphLanguageYaml implements SourceCodeGraphLanguage {

    @Override
    public SourceCodeData.SourceCodeGraph parse(String sourceCode, Supplier<String> contextIdSupplier) {

        SourceCodeParamsYaml sourceCodeParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode);

        String internalContextId = contextIdSupplier.get();


        for (SourceCodeParamsYaml.Process process : sourceCodeParams.source.getProcesses()) {
            Monitoring.log("##026", Enums.Monitor.MEMORY);
            SourceCodeService.ProduceTaskResult produceTaskResult = taskProducingService.produceTasksForProcess(isPersist, sourceCode.getId(), contextId, sourceCodeStoredParams, execContextId, process, pools, parentTaskIds);
            Monitoring.log("##027", Enums.Monitor.MEMORY);
            parentTaskIds.clear();
            parentTaskIds.addAll(produceTaskResult.taskIds);

            numberOfTasks += produceTaskResult.numberOfTasks;
            if (produceTaskResult.status != EnumsApi.SourceCodeProducingStatus.OK) {
                return new SourceCodeApiData.TaskProducingResultComplex(produceTaskResult.status);
            }
            Monitoring.log("##030", Enums.Monitor.MEMORY);

            // this part of code replaces the code below
            for (SourceCodeParamsYaml.Variable variable : process.output) {
                pools.add(variable.name, produceTaskResult.outputResourceCodes);
                for (String outputResourceCode : produceTaskResult.outputResourceCodes) {
                    pools.inputStorageUrls.put(outputResourceCode, variable);
                }
            }
/*
            if (process.outputParams.storageType!=null) {
                pools.add(process.outputParams.storageType, produceTaskResult.outputResourceCodes);
                for (String outputResourceCode : produceTaskResult.outputResourceCodes) {
                    pools.inputStorageUrls.put(outputResourceCode, process.outputParams);
                }
            }
*/
            Monitoring.log("##031", Enums.Monitor.MEMORY);
        }

        return null;
    }
}
