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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_result_processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.batch.BatchRepository;
import ai.metaheuristic.ai.dispatcher.batch.BatchService;
import ai.metaheuristic.ai.dispatcher.batch.BatchSyncService;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 8:06 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchResultProcessorFunction implements InternalFunction {

    private static final String DELEMITER_2 = "\n====================================================================\n";
    private static final String IP_HOST = "IP: %s, host: %s";

    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final BatchService batchService;
    private final BatchRepository batchRepository;
    private final BatchCache batchCache;
    private final BatchSyncService batchSyncService;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphTopLevelService  execContextGraphTopLevelService;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final DispatcherEventService dispatcherEventService;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_RESULT_PROCESSOR_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_RESULT_PROCESSOR_FUNCTION;
    }

    @Override
    public InternalFunctionData.InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, Long taskId, String taskContextId,
            ExecContextParamsYaml.VariableDeclaration variableDeclaration, TaskParamsYaml taskParamsYaml) {

        throw new NotImplementedException("not yet");
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

    @Nullable
    public CleanerInfo getBatchProcessingResult(Long batchId, DispatcherContext context, boolean includeDeleted) throws IOException {
        return getBatchProcessingResult(batchId, context.getCompanyId(), includeDeleted);
    }

    @Nullable
    public CleanerInfo getBatchProcessingResult(Long batchId, Long companyUniqueId, boolean includeDeleted) throws IOException {
        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(companyUniqueId) ||
                (!includeDeleted && batch.deleted)) {
            final String es = "#995.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return null;
        }
        CleanerInfo resource = new CleanerInfo();

        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
        resource.toClean.add(resultDir);

        File zipDir = new File(resultDir, "zip");
        //noinspection ResultOfMethodCallIgnored
        zipDir.mkdir();

        BatchStatusProcessor status = prepareStatusAndData(batch, this::prepareZip, zipDir);

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);
        File zipFile = new File(resultDir, Consts.RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile, status.renameTo);


        String filename = StrUtils.getName(status.originArchiveName) + Consts.ZIP_EXT;

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        // https://stackoverflow.com/questions/93551/how-to-encode-the-filename-parameter-of-content-disposition-header-in-http
        httpHeaders.setContentDisposition(ContentDisposition.parse(
                "filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())));
        resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile), RestUtils.getHeader(httpHeaders, zipFile.length()), HttpStatus.OK);
        return resource;
    }

    // todo 2020-02-25 this method has to be re-written completely
    private boolean prepareZip(BatchService.PrepareZipData prepareZipData, File zipDir ) {
        if (true) {
            throw new NotImplementedException("Previous version was using list of exec contexts and in this method " +
                    "data was prepared only for one task (there was one task for one execContext)." +
                    "Not we have only one execContext with a number of tasks. So need to re-write to use taskId or something like that.");
        }
        final TaskParamsYaml taskParamYaml;
        try {
            taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(prepareZipData.task.getParams());
        } catch (YAMLException e) {
            prepareZipData.bs.getErrorStatus().add(
                    "#990.350 " + prepareZipData.mainDocument + ", " +
                            "Task has broken data in params, status: " + EnumsApi.TaskExecState.from(prepareZipData.task.getExecState()) +
                            ", batchId:" + prepareZipData.batchId +
                            ", execContextId: " + prepareZipData.execContextId + ", " +
                            "taskId: " + prepareZipData.task.getId(), '\n');
            return false;
        }

        File tempFile;
        try {
            tempFile = File.createTempFile("doc-", ".xml", zipDir);
        } catch (IOException e) {
            String msg = "#990.370 Error create a temp file in "+zipDir.getAbsolutePath();
            log.error(msg);
            prepareZipData.bs.getGeneralStatus().add(msg,'\n');
            return false;
        }

        // all documents are sorted in zip folder
        prepareZipData.bs.renameTo.put("zip/" + tempFile.getName(), "zip/" + prepareZipData.mainDocument);


        // TODO 2020-01-30 need to re-write
/*
        try {
            variableService.storeToFile(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(), tempFile);
        } catch (CommonErrorWithDataException e) {
            String msg = "#990.375 Error store data to temp file, data doesn't exist in db, code " +
                    taskParamYaml.taskYaml.outputResourceIds.values().iterator().next() +
                    ", file: " + tempFile.getPath();
            log.error(msg);
            prepareZipData.bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
*/
        return true;
    }

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

    public BatchStatusProcessor prepareStatusAndData(Batch batch, BiFunction<BatchService.PrepareZipData, File, Boolean> prepareZip, @Nullable File zipDir) {
        Long batchId = batch.id;
        final BatchStatusProcessor bs = new BatchStatusProcessor();
        bs.originArchiveName = batchService.getUploadedFilename(batchId);

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

    public boolean prepareStatus(BiFunction<BatchService.PrepareZipData, File, Boolean> prepareZip, @Nullable File zipDir, Long batchId, BatchStatusProcessor bs, Long execContextId) {
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

        // lets find the actual name of target file
        String mainDocumentPoolCode = "";
        String mainDocument = "";
/*
        final String fullMainDocument = getMainDocumentFilenameForPoolCode(mainDocumentPoolCode, execContextId);
        if (fullMainDocument == null) {
            String msg = "#990.270 " + mainDocumentPoolCode + ", Can't determine actual file name of main document, " +
                    "batchId: " + batchId + ", execContextId: " + execContextId;
            log.warn(msg);
            bs.getGeneralStatus().add(msg, '\n');
            return false;
        }
        final String mainDocument = StrUtils.getName(fullMainDocument) + getActualExtension(wb.getSourceCodeId());
*/

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
                FunctionApiData.FunctionExec functionExec;
                try {
                    functionExec = FunctionExecUtils.to(task.getFunctionExecResults());
                } catch (YAMLException e) {
                    bs.getGeneralStatus().add("#990.310 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                            ", batchId:" + batchId + ", execContextId: " + wb.getId() + ", " +
                            "taskId: " + task.getId(),'\n');
                    return false;
                }
                if (functionExec==null) {
                    bs.getGeneralStatus().add("#990.310 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                            ", batchId:" + batchId + ", execContextId: " + wb.getId() + ", " +
                            "taskId: " + task.getId(),'\n');
                    return false;
                }
                String statusForError = getStatusForError(batchId, wb, mainDocument, task, functionExec, processorIpAndHost);
                bs.getErrorStatus().add(statusForError);
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

        BatchService.PrepareZipData prepareZipData = new BatchService.PrepareZipData(bs, task, zipDir, mainDocument, batchId, execContextId);
        boolean isOk = prepareZip.apply(prepareZipData, zipDir);
        if (!isOk) {
            return false;
        }

        String msg = "#990.380 status - Ok, doc: " + mainDocument + ", batchId: " + batchId + ", execContextId: " + execContextId +
                ", taskId: " + task.getId() + ", processorId: " + task.getProcessorId() + ", " + processorIpAndHost;
        bs.getOkStatus().add(msg,'\n');
        return true;
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

    private String getStatusForError(Long batchId, ExecContext ec, String mainDocument, Task task, @NonNull FunctionApiData.FunctionExec functionExec, String processorIpAndHost) {

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

}
