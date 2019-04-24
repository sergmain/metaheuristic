/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.file_process;

import aiai.ai.Consts;
import aiai.ai.launchpad.beans.*;
import aiai.apps.commons.utils.StrUtils;
import aiai.api.v1.launchpad.Process;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.flow.FlowUtils;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.api.v1.EnumsApi;
import aiai.apps.commons.yaml.snippet.SnippetConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("launchpad")
public class FileProcessService {

    private final TaskRepository taskRepository;
    private final SnippetRepository snippetRepository;

    public FileProcessService(TaskRepository taskRepository, SnippetRepository snippetRepository) {
        this.taskRepository = taskRepository;
        this.snippetRepository = snippetRepository;
    }

    @SuppressWarnings("Duplicates")
    public FlowService.ProduceTaskResult produceTasks(
            boolean isPersist, Flow flow, FlowInstance flowInstance,
            Process process, FlowService.ResourcePools pools) {

        Map<String, List<String>> collectedInputs = pools.collectedInputs;
        Map<String, String> inputStorageUrls = pools.inputStorageUrls;

        FlowService.ProduceTaskResult result = new FlowService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();
        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {
                String resourceName = StrUtils.normalizeSnippetCode(snippetCode);
                String outputResourceCode = FlowUtils.getResourceCode(flow.getId(), flowInstance.getId(), process.code, resourceName, process.order);
                result.outputResourceCodes.add(outputResourceCode);
                inputStorageUrls.put(outputResourceCode,
                        StringUtils.isBlank(process.outputStorageUrl) ? Consts.LAUNCHPAD_STORAGE_URL : process.outputStorageUrl);
                if (isPersist) {
                    createTaskInternal(flow, flowInstance, process, outputResourceCode, snippetCode, collectedInputs, inputStorageUrls);
                }
            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
            String resourceName = StrUtils.normalizeSnippetCode(snippetCode);
            String outputResourceCode = FlowUtils.getResourceCode(flow.getId(), flowInstance.getId(), process.code, resourceName, process.order);
            result.outputResourceCodes.add(outputResourceCode);
            inputStorageUrls.put(outputResourceCode,
                    StringUtils.isBlank(process.outputStorageUrl) ? Consts.LAUNCHPAD_STORAGE_URL : process.outputStorageUrl);
            if (isPersist) {
                createTaskInternal(flow, flowInstance, process, outputResourceCode, snippetCode, collectedInputs, inputStorageUrls);
            }
        }
        result.status = EnumsApi.FlowProducingStatus.OK;
        result.numberOfTasks = result.outputResourceCodes.size();
        return result;
    }

    private void createTaskInternal(
            Flow flow, FlowInstance flowInstance, Process process,
            String outputResourceCode,
            String snippetCode, Map<String, List<String>> collectedInputs, Map<String, String> inputStorageUrls) {
        if (process.type!= EnumsApi.ProcessType.FILE_PROCESSING) {
            throw new IllegalStateException("#171.01 Wrong type of process, " +
                    "expected: "+ EnumsApi.ProcessType.FILE_PROCESSING+", " +
                    "actual: " + process.type);
        }
        TaskParamYaml yaml = new TaskParamYaml();
        yaml.setHyperParams( Collections.emptyMap() );
        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {
            yaml.inputResourceCodes.put(entry.getKey(), entry.getValue());
        }
        yaml.outputResourceCode = outputResourceCode;
        yaml.resourceStorageUrls = inputStorageUrls;

        Snippet snippet = snippetRepository.findByCode(snippetCode);
        if (snippet==null) {
            log.error("#171.07 Snippet wasn't found for code: {}", snippetCode);
            return;
        }
        yaml.snippet = SnippetConfigUtils.to(snippet.params);
        yaml.clean = flow.clean;
        yaml.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String taskParams = TaskParamYamlUtils.toString(yaml);

        TaskImpl task = new TaskImpl();
        task.setFlowInstanceId(flowInstance.getId());
        task.setOrder(process.order);
        task.setParams(taskParams);
        task.setProcessType(process.type.value);
        taskRepository.save(task);
    }

}
