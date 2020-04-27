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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.experiment.ExperimentTopLevelService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/v1/dispatcher/experiment")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class ExperimentRestController {

    private final ExperimentTopLevelService experimentTopLevelService;
    private final ExecContextService execContextService;
    private final UserContextService userContextService;

    @GetMapping("/experiments")
    public ExperimentApiData.ExperimentsResult getExperiments(@PageableDefault(size = 5) Pageable pageable) {
        return experimentTopLevelService.getExperiments(pageable);
    }

    @GetMapping(value = "/experiment/{id}")
    public ExperimentApiData.ExperimentResult getExperiment(@PathVariable Long id) {
        return experimentTopLevelService.getExperimentWithoutProcessing(id);
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public ExperimentApiData.ExperimentsEditResult edit(@PathVariable Long id) {
        return experimentTopLevelService.editExperiment(id);
    }

    @PostMapping("/experiment-add-commit")
    public OperationStatusRest addFormCommit(String sourceCodeUid, String name, String code, String description, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return experimentTopLevelService.addExperimentCommit(sourceCodeUid, name, code, description, context);
    }

    @PostMapping("/experiment-edit-commit")
    public OperationStatusRest editFormCommit(@RequestBody ExperimentApiData.SimpleExperiment simpleExperiment) {
        return experimentTopLevelService.editExperimentCommit(simpleExperiment);
    }

    @PostMapping("/experiment-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return experimentTopLevelService.experimentDeleteCommit(id);
    }

    @PostMapping("/experiment-clone-commit")
    public OperationStatusRest experimentCloneCommit(Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return experimentTopLevelService.experimentCloneCommit(id, context);
    }

    @PostMapping("/task-rerun/{taskId}")
    public OperationStatusRest rerunTask(@PathVariable Long taskId) {
        return execContextService.resetTask(taskId);
    }

    @PostMapping("/produce-tasks")
    public OperationStatusRest produceTasks(String experimentCode, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return experimentTopLevelService.produceTasks(experimentCode, context.getCompanyId());
    }

    @PostMapping("/start-processing-of-tasks")
    public OperationStatusRest startProcessingOfTasks(String experimentCode, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return experimentTopLevelService.startProcessingOfTasks(experimentCode, context.getCompanyId());
    }

    @GetMapping("/processing-status/{experimentCode}")
    public EnumsApi.ExecContextState getExperimentProcessingStatus(@PathVariable String experimentCode) {
        return experimentTopLevelService.getExperimentProcessingStatus(experimentCode);
    }

    @GetMapping(value = "/experiment-to-experiment-result/{id}")
    public OperationStatusRest toExperimentResult(@PathVariable Long id) {
        return experimentTopLevelService.toExperimentResult(id);
    }

}
