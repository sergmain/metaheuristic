/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProcessorTaskService#recoveryAtLoad}: what a Task loaded from disk at startup needs to complete. The upload of
 * outputs is queued in memory only ({@code UploadVariableService}), so a stop between the delivery of a result and the end
 * of its uploads leaves a Task finished, reported and delivered with an output not uploaded - and nothing queues that
 * upload again. Task #414109 (2026-10-29 15:30:13, core proc-02) is the first shape below, as its task.yaml read.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class ProcessorTaskRecoveryAtLoadTest {

    private static ProcessorCoreTask task(long taskId, boolean finished, boolean reported, boolean delivered, boolean completed,
                                          long[] notUploaded, long[] uploaded) {
        ProcessorCoreTask t = new ProcessorCoreTask();
        t.taskId = taskId;
        t.execContextId = 6528L;
        t.finishedOn = finished ? 1793313013714L : null;
        t.reported = reported;
        t.reportedOn = reported ? 1793313085519L : null;
        t.delivered = delivered;
        t.completed = completed;
        for (long id : notUploaded) {
            t.output.outputStatuses.add(new ProcessorCoreTask.OutputStatus(id, false));
        }
        for (long id : uploaded) {
            t.output.outputStatuses.add(new ProcessorCoreTask.OutputStatus(id, true));
        }
        return t;
    }

    @Test
    public void test_finishedReportedDelivered_outputNotUploaded_task414109() {
        ProcessorCoreTask t = task(414109L, true, true, true, false, new long[]{310786L}, new long[]{});
        ProcessorTaskService.RecoveryAtLoad r = ProcessorTaskService.recoveryAtLoad(t);
        // the result is delivered again and output #310786 is uploaded again
        assertEquals(new ProcessorTaskService.RecoveryAtLoad(true, List.of(310786L)), r, "#414109: reset the delivery, upload #310786 again");
    }

    @Test
    public void test_finishedReportedNotDelivered_outputNotUploaded() {
        ProcessorCoreTask t = task(2L, true, true, false, false, new long[]{20L}, new long[]{});
        ProcessorTaskService.RecoveryAtLoad r = ProcessorTaskService.recoveryAtLoad(t);
        // the result is reported again now, not 60 s later, and the output is uploaded again
        assertEquals(new ProcessorTaskService.RecoveryAtLoad(true, List.of(20L)), r, "reset the delivery, upload #20 again");
    }

    @Test
    public void test_twoOutputs_oneUploaded() {
        ProcessorCoreTask t = task(3L, true, true, true, false, new long[]{31L}, new long[]{30L});
        ProcessorTaskService.RecoveryAtLoad r = ProcessorTaskService.recoveryAtLoad(t);
        // only the output that isn't uploaded is uploaded again
        assertEquals(new ProcessorTaskService.RecoveryAtLoad(true, List.of(31L)), r, "reset the delivery, upload #31 again, not #30");
    }

    @Test
    public void test_completed_isLeftAsIs() {
        ProcessorCoreTask t = task(4L, true, true, true, true, new long[]{}, new long[]{40L});
        assertEquals(ProcessorTaskService.RecoveryAtLoad.NOTHING, ProcessorTaskService.recoveryAtLoad(t),
                "a completed Task has nothing to recover");
    }

    @Test
    public void test_completedAfterAFunctionError_outputNotUploaded_isLeftAsIs() {
        // markAsFinished() completes a Task whose function failed - its outputs are never uploaded, by design
        ProcessorCoreTask t = task(5L, true, true, true, true, new long[]{50L}, new long[]{});
        assertEquals(ProcessorTaskService.RecoveryAtLoad.NOTHING, ProcessorTaskService.recoveryAtLoad(t),
                "a completed Task has nothing to recover, whatever its outputs");
    }

    @Test
    public void test_notFinished_isLeftForRerun() {
        ProcessorCoreTask t = task(6L, false, false, false, false, new long[]{60L}, new long[]{});
        assertEquals(ProcessorTaskService.RecoveryAtLoad.NOTHING, ProcessorTaskService.recoveryAtLoad(t),
                "a Task that didn't finish is re-run (isTaskForRerun), not recovered");
        assertTrue(ProcessorTaskService.isTaskForRerun(t), "precondition: the Task is one for a re-run");
    }

    @Test
    public void test_finished_allUploaded_notCompletedYet_isLeftAsIs() {
        // completeIfAllDone() completes it on the next pass - nothing is missing
        ProcessorCoreTask t = task(7L, true, true, true, false, new long[]{}, new long[]{70L});
        assertEquals(ProcessorTaskService.RecoveryAtLoad.NOTHING, ProcessorTaskService.recoveryAtLoad(t),
                "every output is uploaded - nothing to recover");
    }
}
