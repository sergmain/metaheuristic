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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchAndExecContextStates;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.NeedRetryAfterCacheCleanException;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/1/2019
 * Time: 4:18 PM
 */
@SuppressWarnings({"UnusedReturnValue"})
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchService {

    private static final String SOURCE_CODE_NOT_FOUND = "Source code wasn't found";

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final BatchCache batchCache;
    private final BatchRepository batchRepository;
    private final BatchSyncService batchSyncService;
    private final ExecContextCache execContextCache;
    private final VariableService variableService;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    public Batch changeStateToPreparing(Long batchId) {
        return batchSyncService.getWithSync(batchId, (b)-> {
            if (b == null) {
                log.warn("#990.010 batch wasn't found {}", batchId);
                return null;
            }
            if (b.execState != Enums.BatchExecState.Unknown.code && b.execState != Enums.BatchExecState.Stored.code &&
                    b.execState != Enums.BatchExecState.Preparing.code) {
                throw new IllegalStateException("\"#990.020 Can't change state to Preparing, " +
                        "current state: " + Enums.BatchExecState.toState(b.execState));
            }
            if (b.execState == Enums.BatchExecState.Preparing.code) {
                return b;
            }
            b.execState = Enums.BatchExecState.Preparing.code;
            return batchCache.save(b);
        });
    }

    public Batch changeStateToProcessing(Long batchId) {
        return batchSyncService.getWithSync(batchId, (b)-> {
            if (b == null) {
                log.warn("#990.030 batch wasn't found {}", batchId);
                return null;
            }
            if (b.execState != Enums.BatchExecState.Preparing.code && b.execState != Enums.BatchExecState.Processing.code) {
                throw new IllegalStateException("\"#990.040 Can't change state to Finished, " +
                        "current state: " + Enums.BatchExecState.toState(b.execState));
            }
            if (b.execState == Enums.BatchExecState.Processing.code) {
                return b;
            }
            b.execState = Enums.BatchExecState.Processing.code;
            dispatcherEventService.publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_PROCESSING_STARTED, null, null, null, batchId, null, null );
            return batchCache.save(b);
        });
    }

    private void changeStateToFinished(Long batchId) {
        batchSyncService.getWithSyncVoid(batchId, (b) -> {
            try {
                if (b == null) {
                    log.warn("#990.050 batch wasn't found {}", batchId);
                    return;
                }
                if (b.execState != Enums.BatchExecState.Processing.code && b.execState != Enums.BatchExecState.Finished.code) {
                    throw new IllegalStateException("#990.060 Can't change state to Finished, " +
                            "current state: " + Enums.BatchExecState.toState(b.execState));
                }
                if (b.execState == Enums.BatchExecState.Finished.code) {
                    return;
                }
                b.execState = Enums.BatchExecState.Finished.code;
                batchCache.save(b);
            }
            finally {
                dispatcherEventService.publishEventBatchFinished(batchId);
            }
        });
    }

    @SuppressWarnings("unused")
    public Batch changeStateToError(Long batchId, String error) {
        return batchSyncService.getWithSync(batchId, (b) -> {
            try {
                if (b == null) {
                    log.warn("#990.070 batch not found in db, batchId: #{}", batchId);
                    return null;
                }
                b.setExecState(Enums.BatchExecState.Error.code);

                BatchParamsYaml batchParams = BatchParamsYamlUtils.BASE_YAML_UTILS.to(b.params);
                if (batchParams == null) {
                    batchParams = new BatchParamsYaml();
                }
                if (batchParams.batchStatus==null) {
                    batchParams.batchStatus = new BatchParamsYaml.BatchStatus();
                }
                BatchStatusProcessor batchStatusProcessor = new BatchStatusProcessor();
                batchStatusProcessor.getGeneralStatus().add(error);
                initBatchStatus(batchStatusProcessor);
                batchParams.batchStatus.status = batchStatusProcessor.status;
                b.params = BatchParamsYamlUtils.BASE_YAML_UTILS.toString(batchParams);

                b = batchCache.save(b);
                return b;
            }
            finally {
                dispatcherEventService.publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_FINISHED_WITH_ERROR, null, null, null, batchId, null, null );
            }
        });
    }

