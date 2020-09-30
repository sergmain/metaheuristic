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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorTopLevelService {

    private final Globals globals;
    private final ProcessorRepository processorRepository;
    private final ProcessorCache processorCache;
//    private final ProcessorSyncService processorSyncService;
    private final ProcessorTransactionService processorTransactionService;

    // Attention, this value must be greater than
    // ai.metaheuristic.ai.dispatcher.server.ServerService.SESSION_UPDATE_TIMEOUT
    // at least for 20 seconds
    public static final long PROCESSOR_TIMEOUT = TimeUnit.SECONDS.toMillis(140);

    public ProcessorData.ProcessorsResult getProcessors(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.processorRowsLimit, pageable);
        ProcessorData.ProcessorsResult result = new ProcessorData.ProcessorsResult();
        Slice<Long> ids = processorRepository.findAllByOrderByUpdatedOnDescId(pageable);
        List<ProcessorData.ProcessorStatus> ss = new ArrayList<>(pageable.getPageSize()+1);
        for (Long processorId : ids) {
            Processor processor = processorCache.findById(processorId);
            if (processor ==null) {
                continue;
            }
            ProcessorStatusYaml status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);

            String blacklistReason = processorBlacklisted(status);

            boolean isFunctionProblem = status.downloadStatuses.stream()
                    .anyMatch(s->s.functionState != Enums.FunctionState.none &&
                            s.functionState != Enums.FunctionState.ready &&
                            s.functionState != Enums.FunctionState.not_found &&
                            s.functionState != Enums.FunctionState.ok);

            ss.add(new ProcessorData.ProcessorStatus(
                    processor, System.currentTimeMillis() - processor.updatedOn < PROCESSOR_TIMEOUT,
                    isFunctionProblem,
                    blacklistReason!=null, blacklistReason,
                    processor.updatedOn,
                    (StringUtils.isNotBlank(status.ip) ? status.ip : "[unknown]"),
                    (StringUtils.isNotBlank(status.host) ? status.host : "[unknown]")
            ));
        }
        result.items =  new SliceImpl<>(ss, pageable, ids.hasNext());
        return result;
    }

    private @Nullable String processorBlacklisted(ProcessorStatusYaml status) {
        if (status.taskParamsVersion > TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion()) {
            return "#807.020 Dispatcher is too old and can't communicate to this processor, needs to be upgraded";
        }
        return null;
    }

    public ProcessorData.ProcessorResult getProcessor(Long id) {
        Processor processor = processorCache.findById(id);
        if (processor==null) {
            return new ProcessorData.ProcessorResult("#807.040 Processor wasn't found for id #"+ id);
        }
        ProcessorData.ProcessorResult r = new ProcessorData.ProcessorResult(processor);
        return r;
    }

    public ProcessorData.ProcessorResult updateDescription(Long processorId, @Nullable String desc) {
        return processorTransactionService.updateDescription(processorId, desc);
//        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.updateDescription(processorId, desc));
    }

    public OperationStatusRest deleteProcessorById(Long processorId) {
        return processorTransactionService.deleteProcessorById(processorId);
//        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.deleteProcessorById(processorId));
    }

    public DispatcherCommParamsYaml.ReAssignProcessorId assignNewSessionId(Processor processor, ProcessorStatusYaml ss) {
        return processorTransactionService.assignNewSessionId(processor, ss);
//        return processorSyncService.getWithSync(processor.id, ()-> processorTransactionService.assignNewSessionId(processor, ss));
    }

    public void storeProcessorStatuses(@Nullable String processorIdAsStr, ProcessorCommParamsYaml.ReportProcessorStatus status, ProcessorCommParamsYaml.FunctionDownloadStatus functionDownloadStatus) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final Long processorId = Long.valueOf(processorIdAsStr);
        processorTransactionService.storeProcessorStatuses(processorId, status, functionDownloadStatus);
//        processorSyncService.getWithSyncVoid(processorId, ()-> processorTransactionService.storeProcessorStatuses(processorId, status, functionDownloadStatus));
    }

    public void reconcileProcessorTasks(@Nullable String processorIdAsStr, List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> statuses) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final long processorId = Long.parseLong(processorIdAsStr);
        processorTransactionService.reconcileProcessorTasks(processorId, statuses);
//        processorSyncService.getWithSyncVoid( processorId, ()-> processorTransactionService.reconcileProcessorTasks(processorId, statuses));
    }

    public void checkProcessorId(final long processorId, @Nullable String sessionId, String remoteAddress, DispatcherCommParamsYaml lcpy) {
        processorTransactionService.checkProcessorId(processorId, sessionId, remoteAddress, lcpy);
//        processorSyncService.getWithSyncVoid( processorId, ()-> processorTransactionService.checkProcessorId(processorId, sessionId, remoteAddress, lcpy));
    }


}
