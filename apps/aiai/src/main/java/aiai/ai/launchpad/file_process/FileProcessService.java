package aiai.ai.launchpad.file_process;

import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.StationsRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
public class FileProcessService {

    public void produceTasks(Flow flow, Process prevProcess, Process process, int idx) {

        // output resource code: flow-10-assembly-raw-file-snippet-01
        //
        // input resource:
        // - code: flow-10-assembly-raw-file-snippet-01
        //   type: assembled-raw

        if (process.parallelExec) {
            for (String snippetCode : process.snippetCodes) {

            }
        }
        else {
            String snippetCode = process.snippetCodes.get(0);
        }

    }
}
