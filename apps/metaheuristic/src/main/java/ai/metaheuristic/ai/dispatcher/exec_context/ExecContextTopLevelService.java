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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.LaunchpadContext;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextRepository execContextRepository;
    private final ExecContextService execContextService;
    private final SourceCodeCache sourceCodeCache;

    public SourceCodeApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, LaunchpadContext context) {
        return execContextService.getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
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
