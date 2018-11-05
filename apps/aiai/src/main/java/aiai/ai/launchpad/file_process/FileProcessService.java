package aiai.ai.launchpad.file_process;

import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import org.springframework.stereotype.Service;

@Service
public class FileProcessService {

    private final FlowInstanceRepository flowInstanceRepository;

    public FileProcessService(FlowInstanceRepository flowInstanceRepository) {
        this.flowInstanceRepository = flowInstanceRepository;
    }

    public void produceTasks(Flow flow, Process prevProcess, Process process, int idx) {

        FlowInstance fi = new FlowInstance();
        fi.setFlowId(flow.getId());
        fi.setCompleted(false);
        fi.setCreatedOn( System.currentTimeMillis() );
        flowInstanceRepository.save(fi);

        // output resource code: flow-10-assembly-raw-file-snippet-01
        //
        // input resource:
        // - code: flow-10-assembly-raw-file-snippet-01
        //   type: assembled-raw

        String inputResource;
        if (prevProcess==null) {
            inputResource = flow.inputPoolCode;
        }
        else {
            inputResource = getResourceCode(flow.code, flow.getId(), prevProcess.code, idx);
        }

        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {
                String outputResource = getResourceCode( flow.code, flow.getId(), process.code, idx);

            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
        }

    }

    private String getResourceCode(String flowCode, long flowId, String processCode, int idx) {
        return String.format("%s-%d-%d-%s", flowCode, flowId, idx, processCode);
    }
}
