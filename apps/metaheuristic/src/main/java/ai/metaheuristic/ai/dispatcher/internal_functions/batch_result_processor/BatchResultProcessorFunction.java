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
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.batch.BatchRepository;
import ai.metaheuristic.ai.dispatcher.batch.BatchService;
import ai.metaheuristic.ai.dispatcher.batch.BatchSyncService;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYaml;
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 4/19/2020
 * Time: 8:06 PM
 */
@SuppressWarnings("unused")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchResultProcessorFunction implements InternalFunction {

    private static final String DELEMITER_2 = "\n====================================================================\n";
    private static final String IP_HOST = "IP: %s, host: %s";

    private static final String BATCH_ITEM_PROCESSED_FILE = "batch-item-processed-file";
    private static final String BATCH_ITEM_PROCESSING_STATUS = "batch-item-processing-status";
    private static final String BATCH_ITEM_MAPPING = "batch-item-mapping";
    private static final String BATCH_STATUS = "batch-status";

    private final VariableService variableService;
    private final VariableRepository variableRepository;
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

    @Data
    public static class VarWithType {
        public String name;
        public String type;
    }

    @Data
    public static class ItemWithStatusWithMapping {
        public String taskContextId;
        public SimpleVariable item;
        public SimpleVariable status;
        public SimpleVariable mapping;

        public ItemWithStatusWithMapping(String taskContextId) {
            this.taskContextId = taskContextId;
        }
    }

    @SuppressWarnings("UnusedAssignment")
    @SneakyThrows
    @Override
    public InternalFunctionData.InternalFunctionProcessingResult process(
            @NonNull Long sourceCodeId, @NonNull Long execContextId, @NonNull Long taskId, @NonNull String taskContextId,
            @NonNull ExecContextParamsYaml.VariableDeclaration variableDeclaration, @NonNull TaskParamsYaml taskParamsYaml) {

//        - name: var-processed-file-1
//          type: batch-item-processed-file
//        - name: var-processing-status-1
//          type: batch-item-processing-status
//        - name: var-item-mapping-1
//          type: batch-item-mapping

        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.exec_context_not_found,
                    S.f("#993.010 ExecContext #%s wasn't found", execContextId));
        }

        ExecContextParamsYaml ecpy = ec.getExecContextParamsYaml();
        // key is name of variable
        Map<String, ExecContextParamsYaml.Variable> nameToVar = gatherVars(ecpy);
        Set<String> varNames = nameToVar.keySet();
        List<SimpleVariable> vars = variableRepository.findByExecContextIdAndNames(execContextId, varNames);

        // key is taskContextId
        Map<String, ItemWithStatusWithMapping> prepared = groupByTaskContextId(vars, nameToVar);

        String batchStatusVarName = taskParamsYaml.task.outputs.stream().filter(o-> BATCH_STATUS.equals(o.type)).findFirst().map(o->o.name)
                .orElse(S.f("batch-status-%s-%s", execContextId, UUID.randomUUID().toString()));

        File resultDir = DirUtils.createTempDir("batch-result-processing-");
        File zipDir = new File(resultDir, "zip");
        //noinspection ResultOfMethodCallIgnored
        zipDir.mkdir();

        BatchStatusProcessor status = prepareStatus(execContextId, this::prepareZip, zipDir);

        for (Map.Entry<String, ItemWithStatusWithMapping> entry : prepared.entrySet()) {
            storeResultVariables(zipDir, execContextId, entry.getValue());
        }

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);
        File zipFile = new File(resultDir, Consts.RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile, status.renameTo);

        Variable v;
        byte[] bytes = status.getStatus().getBytes();
        v = variableService.createInitialized(new ByteArrayInputStream(bytes), bytes.length, batchStatusVarName, "status.txt", execContextId, taskContextId);
        try (FileInputStream fis = new FileInputStream(zipFile)) {
            String originBatchFilename = batchService.findUploadedFilenameForBatchId(execContextId, "batch-result.zip");
            v = variableService.createInitialized(fis, zipFile.length(), batchStatusVarName, originBatchFilename, execContextId, taskContextId);
        }

        return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok);
    }

    private Map<String, ItemWithStatusWithMapping> groupByTaskContextId(List<SimpleVariable> vars, Map<String, ExecContextParamsYaml.Variable> nameToVar) {
        Map<String, ItemWithStatusWithMapping> map = new HashMap<>();

        for (SimpleVariable var : vars) {
            ItemWithStatusWithMapping v = map.computeIfAbsent(var.taskContextId, o->new ItemWithStatusWithMapping(var.taskContextId));
            for (SimpleVariable simpleVariable : vars) {
                if (v.taskContextId.equals(simpleVariable.taskContextId)) {
                    continue;
                }
                ExecContextParamsYaml.Variable varFromExecContext = nameToVar.get(simpleVariable.variable);
                if (varFromExecContext==null) {
                    log.error(S.f("Can't find variable %s in execContext",simpleVariable.variable) );
                    continue;
                }
                if (S.b(varFromExecContext.type)) {
                    log.warn(S.f("Variable %s doesn't have a type",simpleVariable.variable));
                    continue;
                }

                switch(varFromExecContext.type) {
                    case BATCH_ITEM_PROCESSED_FILE:
                        v.item = simpleVariable;
                        break;
                    case BATCH_ITEM_PROCESSING_STATUS:
                        v.status = simpleVariable;
                        break;
                    case BATCH_ITEM_MAPPING:
                        v.mapping = simpleVariable;
                        break;
                    default:
                        log.error(S.f("Unknown type %s in %s variable", varFromExecContext.type, simpleVariable.variable));
                }
            }
        }

        for (ItemWithStatusWithMapping value : map.values()) {
            if (value.mapping==null || value.status==null || value.item==null) {
                log.error(S.f("TaskContextId #%s is broken, ItemWithStatusWithMapping: %s", value.taskContextId, value));
            }
        }
        return map;
    }

    private Map<String, ExecContextParamsYaml.Variable> gatherVars(ExecContextParamsYaml ecpy) {
        Map<String, ExecContextParamsYaml.Variable> map = ecpy.processes.stream()
                .flatMap(o->o.outputs.stream())
                .collect(Collectors.toMap(o->o.name, o->o, (a, b) -> b, HashMap::new));;
        return map;
    }

    private Set<String> collectTaskContextIds(Long execContextId, String[] batchItems, String batchResult, String batchStatus, @Nullable String batchDirMapping) {
        Set<String> vars = new HashSet<>(Arrays.asList(batchItems));
        vars.add(batchResult);
        vars.add(batchStatus);
        if (!S.b(batchDirMapping)) {
            vars.add(batchDirMapping);
        }

        Set<String> taskContextIds = variableService.findAllByExecContextIdAndVariableNames(execContextId, vars);
        return taskContextIds;
    }

    private void storeResultVariables(File zipDir, Long execContextId, ItemWithStatusWithMapping item) {

        if (item.mapping==null || item.status==null || item.item==null) {
            log.error(S.f("TaskContextId #%s has been skipped, ItemWithStatusWithMapping: %s", item.taskContextId, item));
            return;
        }

        String resultDirName;
        BatchItemMappingYaml bimy = new BatchItemMappingYaml();
        bimy.targetDir = getResultDirNameFromTaskContextId(item.taskContextId);

        if (item.mapping!=null) {
            SimpleVariable sv = item.mapping;
            try {
                String mapping = variableService.getVariableDataAsString(sv.id);
                bimy = BatchItemMappingYamlUtils.BASE_YAML_UTILS.to(mapping);
            } catch (CommonErrorWithDataException e) {
                log.warn(S.f("#993.120 no mapping variables with id were found in execContextId #%s", sv.id, execContextId));
                resultDirName = getResultDirNameFromTaskContextId(item.taskContextId);
            }
        }

        File resultDir = new File(zipDir, bimy.targetDir);
        resultDir.mkdir();

        storeVariableToFile(bimy, resultDir, item.item);
        storeVariableToFile(bimy, resultDir, item.status);
    }

    private void storeVariableToFile(BatchItemMappingYaml bimy, File resultDir, SimpleVariable simpleVariable) {
        String itemFilename = bimy.realNames.get(simpleVariable.id.toString());
        if (S.b(itemFilename)) {
            itemFilename = simpleVariable.id.toString();
        }
        File file = new File(resultDir, itemFilename);
        variableService.storeToFile(simpleVariable.id, file);
    }

    @NonNull
    private String getResultDirNameFromTaskContextId(String taskContextId) {
        return S.f("result-dir-%s", StringUtils.replaceEach(taskContextId, new String[]{",", "#"}, new String[]{"_", "-"}));
    }

    @SuppressWarnings("unused")
    public Batch changeStateToError(Long batchId, String error) {
        return batchSyncService.getWithSync(batchId, (b) -> {
            try {
                if (b == null) {
                    log.warn("#993.140 batch not found in db, batchId: #{}", batchId);
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

    private boolean prepareZip(BatchService.PrepareZipData prepareZipData, File zipDir ) {
        final TaskParamsYaml taskParamYaml;
        try {
            taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(prepareZipData.task.getParams());
        } catch (YAMLException e) {
            prepareZipData.bs.getErrorStatus().add(
                    "#993.160 " + prepareZipData.mainDocument + ", " +
                            "Task has broken data in params, status: " + EnumsApi.TaskExecState.from(prepareZipData.task.getExecState()) +
                            ", execContextId: " + prepareZipData.execContextId + ", " +
                            "taskId: " + prepareZipData.task.getId(), '\n');
            return false;
        }

        File tempFile;
        try {
            tempFile = File.createTempFile("doc-", ".xml", zipDir);
        } catch (IOException e) {
            String msg = "#993.180 Error create a temp file in "+zipDir.getAbsolutePath();
            log.error(msg);
            prepareZipData.bs.getGeneralStatus().add(msg,'\n');
            return false;
        }

        // all documents are sorted in zip folder
        prepareZipData.bs.renameTo.put("zip/" + tempFile.getName(), "zip/" + prepareZipData.mainDocument);

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

    public BatchStatusProcessor prepareStatus(Long execContextId, BiFunction<BatchService.PrepareZipData, File, Boolean> prepareZip, @Nullable File zipDir) {
        final BatchStatusProcessor bs = new BatchStatusProcessor();
        bs.ok = prepareStatus(prepareZip, zipDir, bs, execContextId);
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

        BatchStatusProcessor batchStatus = prepareStatus(b.execContextId, (prepareZipData, file) -> true, null);
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

    public boolean prepareStatus(BiFunction<BatchService.PrepareZipData, File, Boolean> prepareZip, @Nullable File zipDir, BatchStatusProcessor bs, Long execContextId) {
        ExecContextImpl wb = execContextCache.findById(execContextId);
        if (wb == null) {
            String msg = "#993.200 ExecContext #" + execContextId + " wasn't found";
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
            String msg = "#993.220 Can't find tasks for execContextId #" + wb.getId() + ", error: " + e.getMessage();
            log.warn(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        if (taskVertices.isEmpty()) {
            String msg = "#993.240 " + mainDocument + ", Can't find any task for execContextId: " + execContextId;
            log.info(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        if (taskVertices.size() > 1) {
            String msg = "#993.260 " + mainDocument + ", Can't download file because there are more than one task " +
                    "at the final state, execContextId: " + execContextId + ", execContextId: " + wb.getId();
            log.info(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        final Task task = taskRepository.findById(taskVertices.get(0).taskId).orElse(null);
        if (task==null) {
            String msg = "#993.280 " + mainDocument + ", Can't find task #" + taskVertices.get(0).taskId;
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
                bs.getProgressStatus().add("#993.300 " + mainDocument + ", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                                ", execContextId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + ", processorId: " + task.getProcessorId() +
                                ", " + processorIpAndHost
                        ,'\n');
                return true;
            case ERROR:
                FunctionApiData.FunctionExec functionExec;
                try {
                    functionExec = FunctionExecUtils.to(task.getFunctionExecResults());
                } catch (YAMLException e) {
                    bs.getGeneralStatus().add("#993.320 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                            ", execContextId: " + wb.getId() + ", " +
                            "taskId: " + task.getId(),'\n');
                    return false;
                }
                if (functionExec==null) {
                    bs.getGeneralStatus().add("#993.340 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                            ", execContextId: " + wb.getId() + ", " +
                            "taskId: " + task.getId(),'\n');
                    return false;
                }
                String statusForError = getStatusForError(wb, mainDocument, task, functionExec, processorIpAndHost);
                bs.getErrorStatus().add(statusForError);
                return true;
            case OK:
                break;
            case SKIPPED:
                // todo 2020-08-16 need to decide what to do here
                break;
        }

        if (wb.getState() != EnumsApi.ExecContextState.FINISHED.code) {
            bs.getProgressStatus().add("#993.360 " + mainDocument + ", Task hasn't completed yet, " +
                            "execContextId: " + wb.getId() + ", " +
                            "taskId: " + task.getId() + ", " +
                            "processorId: " + task.getProcessorId() + ", " + processorIpAndHost
                    ,'\n');
            return true;
        }

        BatchService.PrepareZipData prepareZipData = new BatchService.PrepareZipData(bs, task, zipDir, mainDocument, execContextId);
        boolean isOk = prepareZip.apply(prepareZipData, zipDir);
        if (!isOk) {
            return false;
        }

        String msg = "#993.380 status - Ok, doc: " + mainDocument + ", execContextId: " + execContextId +
                ", taskId: " + task.getId() + ", processorId: " + task.getProcessorId() + ", " + processorIpAndHost;
        bs.getOkStatus().add(msg,'\n');
        return true;
    }

    private String getProcessorIpAndHost(@Nullable Processor processor) {
        if (processor ==null) {
            return String.format(IP_HOST, Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO);
        }

        ProcessorStatusYaml status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        final String ip = StringUtils.isNotBlank(status.ip) ? status.ip : Consts.UNKNOWN_INFO;
        final String host = StringUtils.isNotBlank(status.host) ? status.host : Consts.UNKNOWN_INFO;

        return String.format(IP_HOST, ip, host);
    }

    private String getStatusForError(ExecContext ec, String mainDocument, Task task, @NonNull FunctionApiData.FunctionExec functionExec, String processorIpAndHost) {

        final String header =
                "#993.400 " + mainDocument + ", Task was completed with an error, execContextId: " + ec.getId() + ", " +
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
