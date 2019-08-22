/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.file_process;

import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.plan.PlanUtils;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.process.Process;
import ai.metaheuristic.api.launchpad.process.SnippetDefForPlan;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class FileProcessService {

    private final TaskRepository taskRepository;
    private final SnippetService snippetService;
    private final WorkbookService workbookService;
    private final WorkbookCache workbookCache;

    @SuppressWarnings("Duplicates")
    public PlanService.ProduceTaskResult produceTasks(
            boolean isPersist, Long planId, PlanParamsYaml planParams, Long workbookId,
            Process process, PlanService.ResourcePools pools, List<Long> parentTaskIds) {

        Map<String, DataStorageParams> inputStorageUrls = new HashMap<>(pools.inputStorageUrls);

        PlanService.ProduceTaskResult result = new PlanService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();
        if (process.parallelExec) {
            for (int i = 0; i < process.snippets.size(); i++) {
                SnippetDefForPlan snDef = process.snippets.get(i);
                String tempCode = snDef.params != null && !snDef.params.isBlank() ? snDef.code + ' ' + snDef.params : snDef.code;
                String normalizeSnippetCode = StrUtils.normalizeSnippetCode(tempCode);
                String outputResourceCode = PlanUtils.getResourceCode(workbookId, process.code, normalizeSnippetCode, process.order, i);
                result.outputResourceCodes.add(outputResourceCode);
                inputStorageUrls.put(outputResourceCode, process.outputParams);
                if (isPersist) {
                    Task t = createTaskInternal(planParams, workbookId, process, outputResourceCode, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
                    if (t!=null) {
                        result.taskIds.add(t.getId());
                    }
                }
            }
        }
        else {
            SnippetDefForPlan snDef = process.snippets.get(0);
            String tempCode = snDef.params != null && !snDef.params.isBlank() ? snDef.code + ' ' + snDef.params : snDef.code;
            String normalizeSnippetCode = StrUtils.normalizeSnippetCode(tempCode);
            String outputResourceCode = PlanUtils.getResourceCode(workbookId, process.code, normalizeSnippetCode, process.order, 0);
            result.outputResourceCodes.add(outputResourceCode);
            inputStorageUrls.put(outputResourceCode, process.outputParams);
            if (isPersist) {
                Task t = createTaskInternal(planParams, workbookId, process, outputResourceCode, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
                if (t!=null) {
                    result.taskIds.add(t.getId());
                }
            }
        }
        result.status = EnumsApi.PlanProducingStatus.OK;
        result.numberOfTasks = result.outputResourceCodes.size();

        workbookService.addNewTasksToGraph(workbookCache.findById(workbookId), parentTaskIds, result.taskIds);

        return result;
    }

    @SuppressWarnings("Duplicates")
    private TaskImpl createTaskInternal(
            PlanParamsYaml planParams, Long workbookId, Process process,
            String outputResourceCode,
            SnippetDefForPlan snDef, Map<String, List<String>> collectedInputs, Map<String, DataStorageParams> inputStorageUrls,
            Map<String, String> mappingCodeToOriginalFilename) {
        if (process.type!= EnumsApi.ProcessType.FILE_PROCESSING) {
            throw new IllegalStateException("#171.01 Wrong type of process, " +
                    "expected: "+ EnumsApi.ProcessType.FILE_PROCESSING+", " +
                    "actual: " + process.type);
        }
        TaskParamsYaml yaml = new TaskParamsYaml();
        yaml.taskYaml.setHyperParams( Collections.emptyMap() );
        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {
            yaml.taskYaml.inputResourceCodes.put(entry.getKey(), entry.getValue());
        }
        yaml.taskYaml.outputResourceCode = outputResourceCode;
        yaml.taskYaml.realNames = mappingCodeToOriginalFilename;

        // work around with SnakeYaml's refs
        Map<String, DataStorageParams> map = new HashMap<>();
        for (Map.Entry<String, DataStorageParams> entry : inputStorageUrls.entrySet()) {
            final DataStorageParams v = entry.getValue();
            map.put(entry.getKey(), new DataStorageParams(v.sourcing, v.git, v.disk, v.storageType));
        }
        yaml.taskYaml.resourceStorageUrls = map;

        yaml.taskYaml.snippet = snippetService.getSnippetConfig(snDef);
        if (yaml.taskYaml.snippet==null) {
            log.error("#171.07 Snippet wasn't found for code: {}", snDef.code);
            return null;
        }
        yaml.taskYaml.preSnippets = new ArrayList<>();
        if (process.getPreSnippets()!=null) {
            for (SnippetDefForPlan preSnippet : process.getPreSnippets()) {
                yaml.taskYaml.preSnippets.add(snippetService.getSnippetConfig(preSnippet));
            }
        }
        yaml.taskYaml.postSnippets = new ArrayList<>();
        if (process.getPostSnippets()!=null) {
            for (SnippetDefForPlan postSnippet : process.getPostSnippets()) {
                yaml.taskYaml.postSnippets.add(snippetService.getSnippetConfig(postSnippet));
            }
        }
        yaml.taskYaml.clean = planParams.planYaml.clean;
        yaml.taskYaml.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(yaml);

        TaskImpl task = new TaskImpl();
        task.setWorkbookId(workbookId);
        task.setParams(taskParams);
        task.setProcessType(process.type.value);
        taskRepository.save(task);

        return task;
    }

}
