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
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
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

    public FlowService.ProduceTaskResult produceTasks(Flow flow, FlowInstance flowInstance, Process process, int idx, List<String> inputResourceCodes) {

        FlowService.ProduceTaskResult result = new FlowService.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();
        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {
                SnippetVersion sv = SnippetVersion.from(snippetCode);
                String outputResourceCode = FlowUtils.getResourceCode(flow.code, flow.getId(), process.code, sv.name, idx);
                result.outputResourceCodes.add(outputResourceCode);
                createTaskInternal(flow, flowInstance, process, idx, inputResourceCodes, outputResourceCode, snippetCode);
            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
            SnippetVersion sv = SnippetVersion.from(snippetCode);
            String outputResourceCode = FlowUtils.getResourceCode(flow.code, flow.getId(), process.code, sv.name, idx);
            result.outputResourceCodes.add(outputResourceCode);
            createTaskInternal(flow, flowInstance, process, idx, inputResourceCodes, outputResourceCode, snippetCode);
        }
        result.status = Enums.FlowProducingStatus.OK;
        return result;
    }

    private void createTaskInternal(Flow flow, FlowInstance flowInstance, Process process, int idx, List<String> inputResourceCodes, String outputResourceCode, String snippetCode) {
        SnippetVersion sv = SnippetVersion.from(snippetCode);

        TaskParamYaml yaml = new TaskParamYaml();
        yaml.setHyperParams( Collections.emptyMap() );
        yaml.inputResourceCodes = inputResourceCodes;
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
                snippet.fileProvided
        );

        String taskParams = taskParamYamlUtils.toString(yaml);

        Task task = new Task();
        task.setFlowInstanceId(flowInstance.getId());
        task.setOrder(idx);
        task.setParams(taskParams);
        taskRepository.save(task);
    }

}
