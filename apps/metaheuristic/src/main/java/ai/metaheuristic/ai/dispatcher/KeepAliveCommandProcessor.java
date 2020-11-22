/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 11/22/2020
 * Time: 12:24 AM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class KeepAliveCommandProcessor  {

    private final TaskService taskService;
    private final ProcessorTopLevelService processorTopLevelService;
    private final FunctionService functionService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ProcessorTransactionService processorService;
    private final TaskProviderService taskProviderService;
    private final ProcessorCache processorCache;

    public void process(KeepAliveRequestParamYaml request, KeepAliveResponseParamYaml response) {
        processProcessorTaskStatus(request);
        response.assignedProcessorId = getNewProcessorId(request.requestProcessorId);
        response.functions.infos.addAll( functionService.getFunctionInfos() );
    }

    // processing at dispatcher side
    private void processProcessorTaskStatus(KeepAliveRequestParamYaml request) {
        if (request.processorCommContext==null) {
            log.warn("#997.020 (request.processorCommContext==null)");
            return;
        }
        if (true) throw new NotImplementedException("need to decide what to do with reconcileProcessorTasks() below");
        processorTopLevelService.setTaskIds(request.processorCommContext.processorId, request.taskIds);
//        processorTopLevelService.reconcileProcessorTasks(request.processorCommContext.processorId, request.reportProcessorTaskStatus.statuses);
    }

    // processing at dispatcher side
    private void processReportProcessorStatus(KeepAliveRequestParamYaml request, KeepAliveResponseParamYaml response) {
/*
        if (request.reportProcessorStatus==null) {
            return;
        }
        if (request.processorCommContext==null) {
            log.warn("#997.030 (request.processorCommContext==null)");
            return;
        }
*/
        checkProcessorId(request);
        // IDEA has become too lazy
        if (request.processorCommContext.processorId==null) {
            log.warn("#997.030 (request.processorCommContext==null)");
            return;
        }
        processorTopLevelService.processProcessorStatuses(request.processorCommContext.processorId, request.processor, request.functions, response);
    }

    private void checkProcessorId(KeepAliveRequestParamYaml request) {
        if (request.processorCommContext ==null  || request.processorCommContext.processorId==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("#997.070 processorId is null");
        }
    }

    // processing at dispatcher side
    @Nullable
    public KeepAliveResponseParamYaml.AssignedProcessorId getNewProcessorId(@Nullable KeepAliveRequestParamYaml.RequestProcessorId request) {
        if (request==null) {
            return null;
        }
        ProcessorData.ProcessorSessionId processorSessionId = processorService.getNewProcessorId();
        return new KeepAliveResponseParamYaml.AssignedProcessorId(processorSessionId.processorId, processorSessionId.sessionId);
    }
}

