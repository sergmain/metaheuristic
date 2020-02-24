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
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.exceptions.NeedRetryAfterCacheCleanException;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchAndExecContextStates;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
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
    private static final String IP_HOST = "IP: %s, host: %s";
    private static final String DELEMITER_2 = "\n====================================================================\n";

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final BatchCache batchCache;
    private final BatchRepository batchRepository;
    private final ExecContextCache execContextCache;
    private final VariableService variableService;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    private static final ConcurrentHashMap<Long, Object> batchMap = new ConcurrentHashMap<>(100, 0.75f, 10);

    /**
     * Don't forget to call this method before storing in db
     * @param batchStatus
     */
    public static BatchStatusProcessor initBatchStatus(BatchStatusProcessor batchStatus) {
        String generalStr = batchStatus.getGeneralStatus().asString();
        if (!generalStr.isBlank()) {
            batchStatus.status = generalStr + DELEMITER_2;
        }
        String progressStr = batchStatus.getProgressStatus().asString();
        if (!progressStr.isBlank()) {
            batchStatus.status += progressStr + DELEMITER_2;
        }
        String okStr = batchStatus.getOkStatus().asString();
        if (!okStr.isBlank()) {
            batchStatus.status += okStr + DELEMITER_2;
        }
        String errorStr = batchStatus.getErrorStatus().asString();
        if (!errorStr.isBlank()) {
            batchStatus.status += errorStr + DELEMITER_2;
        }
        return batchStatus;
    }

    public Batch changeStateToPreparing(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
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
    }

    public Batch changeStateToProcessing(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
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
    }

    private void changeStateToFinished(Long batchId) {
        if (batchId==null) {
            return;
        }
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                Batch b = batchRepository.findByIdForUpdate(batchId);
                if (b == null) {
                    log.warn("#990.050 batch wasn't found {}", batchId);
                    return;
                }
                if (b.execState != Enums.BatchExecState.Processing.code
                        && b.execState != Enums.BatchExecState.Finished.code
                ) {
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
                try {
                    updateBatchStatusWithoutSync(batchId);
                } catch (Throwable th) {
                    log.warn("#990.065 error while updating the status of batch #" + batchId, th);
                    // TODO 2019-12-15 this isn't good solution but need more info about behaviour with this error
                }
                dispatcherEventService.publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_PROCESSING_FINISHED, null, null, null, batchId, null, null );
            }
        }
    }

    @SuppressWarnings("unused")
    public Batch changeStateToError(Long batchId, String error) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                Batch b = batchCache.findById(batchId);
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
        }
    }

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
                    EXPORTING_TO_ATLAS(7),    // execContext is marked as needed to be exported to atlas
                    EXPORTING_TO_ATLAS_WAS_STARTED(8),    // execContext is marked as needed to be exported to atlas and export was started
                    EXPORTED_TO_ATLAS(9);    // execContext was exported to atlas
