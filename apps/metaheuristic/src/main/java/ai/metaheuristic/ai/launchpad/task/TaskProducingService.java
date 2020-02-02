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

package ai.metaheuristic.ai.launchpad.task;

import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookGraphTopLevelService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class TaskProducingService {

    private final TaskRepository taskRepository;
    private final SnippetService snippetService;
    private final WorkbookGraphTopLevelService workbookGraphTopLevelService;

    @SuppressWarnings("Duplicates")
    public PlanService.ProduceTaskResult produceTasks(
            boolean isPersist, Long planId, PlanParamsYaml planParams, Long workbookId,
            PlanParamsYaml.Process process, PlanService.ResourcePools pools, List<Long> parentTaskIds) {

        Map<String, PlanParamsYaml.Variable> inputStorageUrls = new HashMap<>(pools.inputStorageUrls);

        PlanService.ProduceTaskResult result = new PlanService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();
        if (true) {
            throw new NotImplementedException("Need to re-write according with latest version of PlanParamYaml");
        }
        if (true) {
/*
        if (process.parallelExec) {
            for (int i = 0; i < process.snippets.size(); i++) {
                PlanParamsYaml.SnippetDefForPlan snDef = process.snippets.get(i);
*/
                PlanParamsYaml.SnippetDefForPlan snDef = process.snippet;
/*
                String normalizedSnippetCode = StrUtils.normalizeCode(snDef.code);
                String normalizedPlanCode = StrUtils.normalizeCode(process.code);
                String outputResourceCode = PlanUtils.getResourceCode(workbookId, normalizedPlanCode, normalizedSnippetCode, process.order, i);
*/
                Map<String, PlanParamsYaml.Variable> outputResourceIds = new HashMap<>();
                for (PlanParamsYaml.Variable variable : process.output) {
                    String resourceId = "1L";
                    outputResourceIds.put(resourceId, variable);
                    result.outputResourceCodes.add(resourceId);
                    inputStorageUrls.put(resourceId, variable);
                }
                if (isPersist) {
                    Task t = createTaskInternal(planParams, workbookId, process, outputResourceIds, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
                    if (t!=null) {
                        result.taskIds.add(t.getId());
                    }
                }
        }
        else {
            PlanParamsYaml.SnippetDefForPlan snDef = process.snippet;
            String normalizedSnippetCode = StrUtils.normalizeCode(snDef.code);
/*
            String normalizedPlanCode = StrUtils.normalizeCode(process.code);
            String outputResourceCode = PlanUtils.getResourceCode(workbookId, normalizedPlanCode, normalizedSnippetCode, process.order, 0);
*/
            Map<String, PlanParamsYaml.Variable> outputResourceIds = new HashMap<>();
            for (PlanParamsYaml.Variable variable : process.output) {
                String resourceId = "1L";
                outputResourceIds.put(resourceId, variable);
                result.outputResourceCodes.add(resourceId);
                inputStorageUrls.put(resourceId, variable);
            }
            if (isPersist) {
                Task t = createTaskInternal(planParams, workbookId, process, outputResourceIds, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
                if (t!=null) {
                    result.taskIds.add(t.getId());
                }
            }
        }
        result.status = EnumsApi.PlanProducingStatus.OK;
        result.numberOfTasks = result.outputResourceCodes.size();

        workbookGraphTopLevelService.addNewTasksToGraph(workbookId, parentTaskIds, result.taskIds);

        return result;
    }

    @SuppressWarnings("Duplicates")
    private TaskImpl createTaskInternal(
            PlanParamsYaml planParams, Long workbookId, PlanParamsYaml.Process process,
            Map<String, PlanParamsYaml.Variable> outputResourceIds,
            PlanParamsYaml.SnippetDefForPlan snDef, Map<String, List<String>> collectedInputs, Map<String, PlanParamsYaml.Variable> inputStorageUrls,
            Map<String, String> mappingCodeToOriginalFilename) {
        TaskParamsYaml yaml = new TaskParamsYaml();

        collectedInputs.forEach((key, value) -> yaml.taskYaml.inputResourceIds.put(key, value));
        outputResourceIds.forEach((key, value) -> yaml.taskYaml.outputResourceIds.put(value.variable, key));

        yaml.taskYaml.realNames = mappingCodeToOriginalFilename;

        // work around with SnakeYaml's refs
        Map<String, PlanParamsYaml.Variable> map = new HashMap<>();
        for (Map.Entry<String, PlanParamsYaml.Variable> entry : inputStorageUrls.entrySet()) {
            final PlanParamsYaml.Variable v = entry.getValue();
            map.put(entry.getKey(), new PlanParamsYaml.Variable(v.sourcing, v.git, v.disk, v.variable));
        }
        yaml.taskYaml.resourceStorageUrls = map;

        yaml.taskYaml.snippet = snippetService.getSnippetConfig(snDef);
        if (yaml.taskYaml.snippet==null) {
            log.error("#171.07 Snippet wasn't found for code: {}", snDef.code);
            return null;
        }
        yaml.taskYaml.preSnippets = new ArrayList<>();
        if (process.getPreSnippets()!=null) {
            for (PlanParamsYaml.SnippetDefForPlan preSnippet : process.getPreSnippets()) {
                yaml.taskYaml.preSnippets.add(snippetService.getSnippetConfig(preSnippet));
            }
        }
        yaml.taskYaml.postSnippets = new ArrayList<>();
        if (process.getPostSnippets()!=null) {
            for (PlanParamsYaml.SnippetDefForPlan postSnippet : process.getPostSnippets()) {
                yaml.taskYaml.postSnippets.add(snippetService.getSnippetConfig(postSnippet));
            }
        }
        yaml.taskYaml.clean = planParams.plan.clean;
        yaml.taskYaml.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(yaml);

        TaskImpl task = new TaskImpl();
        task.setWorkbookId(workbookId);
        task.setParams(taskParams);
        taskRepository.save(task);

        return task;
    }

}
