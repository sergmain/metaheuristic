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

import ai.metaheuristic.ai.launchpad.beans.Ids;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.Variable;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.InternalSnippetOutput;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.InternalSnippetProcessor;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeService;
import ai.metaheuristic.ai.launchpad.repositories.IdsRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.launchpad.variable.VariableService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookGraphTopLevelService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.launchpad.Task;
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
    private final VariableService variableService;
    private final WorkbookGraphTopLevelService workbookGraphTopLevelService;
    private final InternalSnippetProcessor internalSnippetProcessor;
    private final IdsRepository idsRepository;

    @SuppressWarnings("Duplicates")
    public SourceCodeService.ProduceTaskResult produceTasksForProcess(
            boolean isPersist, Long planId, String contextId, PlanParamsYaml planParams, Long workbookId,
            PlanParamsYaml.Process process, SourceCodeService.ResourcePools pools, List<Long> parentTaskIds) {

        Map<String, PlanParamsYaml.Variable> inputStorageUrls = new HashMap<>(pools.inputStorageUrls);

        SourceCodeService.ProduceTaskResult result = new SourceCodeService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();

        PlanParamsYaml.SnippetDefForPlan snDef = process.snippet;
        // start processing of process
        if (process.snippet.context==EnumsApi.SnippetExecContext.external ) {
            Map<String, PlanParamsYaml.Variable> outputResourceIds = new HashMap<>();
            for (PlanParamsYaml.Variable variable : process.output) {
                Variable v = variableService.createUninitialized(variable.name, workbookId, contextId);
                // resourceId is an Id of one part of Variable. Variable can contains unlimited number of resources
                String resourceId = v.id.toString();
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

            workbookGraphTopLevelService.addNewTasksToGraph(workbookId, parentTaskIds, result.taskIds);

            result.numberOfTasks++;
            if (process.subProcesses!=null && CollectionUtils.isNotEmpty(process.subProcesses.processes)) {
                for (PlanParamsYaml.Process subProcess : process.subProcesses.processes) {
                    // Right we don't support subProcesses in subProcesses. Need to collect more info about such cases
                    if (subProcess.subProcesses!=null && CollectionUtils.isNotEmpty(subProcess.subProcesses.processes)) {
                        return new SourceCodeService.ProduceTaskResult(EnumsApi.PlanProducingStatus.TOO_MANY_LEVELS_OF_SUBPROCESSES_ERROR);
                    }

                    String ctxId = contextId + ','+ idsRepository.save(new Ids()).id;

                    for (PlanParamsYaml.Variable variable : subProcess.output) {
                        Variable v = variableService.createUninitialized(variable.name, workbookId, ctxId);
                        // resourceId is an Id of one part of Variable. Variable can contains unlimited number of resources
                        String resourceId = v.id.toString();
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
                    result.numberOfTasks++;
                }
            }
        }
        else {
            // variables will be created while processing of internal snippet
            List<InternalSnippetOutput> outputs = internalSnippetProcessor.process(snDef.code, planId, workbookId, contextId, null);

            // theoretically, internal snippet can be without subProcesses, i.e. a result aggregation snippet
            if (true) {
                throw new NotImplementedException("not yet");
            }

            if (process.subProcesses!=null && CollectionUtils.isNotEmpty(process.subProcesses.processes)) {
                for (PlanParamsYaml.Process subProcess : process.subProcesses.processes) {


                    result.numberOfTasks++;
                }
            }

        }
        // end processing the main process

        result.status = EnumsApi.PlanProducingStatus.OK;
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
        outputResourceIds.forEach((key, value) -> yaml.taskYaml.outputResourceIds.put(value.name, key));

        yaml.taskYaml.realNames = mappingCodeToOriginalFilename;

        // work around with SnakeYaml's refs
        Map<String, PlanParamsYaml.Variable> map = new HashMap<>();
        for (Map.Entry<String, PlanParamsYaml.Variable> entry : inputStorageUrls.entrySet()) {
            final PlanParamsYaml.Variable v = entry.getValue();
            map.put(entry.getKey(), new PlanParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name));
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
