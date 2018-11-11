package aiai.ai.launchpad.file_process;

import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.Task;
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

    public FileProcessService(FlowInstanceRepository flowInstanceRepository, SnippetCache snippetCache, TaskParamYamlUtils taskParamYamlUtils, TaskRepository taskRepository) {
        this.flowInstanceRepository = flowInstanceRepository;
        this.snippetCache = snippetCache;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.taskRepository = taskRepository;
    }

    public void produceTasks(Flow flow, FlowInstance flowInstance, Process process, int idx, String inputResourcePoolCode, String outputResourcePoolCode) {

        // output resource code: flow-10-assembly-raw-file-snippet-01
        //
        // input resource:
        // - code: flow-10-assembly-raw-file-snippet-01
        //   type: assembled-raw

        List<String> inputResourceCode = getResourceCodesInPool(inputResourcePoolCode);

        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {
                createTaskInternal(flow, flowInstance, process, idx, inputResourceCode, snippetCode);
            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
            createTaskInternal(flow, flowInstance, process, idx, inputResourceCode, snippetCode);
        }
    }

    private void createTaskInternal(Flow flow, FlowInstance flowInstance, Process process, int idx, List<String> inputResourceCode, String snippetCode) {
        SnippetVersion sv = SnippetVersion.from(snippetCode);
        String outputResource = getResourceCode( flow.code, flow.getId(), process.code, sv.name, idx);

        TaskParamYaml yaml = new TaskParamYaml();
        yaml.setHyperParams( Collections.emptyMap() );
        yaml.inputResourceCodes = inputResourceCode;

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

    private List<String> getResourceCodesInPool(String inputResourcePoolCode) {
        return null;
    }

    private String getResourceCode(String flowCode, long flowId, String processCode, String snippetName, int idx) {
        return String.format("%s-%d-%d-%s-%s", flowCode, flowId, idx, snippetName, processCode);
    }
}
