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
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 7:54 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorTransactionService {

    private final ProcessorCache processorCache;
    private final ProcessorRepository processorRepository;
    private final TaskRepository taskRepository;
    private final ExecContextTopLevelService execContextTopLevelService;

    public static String createNewSessionId() {
        return UUID.randomUUID().toString() + '-' + UUID.randomUUID().toString();
    }

    @Transactional
    public DispatcherCommParamsYaml.ReAssignProcessorId assignNewSessionId(Processor processor, ProcessorStatusYaml ss) {
        ss.sessionId = createNewSessionId();
        ss.sessionCreatedOn = System.currentTimeMillis();
        processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        processor.updatedOn = ss.sessionCreatedOn;
        processorCache.save(processor);
        // the same processorId but new sessionId
        return new DispatcherCommParamsYaml.ReAssignProcessorId(processor.getId(), ss.sessionId);
    }

    @Transactional
    public ProcessorData.ProcessorWithSessionId getNewProcessorId() {
        String sessionId = createNewSessionId();
        ProcessorStatusYaml psy = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown),
                "", sessionId, System.currentTimeMillis(), "", "", null, false,
                1, EnumsApi.OS.unknown);

        final Processor p = createProcessor(null, null, psy);
        return new ProcessorData.ProcessorWithSessionId(p, sessionId);
    }

    @Transactional
    public Processor createProcessor(@Nullable String description, @Nullable String ip, ProcessorStatusYaml ss) {
        Processor p = new Processor();
        p.setStatus(ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss));
        p.description= description;
        p.ip = ip;
        return processorCache.save(p);
    }

    @Transactional
    public ProcessorData.ProcessorResult updateDescription(Long processorId, @Nullable String desc) {
        Processor s = processorCache.findById(processorId);
        if (s==null) {
            return new ProcessorData.ProcessorResult("#807.060 processor wasn't found, processorId: " + processorId);
        }
        s.description = desc;
        ProcessorData.ProcessorResult r = new ProcessorData.ProcessorResult(processorCache.save(s));
        return r;
    }

    @Transactional
    public OperationStatusRest deleteProcessorById(Long id) {
        Processor processor = processorCache.findById(id);
        if (processor == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#807.080 Processor wasn't found, processorId: " + id);
        }
        processorCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public Void storeProcessorStatuses(Long processorId, ProcessorCommParamsYaml.ReportProcessorStatus status, ProcessorCommParamsYaml.FunctionDownloadStatus functionDownloadStatus) {
        log.debug("Before entering in sync block, storeProcessorStatus()");
        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("#807.100 Processor wasn't found for processorId: " + processorId);
        }
        ProcessorStatusYaml ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        boolean isUpdated = false;
        if (isProcessorStatusDifferent(ss, status)) {
            ss.env = status.env;
            ss.gitStatusInfo = status.gitStatusInfo;
            ss.schedule = status.schedule;

            // Do not include updating of sessionId
            // ss.sessionId = command.status.sessionId;

            // Do not include updating of sessionCreatedOn!
            // ss.sessionCreatedOn = command.status.sessionCreatedOn;

            ss.ip = status.ip;
            ss.host = status.host;
            ss.errors = status.errors;
            ss.logDownloadable = status.logDownloadable;
            ss.taskParamsVersion = status.taskParamsVersion;
            ss.os = (status.os == null ? EnumsApi.OS.unknown : status.os);

            processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            processor.updatedOn = System.currentTimeMillis();
            isUpdated = true;
        }
        if (isProcessorFunctionDownloadStatusDifferent(ss, functionDownloadStatus)) {
            ss.downloadStatuses = functionDownloadStatus.statuses.stream()
                    .map(o -> new ProcessorStatusYaml.DownloadStatus(o.functionState, o.functionCode))
                    .collect(Collectors.toList());
            processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            processor.updatedOn = System.currentTimeMillis();
            isUpdated = true;
        }
        if (isUpdated) {
            try {
                log.debug("#807.120 Save new processor status, processor: {}", processor);
                processorCache.save(processor);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("#807.140 ObjectOptimisticLockingFailureException was encountered\n" +
                        "new processor:\n{}\n" +
                        "db processor\n{}", processor, processorRepository.findById(processorId).orElse(null));

                processorCache.clearCache();
            }
        } else {
            log.debug("#807.160 Processor status is equal to the status stored in db");
        }
        log.debug("#807.180 After leaving sync block");
        return null;
    }

    public static boolean isProcessorFunctionDownloadStatusDifferent(ProcessorStatusYaml ss, ProcessorCommParamsYaml.FunctionDownloadStatus status) {
        if (ss.downloadStatuses.size()!=status.statuses.size()) {
            return true;
        }
        for (ProcessorStatusYaml.DownloadStatus downloadStatus : ss.downloadStatuses) {
            for (ProcessorCommParamsYaml.FunctionDownloadStatus.Status sds : status.statuses) {
                if (downloadStatus.functionCode.equals(sds.functionCode) && !downloadStatus.functionState.equals(sds.functionState)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isProcessorStatusDifferent(ProcessorStatusYaml ss, ProcessorCommParamsYaml.ReportProcessorStatus status) {
        return
                !Objects.equals(ss.env, status.env) ||
                        !Objects.equals(ss.gitStatusInfo, status.gitStatusInfo) ||
                        !Objects.equals(ss.schedule, status.schedule) ||
                        !Objects.equals(ss.ip, status.ip) ||
                        !Objects.equals(ss.host, status.host) ||
                        !Objects.equals(ss.errors, status.errors) ||
                        ss.logDownloadable!=status.logDownloadable ||
                        ss.taskParamsVersion!=status.taskParamsVersion||
                        ss.os!=status.os;
    }

    @Transactional
    public DispatcherCommParamsYaml.ReAssignProcessorId reassignProcessorId(@Nullable String remoteAddress, @Nullable String description) {
        String sessionId = ProcessorTransactionService.createNewSessionId();
        ProcessorStatusYaml psy = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, false, 1, EnumsApi.OS.unknown);
        Processor p = createProcessor(description, remoteAddress, psy);

        return new DispatcherCommParamsYaml.ReAssignProcessorId(p.getId(), sessionId);
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

    public Void reconcileProcessorTasks(final long processorId, List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> statuses) {
        TxUtils.checkTxNotExists();

        List<Object[]> tasks = taskRepository.findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(processorId);
        for (Object[] obj : tasks) {
            long taskId = ((Number)obj[0]).longValue();
            Long assignedOn = obj[1]!=null ? ((Number)obj[1]).longValue() : null;
            Long execContextId = ((Number)obj[2]).longValue();

            if (assignedOn==null) {
                log.error("#807.190 Processor #{} has a task with assignedOn is null", processorId);
            }

            boolean isFound = statuses.stream().anyMatch(status -> status.taskId == taskId);
            boolean isExpired = assignedOn!=null && (System.currentTimeMillis() - assignedOn > 90_000);

            // if Processor haven't reported back about this task in 90 seconds,
            // this task will be de-assigned from this Processor
            if (!isFound && isExpired) {
                log.info("#807.200 De-assign task #{} from processor #{}", taskId, processorId);
                log.info("\tstatuses: {}", statuses.stream().map( o -> Long.toString(o.taskId)).collect(Collectors.toList()));
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tassignedOn: {}, isFound: {}, is expired: {}", assignedOn, isFound, isExpired);
                OperationStatusRest result = execContextTopLevelService.resetTask(taskId);
                if (result.status== EnumsApi.OperationStatus.ERROR) {
                    log.error("#807.220 Resetting of task #{} was failed. See log for more info.", taskId);
                }
            }
        }
        return null;
    }

    @Transactional
    public Void checkProcessorId(final long processorId, @Nullable String sessionId, String remoteAddress, DispatcherCommParamsYaml lcpy) {
        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.warn("#440.260 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
            lcpy.reAssignedProcessorId = reassignProcessorId(remoteAddress, "Id was reassigned from " + processorId);
            return null;
        }
        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        } catch (Throwable e) {
            log.error("#440.280 Error parsing current status of processor:\n{}", processor.status);
            log.error("#440.300 Error ", e);
            // skip any command from this processor
            return null;
        }
        if (StringUtils.isBlank(sessionId)) {
            log.debug("#440.320 StringUtils.isBlank(sessionId), return ReAssignProcessorId() with new sessionId");
            // the same processor but with different and expired sessionId
            // so we can continue to use this processorId with new sessionId
            lcpy.reAssignedProcessorId = assignNewSessionId(processor, ss);
            return null;
        }
        if (!ss.sessionId.equals(sessionId)) {
            if ((System.currentTimeMillis() - ss.sessionCreatedOn) > Consts.SESSION_TTL) {
                log.debug("#440.340 !ss.sessionId.equals(sessionId) && (System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL, return ReAssignProcessorId() with new sessionId");
                // the same processor but with different and expired sessionId
                // so we can continue to use this processorId with new sessionId
                // we won't use processor's sessionIf to be sure that sessionId has valid format
                lcpy.reAssignedProcessorId = assignNewSessionId(processor, ss);
                return null;
            } else {
                log.debug("#440.360 !ss.sessionId.equals(sessionId) && !((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL), return ReAssignProcessorId() with new processorId and new sessionId");
                // different processors with the same processorId
                // there is other active processor with valid sessionId
                lcpy.reAssignedProcessorId = reassignProcessorId(remoteAddress, "Id was reassigned from " + processorId);
                return null;
            }
        } else {
            // see logs in method
            updateSession(processor, ss);
        }
        return null;
    }

}
