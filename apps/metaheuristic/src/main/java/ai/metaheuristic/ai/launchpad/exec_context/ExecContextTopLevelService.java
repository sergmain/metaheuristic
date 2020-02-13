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

package ai.metaheuristic.ai.launchpad.exec_context;

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeUtils;
import ai.metaheuristic.ai.launchpad.repositories.ExecContextRepository;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextRepository execContextRepository;
    private final ExecContextService execContextService;
    private final SourceCodeCache sourceCodeCache;

    public SourceCodeApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, LaunchpadContext context) {
        return execContextService.getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
    }

    public SourceCodeApiData.TaskProducingResult createExecContext(Long sourceCodeId, String inputResourceParam, LaunchpadContext context) {
        final SourceCodeApiData.TaskProducingResultComplex result = execContextService.createExecContext(sourceCodeId, SourceCodeUtils.parseToExecContextParamsYaml(inputResourceParam));
        return new SourceCodeApiData.TaskProducingResult(
                result.getStatus()== EnumsApi.TaskProducingStatus.OK
                        ? new ArrayList<>()
                        : List.of("Error of creating execContext, " +
                        "validation status: " + result.getSourceCodeValidateStatus()+", producing status: " + result.getSourceCodeProducingStatus()),
                result.sourceCodeValidateStatus,
                result.sourceCodeProducingStatus,
                result.execContext.getId()
        );
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtendedForDeletion(Long execContextId, LaunchpadContext context) {
        SourceCodeApiData.ExecContextResult result = execContextService.getExecContextExtended(execContextId);

        // don't show actual graph for this execContext
        ExecContextParamsYaml wpy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(result.execContext.getParams());
        wpy.graph = null;
        result.execContext.setParams( ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(wpy) );

        return result;
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        //noinspection UnnecessaryLocalVariable
        SourceCodeApiData.ExecContextResult result = execContextService.getExecContextExtended(execContextId);
        return result;
    }

}
