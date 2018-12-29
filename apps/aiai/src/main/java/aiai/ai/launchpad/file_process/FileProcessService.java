package aiai.ai.launchpad.file_process;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.flow.FlowUtils;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.task.SimpleSnippet;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
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

    private final FlowInstanceRepository flowInstanceRepository;
    private final SnippetCache snippetCache;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final TaskRepository taskRepository;
    private final BinaryDataService binaryDataService;

    public FileProcessService(FlowInstanceRepository flowInstanceRepository, SnippetCache snippetCache, TaskParamYamlUtils taskParamYamlUtils, TaskRepository taskRepository, BinaryDataService binaryDataService) {
        this.flowInstanceRepository = flowInstanceRepository;
        this.snippetCache = snippetCache;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.taskRepository = taskRepository;
        this.binaryDataService = binaryDataService;
    }

    @SuppressWarnings("Duplicates")
    public FlowService.ProduceTaskResult produceTasks(
            boolean isPersist, Flow flow, FlowInstance flowInstance, Process process,
            Map<String, List<String>> collectedInputs) {

        FlowService.ProduceTaskResult result = new FlowService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();
        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {
                SnippetVersion sv = SnippetVersion.from(snippetCode);
                String outputResourceCode = FlowUtils.getResourceCode(flow.code, flow.getId(), process.code, sv.name, process.order);
                result.outputResourceCodes.add(outputResourceCode);
                if (isPersist) {
                    createTaskInternal(flow, flowInstance, process, outputResourceCode, snippetCode, collectedInputs);
                }
            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
            SnippetVersion sv = SnippetVersion.from(snippetCode);
            String outputResourceCode = FlowUtils.getResourceCode(flow.code, flow.getId(), process.code, sv.name, process.order);
            result.outputResourceCodes.add(outputResourceCode);
            if (isPersist) {
                createTaskInternal(flow, flowInstance, process, outputResourceCode, snippetCode, collectedInputs);
            }
        }
        result.status = Enums.FlowProducingStatus.OK;
        result.numberOfTasks = result.outputResourceCodes.size();
        return result;
    }

    private void createTaskInternal(
            Flow flow, FlowInstance flowInstance, Process process,
            String outputResourceCode,
            String snippetCode, Map<String, List<String>> collectedInputs) {
        if (process.type!= Enums.ProcessType.FILE_PROCESSING) {
            throw new IllegalStateException("Wrong type of process, " +
                    "expected: "+ Enums.ProcessType.FILE_PROCESSING+", " +
                    "actual: " + process.type);
        }
        SnippetVersion sv = SnippetVersion.from(snippetCode);

        TaskParamYaml yaml = new TaskParamYaml();
        yaml.setHyperParams( Collections.emptyMap() );
        for (Map.Entry<String, List<String>> entry : collectedInputs.entrySet()) {
            yaml.inputResourceCodes.put(entry.getKey(), entry.getValue());
        }
        yaml.outputResourceCode = outputResourceCode;

        Snippet snippet = snippetCache.findByNameAndSnippetVersion(sv.name, sv.version);
        if (snippet==null) {
            log.warn("Snippet wasn't found for code: {}", snippetCode);
            return;
        }
        yaml.snippet = new SimpleSnippet(
                snippet.getType(),
                snippetCode,
                snippet.getFilename(),
                snippet.checksum,
                snippet.env,
                snippet.reportMetrics,
                snippet.fileProvided,
                snippet.params
        );
        yaml.clean = flow.clean;

        String taskParams = taskParamYamlUtils.toString(yaml);

        Task task = new Task();
        task.setFlowInstanceId(flowInstance.getId());
        task.setOrder(process.order);
        task.setParams(taskParams);
        task.setProcessType(process.type.value);
        taskRepository.save(task);
    }

}
