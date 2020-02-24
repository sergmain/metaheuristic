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

package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.source_code.ProcessValidator;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExperimentProcessValidator implements ProcessValidator {

    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;
    private final ExecContextCache execContextCache;
    private final FunctionRepository functionRepository;

    // TODO experiment has to be stateless and has its own instances
    // TODO 2019.05.02 do we need an experiment to have its own instance still?
    // TODO 2019.07.04 the current thought is that we don't need stateless experiment
    //      because each experiment has its own set of hyper parameters
    // TODO 2019.08.26 an experiment will be always stateful.
    //      that means that there won't be separated description of experiment and instances of experiment
    // TODO 2020.02.01 experiment as special process will be deleted. There will be only a standard function processing

    @Override
    public EnumsApi.SourceCodeValidateStatus validate(SourceCode sourceCode, SourceCodeParamsYaml.Process process, boolean isFirst) {
        if (StringUtils.isBlank(process.code)) {
            return EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_DEFINED_ERROR;
        }
        Long experimentId = experimentRepository.findIdByCode(process.code);
        if (experimentId==null) {
            return EnumsApi.SourceCodeValidateStatus.EXPERIMENT_NOT_FOUND_ERROR;
        }
        Experiment e = experimentCache.findById(experimentId);
        if (e==null) {
            return EnumsApi.SourceCodeValidateStatus.EXPERIMENT_NOT_FOUND_ERROR;
        }
        if (e.getExecContextId()!=null) {
            ExecContext execContext = execContextCache.findById(e.getExecContextId());
            if (execContext != null) {
                if (!sourceCode.getId().equals(execContext.getSourceCodeId())) {
                    return EnumsApi.SourceCodeValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR;
                }
            }
            else {
                return EnumsApi.SourceCodeValidateStatus.EXEC_CONTEXT_DOESNT_EXIST_ERROR;
            }
        }
        ExperimentParamsYaml epy = e.getExperimentParamsYaml();

        if (StringUtils.isBlank(epy.experimentYaml.fitFunction) || StringUtils.isBlank(epy.experimentYaml.predictFunction)) {
            return EnumsApi.SourceCodeValidateStatus.EXPERIMENT_HASNT_ALL_FUNCTIONS_ERROR;
        }
        Function s = functionRepository.findByCode(epy.experimentYaml.fitFunction);
        if (s==null) {
            return EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR;
        }

        Function predictFunction = functionRepository.findByCode(epy.experimentYaml.fitFunction);
        if (predictFunction ==null) {
            return EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR;
        }
        boolean isFittingDetection = MetaUtils.isTrue(predictFunction.getFunctionConfig(false).metas, ConstsApi.META_MH_FITTING_DETECTION_SUPPORTED);
        if (isFittingDetection) {
            if (S.b(epy.experimentYaml.checkFittingFunction)) {
                return EnumsApi.SourceCodeValidateStatus.FITTING_FUNCTION_NOT_FOUND_ERROR;
            }
            Function fittingFunction = functionRepository.findByCode(epy.experimentYaml.checkFittingFunction);
            if (fittingFunction ==null) {
                return EnumsApi.SourceCodeValidateStatus.FITTING_FUNCTION_NOT_FOUND_ERROR;
            }

        }
        if (!isFirst) {
            if (process.metas == null || process.metas.isEmpty()) {
                return EnumsApi.SourceCodeValidateStatus.EXPERIMENT_META_NOT_FOUND_ERROR;
            }

            Meta m1 = process.getMeta("dataset");
            if (m1 == null || StringUtils.isBlank(m1.getValue())) {
                return EnumsApi.SourceCodeValidateStatus.EXPERIMENT_META_DATASET_NOT_FOUND_ERROR;
            }

            Meta m3 = process.getMeta("feature");
            if (m3 == null || StringUtils.isBlank(m3.getValue())) {
                return EnumsApi.SourceCodeValidateStatus.EXPERIMENT_META_FEATURE_NOT_FOUND_ERROR;
            }
        }
        return null;
    }
}