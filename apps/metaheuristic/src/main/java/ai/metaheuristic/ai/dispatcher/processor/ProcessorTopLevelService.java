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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorTopLevelService {

    private final Globals globals;
    private final ProcessorRepository processorRepository;
    private final ProcessorCache processorCache;
    private final TaskRepository taskRepository;
    private final ExecContextFSM execContextFSM;
    private final ProcessorSyncService processorSyncService;
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

    @Transactional
    public ProcessorData.ProcessorResult updateDescription(Long processorId, @Nullable String desc) {
        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.updateDescription(processorId, desc));
    }

    public OperationStatusRest deleteProcessorById(Long processorId) {
        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.deleteProcessorById(processorId));
    }

    public DispatcherCommParamsYaml.ReAssignProcessorId assignNewSessionId(Processor processor, ProcessorStatusYaml ss) {
        return processorSyncService.getWithSync(processor.id, ()-> processorTransactionService.assignNewSessionId(processor, ss));
    }

    public void storeProcessorStatuses(@Nullable String processorIdAsStr, ProcessorCommParamsYaml.ReportProcessorStatus status, ProcessorCommParamsYaml.FunctionDownloadStatus functionDownloadStatus) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final Long processorId = Long.valueOf(processorIdAsStr);
        processorSyncService.getWithSyncVoid(processorId, ()-> processorTransactionService.storeProcessorStatuses(processorId, status, functionDownloadStatus));
    }

    /**
     * session is Ok, so we need to update session's timestamp periodically
     */
    @Transactional
    @SuppressWarnings("UnnecessaryReturnStatement")
    public void updateSession(Processor processor, ProcessorStatusYaml ss) {
        final long millis = System.currentTimeMillis();
        final long diff = millis - ss.sessionCreatedOn;
        if (diff > Consts.SESSION_UPDATE_TIMEOUT) {
            log.debug("#440.380 (System.currentTimeMillis()-ss.sessionCreatedOn)>SESSION_UPDATE_TIMEOUT),\n" +
                            "'    processor.version: {}, millis: {}, ss.sessionCreatedOn: {}, diff: {}, SESSION_UPDATE_TIMEOUT: {},\n" +
                            "'    processor.status:\n{},\n" +
                            "'    return ReAssignProcessorId() with the same processorId and sessionId. only session'p timestamp was updated.",
                    processor.version, millis, ss.sessionCreatedOn, diff, Consts.SESSION_UPDATE_TIMEOUT, processor.status);
            // the same processor, with the same sessionId
            // so we just need to refresh sessionId timestamp
            ss.sessionCreatedOn = millis;
            processor.updatedOn = millis;
            processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            processorCache.save(processor);

            // the same processorId but new sessionId
            return;
        }
        else {
            // the same processorId, the same sessionId, session isn't expired
            return;
        }
    }

    @Transactional
    public DispatcherCommParamsYaml.ReAssignProcessorId reassignProcessorId(@Nullable String remoteAddress, @Nullable String description) {
        String sessionId = ProcessorTransactionService.createNewSessionId();
        ProcessorStatusYaml psy = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, false, 1, EnumsApi.OS.unknown);
        Processor p = processorTransactionService.createProcessor(description, remoteAddress, psy);

        return new DispatcherCommParamsYaml.ReAssignProcessorId(p.getId(), sessionId);
    }

    @Transactional
    public void reconcileProcessorTasks(@Nullable String processorIdAsStr, List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> statuses) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final long processorId = Long.parseLong(processorIdAsStr);
        List<Object[]> tasks = taskRepository.findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(processorId);
        for (Object[] obj : tasks) {
            long taskId = ((Number)obj[0]).longValue();
            Long assignedOn = obj[1]!=null ? ((Number)obj[1]).longValue() : null;
            Long execContextId = ((Number)obj[2]).longValue();

            if (assignedOn==null) {
                log.error("#807.190 Processor #{} has a task with assignedOn is null", processorIdAsStr);
            }

            boolean isFound = statuses.stream().anyMatch(status -> status.taskId == taskId);
            boolean isExpired = assignedOn!=null && (System.currentTimeMillis() - assignedOn > 90_000);

            // if Processor haven't reported back about this task in 90 seconds,
            // this task will be de-assigned from this Processor
            if (!isFound && isExpired) {
                log.info("#807.200 De-assign task #{} from processor #{}", taskId, processorIdAsStr);
                log.info("\tstatuses: {}", statuses.stream().map( o -> Long.toString(o.taskId)).collect(Collectors.toList()));
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tassignedOn: {}, isFound: {}, is expired: {}", assignedOn, isFound, isExpired);
                OperationStatusRest result = execContextFSM.resetTask(execContextId, taskId);
                if (result.status== EnumsApi.OperationStatus.ERROR) {
                    log.error("#807.220 Resetting of task #{} was failed. See log for more info.", taskId);
                }
            }
        }
    }


}
