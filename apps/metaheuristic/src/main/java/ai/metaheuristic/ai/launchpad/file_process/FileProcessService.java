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
import ai.metaheuristic.api.v1.data.TaskApiData;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.plan.PlanUtils;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.yaml.task.TaskParamYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.Process;
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
            boolean isPersist, Plan plan, Workbook workbook,
            Process process, PlanService.ResourcePools pools) {

        Map<String, List<String>> collectedInputs = pools.collectedInputs;
        Map<String, DataStorageParams> inputStorageUrls = pools.inputStorageUrls;

        PlanService.ProduceTaskResult result = new PlanService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();
        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {
                String resourceName = StrUtils.normalizeSnippetCode(snippetCode);
                String outputResourceCode = PlanUtils.getResourceCode(plan.getId(), workbook.getId(), process.code, resourceName, process.order);
                result.outputResourceCodes.add(outputResourceCode);
                inputStorageUrls.put(outputResourceCode, process.outputParams);
                if (isPersist) {
                    createTaskInternal(plan, workbook, process, outputResourceCode, snippetCode, collectedInputs, inputStorageUrls);
                }
            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
            String resourceName = StrUtils.normalizeSnippetCode(snippetCode);
            String outputResourceCode = PlanUtils.getResourceCode(plan.getId(), workbook.getId(), process.code, resourceName, process.order);
            result.outputResourceCodes.add(outputResourceCode);
            inputStorageUrls.put(outputResourceCode, process.outputParams);
            if (isPersist) {
                createTaskInternal(plan, workbook, process, outputResourceCode, snippetCode, collectedInputs, inputStorageUrls);
            }
        }
        result.status = EnumsApi.PlanProducingStatus.OK;
        result.numberOfTasks = result.outputResourceCodes.size();
        return result;
    }

    private void createTaskInternal(
            Plan plan, Workbook workbook, Process process,
            String outputResourceCode,
            String snippetCode, Map<String, List<String>> collectedInputs, Map<String, DataStorageParams> inputStorageUrls) {
        if (process.type!= EnumsApi.ProcessType.FILE_PROCESSING) {
            throw new IllegalStateException("#171.01 Wrong type of process, " +
                    "expected: "+ EnumsApi.ProcessType.FILE_PROCESSING+", " +
                    "actual: " + process.type);
        }
        TaskApiData.TaskParamYaml yaml = new TaskApiData.TaskParamYaml();
        yaml.setHyperParams( Collections.emptyMap() );
        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {
            yaml.inputResourceCodes.put(entry.getKey(), entry.getValue());
        }
        yaml.outputResourceCode = outputResourceCode;

        // work around with SnakeYaml's refs
        Map<String, DataStorageParams> map = new HashMap<>();
        for (Map.Entry<String, DataStorageParams> entry : inputStorageUrls.entrySet()) {
            final DataStorageParams v = entry.getValue();
            map.put(entry.getKey(), new DataStorageParams(
                    v.sourcing, v.git, v.disk, v.storageType));
        }
        yaml.resourceStorageUrls = map;

        yaml.snippet = snippetService.getSnippetConfig(snippetCode);
        if (yaml.snippet==null) {
            log.error("#171.07 Snippet wasn't found for code: {}", snippetCode);
            return;
        }
        yaml.preSnippet = snippetService.getSnippetConfig(process.getPreSnippetCode());
        yaml.postSnippet = snippetService.getSnippetConfig(process.getPostSnippetCode());

        yaml.clean = plan.isClean();
        yaml.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String taskParams = TaskParamYamlUtils.toString(yaml);

        TaskImpl task = new TaskImpl();
        task.setWorkbookId(workbook.getId());
        task.setOrder(process.order);
        task.setParams(taskParams);
        task.setProcessType(process.type.value);
        taskRepository.saveAndFlush(task);
    }

}
