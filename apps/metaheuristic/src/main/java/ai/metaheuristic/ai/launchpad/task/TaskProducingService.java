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
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
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
            boolean isPersist, Long sourceCodeId, String internalContextId, SourceCodeParamsYaml sourceCodeParams, Long execContextId,
            SourceCodeParamsYaml.Process process, SourceCodeService.ResourcePools pools, List<Long> parentTaskIds) {

        Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls = new HashMap<>(pools.inputStorageUrls);

        SourceCodeService.ProduceTaskResult result = new SourceCodeService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();

        SourceCodeParamsYaml.SnippetDefForSourceCode snDef = process.snippet;
        // start processing of process
        if (process.snippet.context==EnumsApi.SnippetExecContext.external ) {
            Map<String, SourceCodeParamsYaml.Variable> outputResourceIds = new HashMap<>();
            for (SourceCodeParamsYaml.Variable variable : process.output) {
                Variable v = variableService.createUninitialized(variable.name, execContextId, internalContextId);
                // resourceId is an Id of one part of Variable. Variable can contains unlimited number of resources
                String resourceId = v.id.toString();
                outputResourceIds.put(resourceId, variable);
                result.outputResourceCodes.add(resourceId);
                inputStorageUrls.put(resourceId, variable);
            }
            if (isPersist) {
                Task t = createTaskInternal(sourceCodeParams, execContextId, process, outputResourceIds, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
                if (t!=null) {
                    result.taskIds.add(t.getId());
                }
            }

            workbookGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, result.taskIds);

            result.numberOfTasks++;
            if (process.subProcesses!=null && CollectionUtils.isNotEmpty(process.subProcesses.processes)) {
                for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
                    // Right we don't support subProcesses in subProcesses. Need to collect more info about such cases
                    if (subProcess.subProcesses!=null && CollectionUtils.isNotEmpty(subProcess.subProcesses.processes)) {
                        return new SourceCodeService.ProduceTaskResult(EnumsApi.SourceCodeProducingStatus.TOO_MANY_LEVELS_OF_SUBPROCESSES_ERROR);
                    }

                    String ctxId = internalContextId + ','+ idsRepository.save(new Ids()).id;

                    for (SourceCodeParamsYaml.Variable variable : subProcess.output) {
                        Variable v = variableService.createUninitialized(variable.name, execContextId, ctxId);
                        // resourceId is an Id of one part of Variable. Variable can contains unlimited number of resources
                        String resourceId = v.id.toString();
                        outputResourceIds.put(resourceId, variable);
                        result.outputResourceCodes.add(resourceId);
                        inputStorageUrls.put(resourceId, variable);
                    }
                    if (isPersist) {
                        Task t = createTaskInternal(sourceCodeParams, execContextId, process, outputResourceIds, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
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
            List<InternalSnippetOutput> outputs = internalSnippetProcessor.process(snDef.code, sourceCodeId, execContextId, internalContextId, null);

            // theoretically, internal snippet can be without subProcesses, i.e. a result aggregation snippet
            if (true) {
                throw new NotImplementedException("not yet");
            }

            if (process.subProcesses!=null && CollectionUtils.isNotEmpty(process.subProcesses.processes)) {
                for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {


                    result.numberOfTasks++;
                }
            }

        }
        // end processing the main process

        result.status = EnumsApi.SourceCodeProducingStatus.OK;
        return result;
    }

    @SuppressWarnings("Duplicates")
    private TaskImpl createTaskInternal(
            SourceCodeParamsYaml sourceCodeParams, Long workbookId, SourceCodeParamsYaml.Process process,
            Map<String, SourceCodeParamsYaml.Variable> outputResourceIds,
            SourceCodeParamsYaml.SnippetDefForSourceCode snDef, Map<String, List<String>> collectedInputs, Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls,
            Map<String, String> mappingCodeToOriginalFilename) {
        TaskParamsYaml yaml = new TaskParamsYaml();

        collectedInputs.forEach((key, value) -> yaml.taskYaml.inputResourceIds.put(key, value));
        outputResourceIds.forEach((key, value) -> yaml.taskYaml.outputResourceIds.put(value.name, key));

        yaml.taskYaml.realNames = mappingCodeToOriginalFilename;

        // work around with SnakeYaml's refs
        Map<String, SourceCodeParamsYaml.Variable> map = new HashMap<>();
        for (Map.Entry<String, SourceCodeParamsYaml.Variable> entry : inputStorageUrls.entrySet()) {
            final SourceCodeParamsYaml.Variable v = entry.getValue();
            map.put(entry.getKey(), new SourceCodeParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name));
        }
        yaml.taskYaml.resourceStorageUrls = map;

        yaml.taskYaml.snippet = snippetService.getSnippetConfig(snDef);
        if (yaml.taskYaml.snippet==null) {
            log.error("#171.07 Snippet wasn't found for code: {}", snDef.code);
            return null;
        }
        yaml.taskYaml.preSnippets = new ArrayList<>();
        if (process.getPreSnippets()!=null) {
            for (SourceCodeParamsYaml.SnippetDefForSourceCode preSnippet : process.getPreSnippets()) {
                yaml.taskYaml.preSnippets.add(snippetService.getSnippetConfig(preSnippet));
            }
        }
        yaml.taskYaml.postSnippets = new ArrayList<>();
        if (process.getPostSnippets()!=null) {
            for (SourceCodeParamsYaml.SnippetDefForSourceCode postSnippet : process.getPostSnippets()) {
                yaml.taskYaml.postSnippets.add(snippetService.getSnippetConfig(postSnippet));
            }
        }
        yaml.taskYaml.clean = sourceCodeParams.source.clean;
        yaml.taskYaml.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(yaml);

        TaskImpl task = new TaskImpl();
        task.setWorkbookId(workbookId);
        task.setParams(taskParams);
        taskRepository.save(task);

        return task;
    }

}
