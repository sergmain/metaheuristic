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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeUtils;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
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
public class WorkbookTopLevelService {

    private final WorkbookRepository workbookRepository;
    private final WorkbookService workbookService;
    private final SourceCodeCache sourceCodeCache;

    public SourceCodeApiData.ExecContextsResult getWorkbooksOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, LaunchpadContext context) {
        return workbookService.getWorkbooksOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
    }

    public SourceCodeApiData.TaskProducingResult createExecContext(Long sourceCodeId, String inputResourceParam, LaunchpadContext context) {
        final SourceCodeApiData.TaskProducingResultComplex result = workbookService.createWorkbook(sourceCodeId, SourceCodeUtils.parseToWorkbookParamsYaml(inputResourceParam));
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

    public SourceCodeApiData.ExecContextResult getExecContextExtendedForDeletion(Long workbookId, LaunchpadContext context) {
        SourceCodeApiData.ExecContextResult result = workbookService.getWorkbookExtended(workbookId);

        // don't show actual graph for this execContext
        WorkbookParamsYaml wpy = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(result.execContext.getParams());
        wpy.graph = null;
        result.execContext.setParams( WorkbookParamsYamlUtils.BASE_YAML_UTILS.toString(wpy) );

        return result;
    }

    public SourceCodeApiData.ExecContextResult getWorkbookExtended(Long execContextId) {
        //noinspection UnnecessaryLocalVariable
        SourceCodeApiData.ExecContextResult result = workbookService.getWorkbookExtended(execContextId);
        return result;
    }

}