*/

                if (execStates.execContextState != EnumsApi.ExecContextState.ERROR.code && execStates.execContextState != EnumsApi.ExecContextState.FINISHED.code) {
                    isFinished = false;
                    break;
                }
            }
            if (isFinished) {
                final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (obj) {
                    changeStateToFinished(batchId);
                }
            }
        }
    }

    List<BatchData.ProcessResourceItem> getBatches(Page<Long> batchIds) {
        List<BatchData.ProcessResourceItem> items = new ArrayList<>();
        List<Object[]> batchInfos = variableService.getFilenamesForBatchIds(batchIds.getContent());
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
                String filename = batchInfos.stream().filter(o->o[0].equals(batchId)).map(o->(String)o[1]).findFirst().orElse("[unknown]");
//                Account account = accountCache.findByUsername()
                BatchParamsYaml bpy = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.params);
                items.add(new BatchData.ProcessResourceItem(
                        batch, uid, execStateStr, batch.execState, ok, filename,
                        S.b(bpy.username) ? "accountId #"+batch.accountId : bpy.username ));
            }
        }
        return items;
    }

    // TODO 2019-10-13 change synchronization to use BatchSyncService
    public BatchParamsYaml.BatchStatus updateStatus(Batch b) {
        final Object obj = batchMap.computeIfAbsent(b.id, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
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
        }
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

    private BatchParamsYaml.BatchStatus updateBatchStatusWithoutSync(Long batchId) {
        Batch b = batchRepository.findByIdForUpdate(batchId);
        if (b == null) {
            final BatchStatusProcessor statusProcessor = new BatchStatusProcessor().addGeneralStatus("#990.113, Batch wasn't found, batchId: " + batchId, '\n');
            BatchParamsYaml.BatchStatus batchStatus = new BatchParamsYaml.BatchStatus(initBatchStatus(statusProcessor).status);
            batchStatus.ok = false;
            return batchStatus;
        }

        BatchStatusProcessor batchStatus = prepareStatusAndData(b, (prepareZipData, file) -> true, null);
        BatchParamsYaml batchParams = BatchParamsYamlUtils.BASE_YAML_UTILS.to(b.getParams());
        if (batchParams == null) {
            batchParams = new BatchParamsYaml();
        }
        if (batchParams.batchStatus == null) {
            batchParams.batchStatus = new BatchParamsYaml.BatchStatus();
        }
        batchParams.batchStatus.status = batchStatus.status;
        b.params = BatchParamsYamlUtils.BASE_YAML_UTILS.toString(batchParams);
        batchCache.save(b);

        return batchParams.batchStatus;
    }

    private String getStatusForError(Long batchId, ExecContext ec, String mainDocument, Task task, FunctionApiData.FunctionExec functionExec, String processorIpAndHost) {

        final String header =
                "#990.210 " + mainDocument + ", Task was completed with an error, batchId:" + batchId + ", execContextId: " + ec.getId() + ", " +
                "taskId: " + task.getId() + "\n" +
                "processorId: " + task.getProcessorId() + "\n" +
                processorIpAndHost + "\n\n";
        StringBuilder sb = new StringBuilder(header);
        if (functionExec.generalExec!=null) {
            sb.append("General execution state:\n");
            sb.append(execResultAsStr(functionExec.generalExec));
        }
        if (functionExec.preExecs!=null && !functionExec.preExecs.isEmpty()) {
            sb.append("Pre functions:\n");
            for (FunctionApiData.SystemExecResult preExec : functionExec.preExecs) {
                sb.append(execResultAsStr(preExec));
            }
        }
        if (StringUtils.isNotBlank(functionExec.exec.functionCode)) {
            sb.append("Main function:\n");
            sb.append(execResultAsStr(functionExec.exec));
        }

        if (functionExec.postExecs!=null && !functionExec.postExecs.isEmpty()) {
            sb.append("Post functions:\n");
            for (FunctionApiData.SystemExecResult postExec : functionExec.postExecs) {
                sb.append(execResultAsStr(postExec));
            }
        }

        return sb.toString();
    }

    private String execResultAsStr(FunctionApiData.SystemExecResult execResult) {
        return
                "function: " + execResult.functionCode + "\n" +
                "isOk: " + execResult.isOk + "\n" +
                "exitCode: " + execResult.exitCode + "\n" +
                "console:\n" + (StringUtils.isNotBlank(execResult.console) ? execResult.console : "<output to console is blank>") + "\n\n";
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

    public BatchStatusProcessor prepareStatusAndData(Batch batch, BiFunction<PrepareZipData, File, Boolean> prepareZip, File zipDir) {
        Long batchId = batch.id;
        final BatchStatusProcessor bs = new BatchStatusProcessor();
        bs.originArchiveName = getUploadedFilename(batchId, batch.execContextId);

        Long execContextId = batch.execContextId;
        if (execContextId==null) {
            bs.getGeneralStatus().add("#990.250 Batch #"+batchId+" wasn't linked to ExecContext", '\n');
            bs.ok = true;
            return bs;
        }
        if (true) {
            throw new NotImplementedException("need to re-write algo of collecting of statuses. " +
                    "Old version was using list of exec contexts but new one must use a list of tasks");
        }

        bs.ok = prepareStatus(prepareZip, zipDir, batchId, bs, execContextId);
        initBatchStatus(bs);

        return bs;
    }

    public boolean prepareStatus(BiFunction<PrepareZipData, File, Boolean> prepareZip, File zipDir, Long batchId, BatchStatusProcessor bs, Long execContextId) {
        if (true) {
            throw new NotImplementedException(
                    "Previous version was using list of exec contexts and in this method " +
                            "data was prepared only for one task (there was one task for one execContext)." +
                            "Now we have only one execContext with a number of tasks. So need to re-write to use taskId or something like that.");
        }
        ExecContextImpl wb = execContextCache.findById(execContextId);
        if (wb == null) {
            String msg = "#990.260 Batch #" + batchId + " contains broken execContextId - #" + execContextId;
            bs.getGeneralStatus().add(msg, '\n');
            log.warn(msg);
            return false;
        }
        String mainDocumentPoolCode = getMainDocumentPoolCode(wb);

        final String fullMainDocument = getMainDocumentFilenameForPoolCode(mainDocumentPoolCode, execContextId);
        if (fullMainDocument == null) {
            String msg = "#990.270 " + mainDocumentPoolCode + ", Can't determine actual file name of main document, " +
                    "batchId: " + batchId + ", execContextId: " + execContextId;
            log.warn(msg);
            bs.getGeneralStatus().add(msg, '\n');
            return false;
        }
        final String mainDocument = StrUtils.getName(fullMainDocument) + getActualExtension(wb.getSourceCodeId());

        List<ExecContextData.TaskVertex> taskVertices;
        try {
            taskVertices = execContextGraphTopLevelService.findLeafs(wb);
        } catch (ObjectOptimisticLockingFailureException e) {
            String msg = "#990.167 Can't find tasks for execContextId #" + wb.getId() + ", error: " + e.getMessage();
            log.warn(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        if (taskVertices.isEmpty()) {
            String msg = "#990.290 " + mainDocument + ", Can't find any task for batchId: " + batchId;
            log.info(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        if (taskVertices.size() > 1) {
            String msg = "#990.300 " + mainDocument + ", Can't download file because there are more than one task " +
                    "at the final state, batchId: " + batchId + ", execContextId: " + wb.getId();
            log.info(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        final Task task = taskRepository.findById(taskVertices.get(0).taskId).orElse(null);
        if (task==null) {
            String msg = "#990.303 " + mainDocument + ", Can't find task #" + taskVertices.get(0).taskId;
            log.info(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return false;
        }

        EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());
        FunctionApiData.FunctionExec functionExec;
        try {
            functionExec = FunctionExecUtils.to(task.getFunctionExecResults());
        } catch (YAMLException e) {
            bs.getGeneralStatus().add("#990.310 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                    ", batchId:" + batchId + ", execContextId: " + wb.getId() + ", " +
                    "taskId: " + task.getId(),'\n');
            return false;
        }
        Processor s = null;
        if (task.getProcessorId()!=null) {
            s = processorCache.findById(task.getProcessorId());
        }
        final String processorIpAndHost = getProcessorIpAndHost(s);
        switch (execState) {
            case NONE:
            case IN_PROGRESS:
                bs.getProgressStatus().add("#990.320 " + mainDocument + ", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                                ", batchId:" + batchId + ", execContextId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + ", processorId: " + task.getProcessorId() +
                                ", " + processorIpAndHost
                        ,'\n');
                return true;
            case ERROR:
            case BROKEN:
                bs.getErrorStatus().add(getStatusForError(batchId, wb, mainDocument, task, functionExec, processorIpAndHost));
                return true;
            case OK:
                break;
        }

        if (wb.getState() != EnumsApi.ExecContextState.FINISHED.code) {
            bs.getProgressStatus().add("#990.360 " + mainDocument + ", Task hasn't completed yet, " +
                            "batchId:" + batchId + ", execContextId: " + wb.getId() + ", " +
                            "taskId: " + task.getId() + ", " +
                            "processorId: " + task.getProcessorId() + ", " + processorIpAndHost
                    ,'\n');
            return true;
        }

        PrepareZipData prepareZipData = new PrepareZipData(bs, task, zipDir, mainDocument, batchId, execContextId);
        boolean isOk = prepareZip.apply(prepareZipData, zipDir);
        if (!isOk) {
            return false;
        }

        String msg = "#990.380 status - Ok, doc: " + mainDocument + ", batchId: " + batchId + ", execContextId: " + execContextId +
                ", taskId: " + task.getId() + ", processorId: " + task.getProcessorId() + ", " + processorIpAndHost;
        bs.getOkStatus().add(msg,'\n');
        return true;
    }

    private String getMainDocumentFilenameForPoolCode(String mainDocumentPoolCode, Long execContextId) {
        final List<String> filename = variableService.getFilenameByVariableAndExecContextId(mainDocumentPoolCode, execContextId);
        if (filename==null || filename.isEmpty() || StringUtils.isBlank(filename.get(0))) {
            log.error("#990.390 Filename is blank for poolCode: {}, execContextId: {}", mainDocumentPoolCode, execContextId);
            return null;
        }
        return filename.get(0);
    }

    public String getUploadedFilename(Long batchId, Long execContextId) {
        final List<String> filename = variableService.findFilenameByBatchId(batchId, execContextId);
        if (filename==null || filename.isEmpty() || S.b(filename.get(0))) {
            log.error("#990.392 Filename is blank for batchId: {}, will be used default name - result.zip", batchId);
            return Consts.RESULT_ZIP;
        }
        return filename.get(0);
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

    private String getProcessorIpAndHost(Processor processor) {
        if (processor ==null) {
            return String.format(IP_HOST, Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO);
        }

        ProcessorStatusYaml status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        final String ip = StringUtils.isNotBlank(status.ip) ? status.ip : Consts.UNKNOWN_INFO;
        final String host = StringUtils.isNotBlank(status.host) ? status.host : Consts.UNKNOWN_INFO;

        return String.format(IP_HOST, ip, host);
    }

}
