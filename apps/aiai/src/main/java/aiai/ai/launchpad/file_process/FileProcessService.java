package aiai.ai.launchpad.file_process;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public Enums.FlowProducingStatus produceTasks(Flow flow, FlowInstance flowInstance, Process process, int idx, String inputResourcePoolCode, String outputResourcePoolCode) {

        List<String> inputResourceCodes = binaryDataService.getResourceCodesInPool(inputResourcePoolCode);

        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {
                createTaskInternal(flow, flowInstance, process, idx, inputResourceCodes, snippetCode);
            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
            createTaskInternal(flow, flowInstance, process, idx, inputResourceCodes, snippetCode);
        }
        return Enums.FlowProducingStatus.OK;
    }

    private void createTaskInternal(Flow flow, FlowInstance flowInstance, Process process, int idx, List<String> inputResourceCodes, String snippetCode) {
        SnippetVersion sv = SnippetVersion.from(snippetCode);
        String outputResource = getResourceCode( flow.code, flow.getId(), process.code, sv.name, idx);

        TaskParamYaml yaml = new TaskParamYaml();
        yaml.setHyperParams( Collections.emptyMap() );
        yaml.inputResourceCodes = inputResourceCodes;

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
                snippet.reportMetrics
        );

        String taskParams = taskParamYamlUtils.toString(yaml);

        Task task = new Task();
        task.setFlowInstanceId(flowInstance.getId());
        task.setOrder(idx);
        task.setParams(taskParams);
        taskRepository.save(task);
    }

    private String getResourceCode(String flowCode, long flowId, String processCode, String snippetName, int idx) {
        return String.format("%s-%d-%d-%s-%s", flowCode, flowId, idx, snippetName, processCode);
    }
}
