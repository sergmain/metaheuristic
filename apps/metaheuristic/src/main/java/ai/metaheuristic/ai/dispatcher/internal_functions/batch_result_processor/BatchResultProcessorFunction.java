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
import ai.metaheuristic.ai.dispatcher.batch.BatchService;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
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
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final String DELIMITER_2 = "\n====================================================================\n";
    private static final String IP_HOST = "IP: %s, host: %s";

    private static final String BATCH_ITEM_PROCESSED_FILE = "batch-item-processed-file";
    private static final String BATCH_ITEM_PROCESSING_STATUS = "batch-item-processing-status";
    private static final String BATCH_ITEM_MAPPING = "batch-item-mapping";
    private static final String BATCH_STATUS = "batch-status";
    private static final String BATCH_RESULT = "batch-result";

    private final VariableService variableService;
    private final VariableRepository variableRepository;
    private final BatchService batchService;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;

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

    @SneakyThrows
    @Override
    public InternalFunctionData.InternalFunctionProcessingResult process(
            @NonNull Long sourceCodeId, @NonNull Long execContextId, @NonNull Long taskId, @NonNull String taskContextId,
            @NonNull ExecContextParamsYaml.VariableDeclaration variableDeclaration, @NonNull TaskParamsYaml taskParamsYaml) {

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
        Map<String, ItemWithStatusWithMapping> prepared = groupByTaskContextId(vars, nameToVar, List.of(Consts.TOP_LEVEL_CONTEXT_ID));

        File resultDir = DirUtils.createTempDir("batch-result-processing-");
        File zipDir = new File(resultDir, "zip");
        //noinspection ResultOfMethodCallIgnored
        zipDir.mkdir();

        for (Map.Entry<String, ItemWithStatusWithMapping> entry : prepared.entrySet()) {
            storeResultVariables(zipDir, execContextId, entry.getValue());
        }

        InternalFunctionData.InternalFunctionProcessingResult ifprForStatus = storeGlobalBatchStatus(ec, taskContextId, taskParamsYaml, zipDir);
        if (ifprForStatus != null) {
            return ifprForStatus;
        }

        File zipFile = new File(resultDir, Consts.RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile);

        InternalFunctionData.InternalFunctionProcessingResult ifprForResult = storeBatchResult(execContextId, taskContextId, taskParamsYaml, zipFile);
        if (ifprForResult != null) {
            return ifprForResult;
        }

        return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok);
    }

    @Nullable
    private InternalFunctionData.InternalFunctionProcessingResult storeBatchResult(Long execContextId, String taskContextId, TaskParamsYaml taskParamsYaml, File zipFile) throws IOException {
        String batchResultVarName = taskParamsYaml.task.outputs.stream().filter(o-> BATCH_RESULT.equals(o.type)).findFirst().map(o->o.name).orElse(null);
        if (S.b(batchResultVarName)) {
            return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_with_type_not_found,
                    S.f("#993.010 Variable with type 'batch-result' wasn't found in taskContext %s, execContext #%s", taskContextId, execContextId));
        }
        SimpleVariable batchResult = variableRepository.findByNameAndTaskContextIdAndExecContextId(batchResultVarName, taskContextId, execContextId);
        if (batchResult==null) {
            return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                    S.f("#993.010 Batch result variable %s wasn't found in taskContext %s, execContext #%s", batchResultVarName, taskContextId, execContextId));
        }

        try (FileInputStream fis = new FileInputStream(zipFile)) {
            String originBatchFilename = batchService.findUploadedFilenameForBatchId(execContextId, "batch-result.zip");
            String ext = MetaUtils.getValue(taskParamsYaml.task.metas, ConstsApi.META_MH_RESULT_FILE_EXTENSION);
            if (!S.b(ext)) {
                originBatchFilename = StrUtils.getName(originBatchFilename) + ext;
            }
            variableService.update(fis, zipFile.length(), batchResult, originBatchFilename);
        }
        return null;
    }

    @Nullable
    private InternalFunctionData.InternalFunctionProcessingResult storeGlobalBatchStatus(
            ExecContextImpl execContext, String taskContextId, TaskParamsYaml taskParamsYaml, File zipDir) throws IOException {
        BatchStatusProcessor status = prepareStatus(execContext);

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);

        String batchStatusVarName = taskParamsYaml.task.outputs.stream().filter(o-> BATCH_STATUS.equals(o.type)).findFirst().map(o->o.name).orElse(null);
        if (S.b(batchStatusVarName)) {
            return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_with_type_not_found,
                    S.f("#993.010 Variable with type 'batch-status' wasn't found in taskContext %s, execContext #%s", taskContextId, execContext));
        }
        SimpleVariable batchStatus = variableRepository.findByNameAndTaskContextIdAndExecContextId(batchStatusVarName, taskContextId, execContext.id);
        if (batchStatus==null) {
            return new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                    S.f("#993.010 Batch status variable %s wasn't found in taskContext %s, execContext #%s", batchStatusVarName, taskContextId, execContext));
        }

        byte[] bytes = status.getStatus().getBytes();
        variableService.update(new ByteArrayInputStream(bytes), bytes.length, batchStatus);
        return null;
    }

    /**
     *
     * @param vars - all variables in specific execContext
     * @param nameToVar
     * @param excludeContextIds - list of taskContextIds to exclude
     * @return Map<String, ItemWithStatusWithMapping> - key is taskContextId, value - variables to store as batch item
     */
    private Map<String, ItemWithStatusWithMapping> groupByTaskContextId(
            List<SimpleVariable> vars, Map<String, ExecContextParamsYaml.Variable> nameToVar, List<String> excludeContextIds) {
        Map<String, ItemWithStatusWithMapping> map = new HashMap<>();

        for (SimpleVariable var : vars) {
            if (excludeContextIds.contains(var.taskContextId)) {
                continue;
            }
            //noinspection unused
            ItemWithStatusWithMapping v = map.computeIfAbsent(var.taskContextId, o -> new ItemWithStatusWithMapping(var.taskContextId));
        }

        for (SimpleVariable simpleVariable : vars) {
            ItemWithStatusWithMapping v = map.get(simpleVariable.taskContextId);
            if (v==null) {
                continue;
            }
            ExecContextParamsYaml.Variable varFromExecContext = nameToVar.get(simpleVariable.variable);
            if (varFromExecContext==null) {
                log.error(S.f("Can't find variable %s in execContext", simpleVariable.variable) );
                continue;
            }
            if (S.b(varFromExecContext.type)) {
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
                    log.info(S.f("Skip variable %s with type %s", simpleVariable.variable, varFromExecContext.type));
            }
        }

        for (ItemWithStatusWithMapping value : map.values()) {
            if (value.mapping==null || value.status==null || value.item==null) {
                log.error(S.f("TaskContextId #%s is broken, batch doesn't contain all variables, ItemWithStatusWithMapping: %s", value.taskContextId, value));
            }
        }
        return map;
    }

    private Map<String, ExecContextParamsYaml.Variable> gatherVars(ExecContextParamsYaml ecpy) {
        Map<String, ExecContextParamsYaml.Variable> map = ecpy.processes.stream()
                .flatMap(o->o.outputs.stream())
                .collect(Collectors.toMap(o->o.name, o->o, (a, b) -> b, HashMap::new));
        return map;
    }

    private void storeResultVariables(File zipDir, Long execContextId, ItemWithStatusWithMapping item) {

        if (item.mapping==null || item.status==null || item.item==null) {
            log.error(S.f("TaskContextId #%s has been skipped, ItemWithStatusWithMapping: %s", item.taskContextId, item));
            return;
        }

        BatchItemMappingYaml bimy = new BatchItemMappingYaml();
        bimy.targetDir = getResultDirNameFromTaskContextId(item.taskContextId);

        if (item.mapping!=null) {
            SimpleVariable sv = item.mapping;
            try {
                String mapping = variableService.getVariableDataAsString(sv.id);
                bimy = BatchItemMappingYamlUtils.BASE_YAML_UTILS.to(mapping);
            } catch (CommonErrorWithDataException e) {
                log.warn(S.f("#993.120 no mapping variables with id were found in execContextId #%s", sv.id, execContextId));
            }
        }

        File resultDir = new File(zipDir, bimy.targetDir);
        resultDir.mkdir();

        storeVariableToFile(bimy, resultDir, item.item);
        storeVariableToFile(bimy, resultDir, item.status);
    }

    private void storeVariableToFile(BatchItemMappingYaml bimy, File resultDir, SimpleVariable simpleVariable) {
        String itemFilename = bimy.filenames.get(simpleVariable.id.toString());
        if (S.b(itemFilename)) {
            itemFilename = simpleVariable.id.toString();
        }
        File file = new File(resultDir, itemFilename);
        variableService.storeToFile(simpleVariable.id, file);
    }

    private String getResultDirNameFromTaskContextId(String taskContextId) {
        return S.f("result-dir-%s", StringUtils.replaceEach(taskContextId, new String[]{",", "#"}, new String[]{"_", "-"}));
    }

    /**
     * Don't forget to call this method before storing in db
     * @param batchStatus
     */
    private static void initBatchStatus(BatchStatusProcessor batchStatus) {
        String generalStr = batchStatus.getGeneralStatus().asString();
        if (!generalStr.isBlank()) {
            batchStatus.status = generalStr + DELIMITER_2;
        }
        String progressStr = batchStatus.getProgressStatus().asString();
        if (!progressStr.isBlank()) {
            batchStatus.status += progressStr + DELIMITER_2;
        }
        String okStr = batchStatus.getOkStatus().asString();
        if (!okStr.isBlank()) {
            batchStatus.status += okStr + DELIMITER_2;
        }
        String errorStr = batchStatus.getErrorStatus().asString();
        if (!errorStr.isBlank()) {
            batchStatus.status += errorStr + DELIMITER_2;
        }
    }

    private BatchStatusProcessor prepareStatus(ExecContextImpl ec) {
        final BatchStatusProcessor bs = new BatchStatusProcessor();
        bs.ok = true;

        List<ExecContextData.TaskVertex> taskVertices = execContextGraphService.getAllTasksTopologically(ec);
        for (ExecContextData.TaskVertex taskVertex : taskVertices) {
            if (taskVertex.execState==EnumsApi.TaskExecState.NONE || taskVertex.execState== EnumsApi.TaskExecState.IN_PROGRESS) {
                continue;
            }
            storeStatusOfTask(bs, ec, taskVertex.taskId);
        }
        initBatchStatus(bs);
        return bs;
    }

    private void storeStatusOfTask(BatchStatusProcessor bs, ExecContextImpl ec, Long taskId) {
        final TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            String msg = "#993.280 Can't find task #" + taskId;
            log.info(msg);
            bs.getGeneralStatus().add(msg,'\n');
            return;
        }

        EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

        final String processorIpAndHost;
        String processorId;
        if (tpy.task.context== EnumsApi.FunctionExecContext.external) {
            processorId = S.f("processorId: %s", task.getProcessorId());
            Processor s = null;
            if (task.getProcessorId() != null) {
                s = processorCache.findById(task.getProcessorId());
            }
            processorIpAndHost = getProcessorIpAndHost(s);
        }
        else {
            processorIpAndHost = "Internal function";
            processorId = "Dispatcher";
        }

        String func = tpy.task.function.code;
        switch (execState) {
            case NONE:
            case IN_PROGRESS:
                bs.getProgressStatus().add(
                        S.f("#993.300 Task #%s hasn't completed yet, function: %s, status: %s, execContextId: %s, %s, %s",
                                taskId, func, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, processorId, processorIpAndHost)
                        ,'\n');
                bs.ok = true;
                return;
            case ERROR:
                FunctionApiData.FunctionExec functionExec;
                try {
                    functionExec = FunctionExecUtils.to(task.getFunctionExecResults());
                } catch (YAMLException e) {
                    bs.getGeneralStatus().add(
                            S.f("#993.320 Task #%s has broken console output, function: %s, status: %s, execContextId: %s, %s, %s",
                                    taskId, func, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, processorId, processorIpAndHost)
                            ,'\n');
                    return;
                }
                if (functionExec==null) {
                    bs.getGeneralStatus().add(
                            S.f("#993.340 Task #%s has broken console output, function: %s, status: %s, execContextId: %s, %s, %s",
                                    taskId, func, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, processorId, processorIpAndHost)
                            ,'\n');
                    return;
                }
                String statusForError = getStatusForError(ec, task, functionExec, processorIpAndHost);
                bs.getErrorStatus().add(statusForError);
                bs.ok = true;
                return;
            case OK:
                break;
            case SKIPPED:
                bs.getProgressStatus().add(
                        S.f("#993.300 Task #%s was skipped yet, function: %s, status: %s, execContextId: %s, %s, %s",
                                taskId, func, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, processorId, processorIpAndHost)
                        ,'\n');
                bs.ok = true;
                return;
        }

        bs.getOkStatus().add(
                S.f("#993.300 Task #%s was completed successfully, function: %s, status: %s, execContextId: %s, %s, %s",
                        taskId, func, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, processorId, processorIpAndHost)
                ,'\n');
        bs.ok = true;
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

    private static String getStatusForError(ExecContextImpl ec, TaskImpl task, FunctionApiData.FunctionExec functionExec, String processorIpAndHost) {

        final String header =
                S.f("#993.400 Task #%s was completed with an error, , status: %s, execContextId: %s, processorId: %s, %s\n\n",
                        task.id, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, task.getProcessorId(), processorIpAndHost);

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

    private static String execResultAsStr(FunctionApiData.SystemExecResult execResult) {
        return
                "function: " + execResult.functionCode + "\n" +
                        "isOk: " + execResult.isOk + "\n" +
                        "exitCode: " + execResult.exitCode + "\n" +
                        "console:\n" + (StringUtils.isNotBlank(execResult.console) ? execResult.console : "<output to console is blank>") + "\n\n";
    }

}
