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
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchAndExecContextStates;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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

    private final SourceCodeCache sourceCodeCache;
    private final BatchCache batchCache;
    private final BatchRepository batchRepository;
    private final VariableService variableService;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextService execContextService;
    private final ExecContextFSM execContextFSM;
    private final TaskProducingService taskProducingService;
    private final BatchHelperService batchHelperService;

    public static String getActualExtension(SourceCodeStoredParamsYaml scspy, String defaultResultFileExtension) {
        return getActualExtension(SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source), defaultResultFileExtension);
    }

    public static String getActualExtension(SourceCodeParamsYaml scpy, String defaultResultFileExtension) {
        final String ext = MetaUtils.getValue(scpy.source.metas, ConstsApi.META_MH_RESULT_FILE_EXTENSION);

        return S.b(ext)
                ? (StringUtils.isNotBlank(defaultResultFileExtension) ? defaultResultFileExtension : ".bin")
                : ext;
    }

    @Nullable
    private Batch changeStateToPreparing(Long batchId) {
            Batch b = batchCache.findById(batchId);
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
    }

    @Transactional
    @Nullable
    public Batch changeStateToProcessing(Long batchId) {
            Batch b = batchCache.findById(batchId);
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
    }

    private void changeStateToFinished(Long batchId) {
            try {
                Batch b = batchCache.findById(batchId);
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
                return;
            }
            finally {
                dispatcherEventService.publishEventBatchFinished(batchId);
            }
    }

    @Transactional
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
                    break;
                }
            }
            if (isFinished) {
                changeStateToFinished(batchId);
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
                SourceCodeImpl sourceCode = sourceCodeCache.findById(batch.getSourceCodeId());
                boolean ok = true;
                if (sourceCode != null) {
                    uid = sourceCode.getUid();
                } else {
                    if (batch.execState != Enums.BatchExecState.Preparing.code) {
                        ok = false;
                    }
                }
                String execStateStr = Enums.BatchExecState.toState(batch.execState).toString();

                String filename = batchHelperService.findUploadedFilenameForBatchId(batch.execContextId, Consts.UNKNOWN_FILENAME_IN_BATCH);
                BatchParamsYaml bpy = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.params);
                items.add(new BatchData.BatchExecInfo(
                        batch, uid, execStateStr, batch.execState, ok, filename,
                        S.b(bpy.username) ? "accountId #"+batch.accountId : bpy.username ));
            }
        }
        return items;
    }

    @Data
    @AllArgsConstructor
    public static class PrepareZipData {
        public BatchStatusProcessor bs;
        public Task task;
        public File zipDir;
        public String mainDocument;
        public Long execContextId;
    }

    @Transactional
    public BatchData.UploadingStatus createBatchForFile(final MultipartFile file, String originFilename, SourceCodeImpl sourceCode, ExecContextImpl execContext, final DispatcherContext dispatcherContext) {
        Batch b;
        String startInputAs = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params).variables.startInputAs;
        if (S.b(startInputAs)) {
            return new BatchData.UploadingStatus("#981.200 Wrong format of sourceCode, startInputAs isn't specified");
        }
        try {
            variableService.createInitialized(
                    file.getInputStream(), file.getSize(), startInputAs,
                    originFilename, execContext.getId(),"1"
            );
        } catch (IOException e) {
            ExceptionUtils.rethrow(e);
        }

        b = new Batch(sourceCode.id, execContext.getId(), Enums.BatchExecState.Stored,
                dispatcherContext.getAccountId(), dispatcherContext.getCompanyId());

        BatchParamsYaml bpy = new BatchParamsYaml();
        bpy.username = dispatcherContext.account.username;
        b.params = BatchParamsYamlUtils.BASE_YAML_UTILS.toString(bpy);
        b = batchCache.save(b);

        dispatcherEventService.publishBatchEvent(
                EnumsApi.DispatcherEventType.BATCH_CREATED, dispatcherContext.getCompanyId(),
                sourceCode.uid, null, b.id, execContext.getId(), dispatcherContext );

        final Batch batch = changeStateToPreparing(b.id);
        // TODO 2019-10-14 when batch is null tempDir won't be deleted, this is wrong behavior and need to be fixed
        if (batch==null) {
            return new BatchData.UploadingStatus("#981.220 can't find batch with id " + b.id);
        }

        log.info("#981.240 The file {} was successfully stored for processing", originFilename);
        //noinspection unused
        int i=0;
        // start producing new tasks
        OperationStatusRest operationStatus = execContextFSM.execContextTargetState(
                execContext.getId(), EnumsApi.ExecContextState.PRODUCING, dispatcherContext.getCompanyId());

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        taskProducingService.produceAllTasks(true, sourceCode, execContext);

        operationStatus = execContextFSM.execContextTargetState(execContext.getId(), EnumsApi.ExecContextState.STARTED, dispatcherContext.getCompanyId());

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }

        changeStateToProcessing(batch.id);

        BatchData.UploadingStatus uploadingStatus = new BatchData.UploadingStatus(b.id, execContext.getId());
        return uploadingStatus;
    }

    @Transactional
    public OperationStatusRest deleteBatch(Long companyUniqueId, boolean isVirtualDeletion, Batch batch) {
        if (isVirtualDeletion) {
            if (!batch.deleted) {
                execContextFSM.toFinished(batch.execContextId);

                Batch b = batchRepository.findByIdForUpdate(batch.id, batch.companyId);
                b.deleted = true;
                batchCache.save(b);
            }
        } else {
            execContextService.deleteExecContext(batch.execContextId, companyUniqueId);
            batchCache.deleteById(batch.id);
        }
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #" + batch.id + " was deleted successfully.", null);
    }


}
