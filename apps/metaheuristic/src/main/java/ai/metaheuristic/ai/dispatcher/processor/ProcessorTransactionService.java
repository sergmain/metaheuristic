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
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

}