/*
    private static String getMainDocumentPoolCode(ExecContextImpl execContext) {
        ExecContextParamsYaml resourceParams = execContext.getExecContextParamsYaml();
        List<String> codes = resourceParams.execContextYaml.variables.get(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH);
        if (codes.isEmpty()) {
            throw new IllegalStateException("#990.080 Main document section is missed. execContext.params:\n" + execContext.getParams());
        }
        if (codes.size()>1) {
            throw new IllegalStateException("#990.090 Main document section contains more than one main document. execContext.params:\n" + execContext.getParams());
        }
        return codes.get(0);
    }
*/

    public void updateBatchStatuses() {
        List<BatchAndExecContextStates> statuses = batchRepository.findAllUnfinished();
        Map<Long, List<BatchAndExecContextStates>> map = statuses.parallelStream().collect(Collectors.groupingBy(status -> status.batchId));
        for (Long batchId : map.keySet()) {
            boolean isFinished = true;
            for (BatchAndExecContextStates execStates : map.get(batchId)) {
/*
                public enum ExecContextState {
                    ERROR(-2),          // some error in configuration
                    UNKNOWN(-1),        // unknown state
                    NONE(0),            // just created execContext
                    PRODUCING(1),       // producing was just started
                    PRODUCED(2),        // producing was finished
                    STARTED(3),         // started
                    STOPPED(4),         // stopped
                    FINISHED(5),        // finished
                    DOESNT_EXIST(6),    // doesn't exist. this state is needed at processor side to reconcile list of experiments
                    EXPORTING_TO_EXPERIMENT_RESULT(7),    // execContext is marked as needed to be exported to ExperimentResult
                    EXPORTING_TO_EXPERIMENT_RESULT_WAS_STARTED(8),    // execContext is marked as needed to be exported to ExperimentResult and export was started
                    EXPORTED_TO_EXPERIMENT_RESULT(9);    // execContext was exported to ExperimentResult
*/

                if (execStates.execContextState != EnumsApi.ExecContextState.ERROR.code && execStates.execContextState != EnumsApi.ExecContextState.FINISHED.code) {
                    isFinished = false;
                }
            }
            if (isFinished) {
                batchSyncService.getWithSyncVoid(batchId, (b) -> changeStateToFinished(batchId));
            }
        }
    }

    public List<BatchData.BatchExecInfo> getBatches(Page<Long> pageWithBatchIds) {
        List<Long> batchIds = pageWithBatchIds.getContent();
        return getBatchExecInfos(batchIds);
    }

    public List<BatchData.BatchExecInfo> getBatchExecInfos(List<Long> batchIds) {
        List<BatchData.BatchExecInfo> items = new ArrayList<>();
        for (Long batchId : batchIds) {
            Batch batch = batchCache.findById(batchId);
            String uid = SOURCE_CODE_NOT_FOUND;
            if (batch!=null) {
                SourceCode sourceCode = sourceCodeCache.findById(batch.getSourceCodeId());
                boolean ok = true;
                if (sourceCode != null) {
                    uid = sourceCode.getUid();
                } else {
                    if (batch.execState != Enums.BatchExecState.Preparing.code) {
                        ok = false;
                    }
                }
                String execStateStr = Enums.BatchExecState.toState(batch.execState).toString();

                String filename = findUploadedFilenameForBatchId(batchId, Consts.UNKNOWN_FILENAME_IN_BATCH);
                BatchParamsYaml bpy = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.params);
                items.add(new BatchData.BatchExecInfo(
                        batch, uid, execStateStr, batch.execState, ok, filename,
                        S.b(bpy.username) ? "accountId #"+batch.accountId : bpy.username ));
            }
        }
        return items;
    }

    // TODO 2019-10-13 change synchronization to use BatchSyncService
    public BatchParamsYaml.BatchStatus updateStatus(Batch batch) {
        return batchSyncService.getWithSync(batch.id, (b)-> {
            try {
                return updateStatusInternal(b);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                log.warn("#990.097 NeedRetryAfterCacheCleanException was caught");
            }
            try {
                return updateStatusInternal(b);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                final BatchStatusProcessor statusProcessor = new BatchStatusProcessor().addGeneralStatus("#990.100 Can't update batch status, Try later");
                return new BatchParamsYaml.BatchStatus(initBatchStatus(statusProcessor).status);
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    private BatchParamsYaml.BatchStatus updateStatusInternal(Batch batch)  {
        Long batchId = batch.id;
        try {
            if (!S.b(batch.getParams()) &&
                    (batch.execState == Enums.BatchExecState.Finished.code || batch.execState == Enums.BatchExecState.Error.code)) {
                BatchParamsYaml batchParams = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.getParams());
                return batchParams.batchStatus;
            }
            return updateBatchStatusWithoutSync(batchId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#990.120 Error updating batch, new: {}, curr: {}", batch, batchRepository.findById(batchId).orElse(null));
            log.error("#990.121 Error updating batch", e);
            batchCache.evictById(batchId);
            // because this error is somehow related to processorCache, let's invalidate it
            processorCache.clearCache();
            throw new NeedRetryAfterCacheCleanException();
        }
    }

    @Data
    @AllArgsConstructor
    public static class PrepareZipData {
        public BatchStatusProcessor bs;
        public Task task;
        public File zipDir;
        public String mainDocument;
        public Long batchId;
        public Long execContextId;
    }



    public String getUploadedFilename(Long batchId) {
        String filename = findUploadedFilenameForBatchId(batchId, "result.zip");
        if (S.b(filename)) {
            log.error("#990.392 Filename is blank for batchId: {}, will be used default name - result.zip", batchId);
            return Consts.RESULT_ZIP;
        }
        return filename;
    }

    @Nullable
    @Transactional(readOnly = true)
    public String findUploadedFilenameForBatchId(Long batchId, @Nullable String defaultName) {
        Batch batch = batchCache.findById(batchId);
        if (batch==null) {
            return defaultName;
        }
        ExecContextImpl ec = execContextCache.findById(batch.execContextId);
        if (ec == null) {
            return defaultName;
        }
        ExecContextParamsYaml ecpy = ec.getExecContextParamsYaml();
        String startInputVariableName = ecpy.variables.startInputAs;
        if (S.b(startInputVariableName)) {
            return defaultName;
        }
        List<String> filenames = variableService.getFilenameByVariableAndExecContextId(batch.execContextId, startInputVariableName);
        if (filenames.isEmpty()) {
            return defaultName;
        }
        if (filenames.size()>1) {
            log.warn("something wrong, too many startInputAs variables: " + filenames);
        }
        return filenames.get(0);
    }

    @SuppressWarnings("deprecation")
    private String getActualExtension(Long sourceCodeId) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            return (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                    ? globals.defaultResultFileExtension
                    : ".bin");
        }

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        final Meta meta = MetaUtils.getMeta(scpy.source.metas, ConstsApi.META_MH_RESULT_FILE_EXTENSION);

        return meta != null && StringUtils.isNotBlank(meta.getValue())
                ? meta.getValue()
                :
                (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                        ? globals.defaultResultFileExtension
                        : ".bin");
    }

}
