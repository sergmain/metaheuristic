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
import ai.metaheuristic.ai.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.api.launchpad.process.Process;
import ai.metaheuristic.api.launchpad.process.SnippetDefForPlan;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@Profile("launchpad")
public class FileProcessService {

    private final TaskRepository taskRepository;
    private final SnippetService snippetService;

    public FileProcessService(TaskRepository taskRepository, SnippetService snippetService) {
        this.taskRepository = taskRepository;
        this.snippetService = snippetService;
    }

    @SuppressWarnings("Duplicates")
    public PlanService.ProduceTaskResult produceTasks(
            boolean isPersist, Long planId, PlanParamsYaml planParams, Workbook workbook,
            Process process, PlanService.ResourcePools pools) {

        Map<String, List<String>> collectedInputs = pools.collectedInputs;
        Map<String, DataStorageParams> inputStorageUrls = pools.inputStorageUrls;

        PlanService.ProduceTaskResult result = new PlanService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();
        if (process.parallelExec) {
            for (SnippetDefForPlan snDef : process.snippets) {
                String resourceName = StrUtils.normalizeSnippetCode(snDef.code);
                String outputResourceCode = PlanUtils.getResourceCode(planId, workbook.getId(), process.code, resourceName, process.order);
                result.outputResourceCodes.add(outputResourceCode);
                inputStorageUrls.put(outputResourceCode, process.outputParams);
                if (isPersist) {
                    createTaskInternal(planParams, workbook, process, outputResourceCode, snDef, collectedInputs, inputStorageUrls);
                }
            }
        }
        else {
            SnippetDefForPlan snDef = process.snippets.get(0);
            String resourceName = StrUtils.normalizeSnippetCode(snDef.code);
            String outputResourceCode = PlanUtils.getResourceCode(planId, workbook.getId(), process.code, resourceName, process.order);
            result.outputResourceCodes.add(outputResourceCode);
            inputStorageUrls.put(outputResourceCode, process.outputParams);
            if (isPersist) {
                createTaskInternal(planParams, workbook, process, outputResourceCode, snDef, collectedInputs, inputStorageUrls);
            }
        }
        result.status = EnumsApi.PlanProducingStatus.OK;
        result.numberOfTasks = result.outputResourceCodes.size();
        return result;
    }

    @SuppressWarnings("Duplicates")
    private void createTaskInternal(
            PlanParamsYaml planParams, Workbook workbook, Process process,
            String outputResourceCode,
            SnippetDefForPlan snDef, Map<String, List<String>> collectedInputs, Map<String, DataStorageParams> inputStorageUrls) {
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
            return;
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
        task.setWorkbookId(workbook.getId());
        task.setOrder(process.order);
        task.setParams(taskParams);
        task.setProcessType(process.type.value);
        taskRepository.saveAndFlush(task);
    }

}
