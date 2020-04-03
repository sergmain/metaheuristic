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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.utils.permutation.Permutation;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:17 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndHyperParamsFunction implements InternalFunction {

    private final VariableRepository variableRepository;
    private final GlobalVariableRepository globalVariableRepository;

    @Data
    public static class VariableHolder {
        public SimpleVariableAndStorageUrl variable;
        public GlobalVariable globalVariable;
    }

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION;
    }

    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, String internalContextId, SourceCodeParamsYaml.VariableDefinition variableDefinition,
            TaskParamsYaml taskParamsYaml) {

        if (CollectionUtils.isNotEmpty(taskParamsYaml.task.inputs)) {
            log.warn("List of input variables isn't empty");
        }

        String variableNames = MetaUtils.getValue(taskParamsYaml.task.metas, "variables");
        if (S.b(variableNames)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "Meta 'variable' must be defined and can't be empty");
        }
        String[] names = StringUtils.split(variableNames, ",");

        List<VariableHolder> holders = new ArrayList<>();
        for (String name : names) {
            VariableHolder holder = new VariableHolder();
            holders.add(holder);
            SimpleVariableAndStorageUrl v = variableRepository.findIdByNameAndContextIdAndExecContextId(name, internalContextId, execContextId);
            if (v!=null) {
                holder.variable = v;
            }
            else {
                GlobalVariable gv = globalVariableRepository.findIdByName(name);
                if (gv!=null) {
                    holder.globalVariable = gv;
                }
                else {
                    return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                            "Variable '"+name+"'not found in internal context #"+internalContextId);
                }
            }
        }

        final Permutation<VariableHolder> permutation = new Permutation<>();
        AtomicLong featureId = new AtomicLong(0);
        for (int i = 0; i < holders.size(); i++) {
            permutation.printCombination(holders, i+1,
                    permutedVariables -> {
                        System.out.println(permutedVariables);
/*
                        final String permutedVariablesAsStr = String.valueOf(permutedVariables);
                        final String checksumMD5 = Checksum.getChecksum(EnumsApi.Type.MD5, permutedVariablesAsStr);
                        String checksumIdCodes = StringUtils.substring(permutedVariablesAsStr, 0, 20) + "###" + checksumMD5;
                        if (list.contains(checksumIdCodes)) {
                            // already exist
                            return true;
                        }

                        ExperimentParamsYaml.ExperimentFeature feature = new ExperimentParamsYaml.ExperimentFeature();
                        feature.id = featureId.incrementAndGet();
                        feature.setExperimentId(experiment.id);
                        feature.setVariables(permutedVariablesAsStr);
                        feature.setChecksumIdCodes(checksumIdCodes);
                        epy.processing.features.add(feature);

                        total.incrementAndGet();
*/
                        return true;
                    }
            );
        }

        if (true) {
            throw new NotImplementedException("not yet");
        }
        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

}
