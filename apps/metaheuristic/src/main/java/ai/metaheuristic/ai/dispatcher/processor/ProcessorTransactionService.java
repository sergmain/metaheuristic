/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.DispatcherApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 7:54 PM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorTransactionService {

    private final ProcessorRepository processorRepository;
    private final ProcessorCache processorCache;

    private static final int COOL_DOWN_MINUTES = 2;
    private static final long LOG_FILE_REQUEST_COOL_DOWN_TIMEOUT = TimeUnit.MINUTES.toMillis(COOL_DOWN_MINUTES);

    private static String createNewSessionId() {
        return UUID.randomUUID().toString() + '-' + UUID.randomUUID();
    }

    @Nullable
    @Transactional
    public DispatcherApiData.ProcessorSessionId checkProcessorId(Enums.ProcessorAndSessionStatus processorAndSessionStatus, final Long processorId, String remoteAddress) {
            if (processorAndSessionStatus == Enums.ProcessorAndSessionStatus.reassignProcessor) {
                return reassignProcessorId(remoteAddress, "Id was reassigned from " + processorId);
            }

            Processor processor = processorCache.findById(processorId);
            if (processor==null) {
                throw new IllegalStateException("(processor==null)");
            }

            if (processorAndSessionStatus == Enums.ProcessorAndSessionStatus.newSession) {
                return assignNewSessionId(processor);
            }
            else if (processorAndSessionStatus == Enums.ProcessorAndSessionStatus.updateSession) {
                updateSession(processor);
                return null;
            }

            throw new IllegalStateException("unknown processorAndSessionStatus: " + processorAndSessionStatus);
    }

    @Transactional
    public OperationStatusRest requestLogFile(Long processorId) {
        ProcessorSyncService.checkWriteLockPresent(processorId);

        Processor processor = processorCache.findById(processorId);
        if (processor==null) {
            String es = "#807.040 Can't find Processor #" + processorId;
            log.warn(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        ProcessorStatusYaml psy = processor.getProcessorStatusYaml();
        if (psy.log==null) {
            psy.log = new ProcessorStatusYaml.Log();
        }
        if (psy.log.logRequested) {
            return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Log file for processor #"+processorId+" was already requested", null);
        }
        if (psy.log.logReceivedOn != null) {
            long diff = System.currentTimeMillis() - psy.log.logReceivedOn;
            if (diff<=LOG_FILE_REQUEST_COOL_DOWN_TIMEOUT) {
                return new OperationStatusRest(EnumsApi.OperationStatus.OK,
                        S.f("Log file can be requested not often than 1 time in %s minutes. Cool down in %d seconds",
                                COOL_DOWN_MINUTES, (LOG_FILE_REQUEST_COOL_DOWN_TIMEOUT - diff)/1_000),
                        null);
            }
        }

        psy.log.logRequested = true;
        psy.log.requestedOn = System.currentTimeMillis();
        processor.updateParams(psy);
        processorCache.save(processor);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Log file for processor #"+processorId+" was requested successfully", null);
    }

    @Transactional
    public void setLogFileReceived(Long processorId) {
        ProcessorSyncService.checkWriteLockPresent(processorId);

        Processor processor = processorCache.findById(processorId);
        if (processor==null) {
            log.warn("#807.045 Can't find Processor #{}", processorId);
            return;
        }
        ProcessorStatusYaml psy = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.getStatus());
        if (psy.log==null) {
            psy.log = new ProcessorStatusYaml.Log();
        }
        psy.log.logReceivedOn = System.currentTimeMillis();
        processor.updateParams(psy);
        processorCache.save(processor);

    }

    public DispatcherApiData.ProcessorSessionId assignNewSessionId(Processor processor) {
        TxUtils.checkTxExists();
        ProcessorSyncService.checkWriteLockPresent(processor.id);

        ProcessorStatusYaml ss = processor.getProcessorStatusYaml();

        ss.sessionId = createNewSessionId();
        ss.sessionCreatedOn = System.currentTimeMillis();
        processor.updateParams(ss);
        processor.updatedOn = ss.sessionCreatedOn;
        processorCache.save(processor);

        // the same processorId but new sessionId
        return new DispatcherApiData.ProcessorSessionId(processor.getId(), ss.sessionId);
    }

    @Transactional
    public DispatcherApiData.ProcessorSessionId getNewProcessorId() {
        String sessionId = createNewSessionId();
        ProcessorStatusYaml psy = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown),
                "", sessionId, System.currentTimeMillis(), "", "", null, false,
                1, EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null, null);

        final Processor p = createProcessor(null, null, psy);
        return new DispatcherApiData.ProcessorSessionId(p.id, sessionId);
    }

    @Transactional
    public Processor createProcessor(@Nullable String description, @Nullable String ip, ProcessorStatusYaml ss) {
        Processor p = new Processor();
        p.updateParams(ss);
        p.description= description;
        p.ip = ip;
        return processorCache.save(p);
    }

    @Transactional
    public ProcessorData.ProcessorResult updateDescription(Long processorId, @Nullable String desc) {
        ProcessorSyncService.checkWriteLockPresent(processorId);
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
        ProcessorSyncService.checkWriteLockPresent(id);
        Processor processor = processorCache.findById(id);
        if (processor == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#807.080 Processor wasn't found, processorId: " + id);
        }
        processorCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public void processKeepAliveData(
            Long processorId, KeepAliveRequestParamYaml.ReportProcessor status,
            KeepAliveRequestParamYaml.FunctionDownloadStatuses functionDownloadStatus,
            ProcessorStatusYaml psy, final boolean processorStatusDifferent, final boolean processorFunctionDownloadStatusDifferent) {

        ProcessorSyncService.checkWriteLockPresent(processorId);

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("#807.100 Processor wasn't found for processorId: " + processorId);
        }

        if (processorStatusDifferent) {
            psy.env = to(status.env);
            psy.gitStatusInfo = status.gitStatusInfo;
            psy.schedule = status.schedule;

            // Do not include updating of sessionId
            // psy.sessionId = command.status.sessionId;

            // Do not include updating of sessionCreatedOn!
            // psy.sessionCreatedOn = command.status.sessionCreatedOn;

            psy.ip = status.ip;
            psy.host = status.host;
            psy.errors = status.errors;
            psy.logDownloadable = status.logDownloadable;
            psy.taskParamsVersion = status.taskParamsVersion;
            psy.os = (status.os == null ? EnumsApi.OS.unknown : status.os);
            psy.currDir = status.currDir;
        }
        if (processorFunctionDownloadStatusDifferent) {
            psy.downloadStatuses = functionDownloadStatus.statuses.stream()
                    .map(o -> new ProcessorStatusYaml.DownloadStatus(o.state, o.code))
                    .collect(Collectors.toList());
        }

        if (processorStatusDifferent || processorFunctionDownloadStatusDifferent) {
            processor.updatedOn = System.currentTimeMillis();
            processor.updateParams(psy);
            try {
                log.debug("#807.120 Save new processor status, processor: {}", processor);
                processorCache.save(processor);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("""
                            #807.140 ObjectOptimisticLockingFailureException was encountered
                            new processor:
                            {}
                            db processor
                            {}""", processor, processorRepository.findById(processorId).orElse(null));
            }
        }
        else {
            log.debug("#807.160 Processor status is equal to the status stored in db");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    private static ProcessorStatusYaml.Env to(KeepAliveRequestParamYaml.Env envYaml) {
        ProcessorStatusYaml.Env env = new ProcessorStatusYaml.Env();
        envYaml.disk.stream().map(o->new ProcessorStatusYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(()->env.disk));
        envYaml.quotas.values.stream().map(o->new ProcessorStatusYaml.Quota(o.tag, o.amount, o.disabled)).collect(Collectors.toCollection(()->env.quotas.values));
        env.quotas.limit = envYaml.quotas.limit;
        env.quotas.disabled = envYaml.quotas.disabled;
        env.quotas.defaultValue = envYaml.quotas.defaultValue;
        env.envs.putAll(envYaml.envs);
        env.mirrors.putAll(envYaml.mirrors);
        env.tags = envYaml.tags;
        return env;
    }

    @Transactional
    public DispatcherApiData.ProcessorSessionId reassignProcessorId(@Nullable String remoteAddress, @Nullable String description) {
        String sessionId = ProcessorTransactionService.createNewSessionId();
        ProcessorStatusYaml psy = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO, null, false, 1, EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null, null);
        Processor p = createProcessor(description, remoteAddress, psy);

        return new DispatcherApiData.ProcessorSessionId(p.getId(), sessionId);
    }

    /**
     * session is Ok, so we need to update session's timestamp periodically
     */
    @Transactional
    public void updateSessionWithTx(Processor processor, ProcessorStatusYaml ss) {
        final long millis = System.currentTimeMillis();
        final long diff = millis - ss.sessionCreatedOn;
        if (diff > Consts.SESSION_UPDATE_TIMEOUT) {
            log.debug("""
                            #807.200 (System.currentTimeMillis()-ss.sessionCreatedOn)>SESSION_UPDATE_TIMEOUT),
                            '    processor.version: {}, millis: {}, ss.sessionCreatedOn: {}, diff: {}, SESSION_UPDATE_TIMEOUT: {},
                            '    processor.status:
                            {},
                            '    return ReAssignProcessorId() with the same processorId and sessionId. only session's timestamp was updated.""",
                    processor.version, millis, ss.sessionCreatedOn, diff, Consts.SESSION_UPDATE_TIMEOUT, processor.getStatus());
            // the same processor, with the same sessionId
            // so we just need to refresh sessionId timestamp
            ss.sessionCreatedOn = millis;
            processor.updatedOn = millis;
            processor.updateParams(ss);
            processorCache.save(processor);

            // the same processorId but new sessionId
            return;
        }
        else {
            // the same processorId, the same sessionId, session isn't expired
            return;
        }
    }

    private void updateSession(Processor processor) {
        final long millis = System.currentTimeMillis();
        ProcessorStatusYaml ss = processor.getProcessorStatusYaml();
        final long diff = millis - ss.sessionCreatedOn;
        log.debug("""
                        #807.200 (System.currentTimeMillis()-ss.sessionCreatedOn)>SESSION_UPDATE_TIMEOUT),
                        '    processor.version: {}, millis: {}, ss.sessionCreatedOn: {}, diff: {}, SESSION_UPDATE_TIMEOUT: {},
                        '    processor.status:
                        {},
                        '    return ReAssignProcessorId() with the same processorId and sessionId. only session's timestamp was updated.""",
                processor.version, millis, ss.sessionCreatedOn, diff, Consts.SESSION_UPDATE_TIMEOUT, processor.getStatus());
        // the same processor, with the same sessionId
        // so we just need to refresh sessionId timestamp
        ss.sessionCreatedOn = millis;
        processor.updateParams(ss);
        processor.updatedOn = ss.sessionCreatedOn;
        processorCache.save(processor);
    }

}
