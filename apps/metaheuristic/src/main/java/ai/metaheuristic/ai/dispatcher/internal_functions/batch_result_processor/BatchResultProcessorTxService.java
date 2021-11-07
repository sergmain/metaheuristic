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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_result_processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchHelperService;
import ai.metaheuristic.ai.dispatcher.batch.BatchService;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.event.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 3/24/2021
 * Time: 7:08 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchResultProcessorTxService {

    private static final String DELIMITER_2 = "\n====================================================================\n";
    private static final String IP_HOST = "IP: %s, host: %s";

    private static final String BATCH_STATUS = "batch-status";
    private static final String BATCH_RESULT = "batch-result";

    private static final String BATCH_ITEM_PROCESSED_FILE = "batch-type-processed-file";
    private static final String BATCH_ITEM_PROCESSING_STATUS = "batch-type-processing-status";
    private static final String BATCH_ITEM_MAPPING = "batch-type-mapping";


    private final Globals globals;
    private final VariableService variableService;
    private final VariableRepository variableRepository;
    private final ExecContextGraphService execContextGraphService;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final SourceCodeCache sourceCodeCache;
    private final BatchHelperService batchHelperService;
    private final ExecContextCache execContextCache;
    private final ApplicationEventPublisher eventPublisher;


    @Data
    public static class ItemWithStatusWithMapping {
        public String taskContextId;
        public List<SimpleVariable> items = new ArrayList<>();
        public SimpleVariable status;
        public SimpleVariable mapping;

        public ItemWithStatusWithMapping(String taskContextId) {
            this.taskContextId = taskContextId;
        }
    }

    @Transactional
    public Void process(
            ExecContextData.SimpleExecContext simpleExecContext, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        ExecContextSyncService.checkWriteLockPresent(simpleExecContext.execContextId);

        ExecContextImpl execContext = execContextCache.findById(simpleExecContext.execContextId);
        if (execContext==null) {
            return null;
        }

        // key is name of variable
        Map<String, ExecContextParamsYaml.Variable> nameToVar = gatherVars(simpleExecContext.paramsYaml);
        Set<String> varNames = nameToVar.keySet();
        List<SimpleVariable> vars = variableRepository.findByExecContextIdAndNames(simpleExecContext.execContextId, varNames);

        String processedFileTypes = MetaUtils.getValue(taskParamsYaml.task.metas, BATCH_ITEM_PROCESSED_FILE);
        if (S.b(processedFileTypes)) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                            S.f("#993.025 Meta '%s' wasn't found in ExecContext #%s", BATCH_ITEM_PROCESSED_FILE, simpleExecContext.execContextId)));
        }
        final List<String> outputTypes = Stream.of(StringUtils.split(processedFileTypes, ", ")).collect(Collectors.toList());

        String statusFileTypes = MetaUtils.getValue(taskParamsYaml.task.metas, BATCH_ITEM_PROCESSING_STATUS);
        if (S.b(statusFileTypes)) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.meta_not_found,
                            S.f("#993.027 Meta '%s' wasn't found in ExecContext #%s", BATCH_ITEM_PROCESSING_STATUS, simpleExecContext.execContextId));
        }
        final List<String> statusTypes = Stream.of(StringUtils.split(statusFileTypes, ", ")).collect(Collectors.toList());

        String mappingFileTypes = MetaUtils.getValue(taskParamsYaml.task.metas, BATCH_ITEM_MAPPING);
        final List<String> mappingTypes;
        if (S.b(mappingFileTypes)) {
            log.info(S.f("#993.029 Meta '%s' wasn't found in ExecContext #%s", BATCH_ITEM_MAPPING, simpleExecContext.execContextId));
            mappingTypes = List.of();
        }
        else {
            mappingTypes = Stream.of(StringUtils.split(mappingFileTypes, ", ")).collect(Collectors.toList());
        }

        // key is taskContextId
        Map<String, ItemWithStatusWithMapping> prepared = groupByTaskContextId(vars, nameToVar, List.of(Consts.TOP_LEVEL_CONTEXT_ID),
                outputTypes::contains, statusTypes::contains, mappingTypes::contains);

        File resultDir = DirUtils.createTempDir("batch-result-processing-");
        if (resultDir == null) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, "#993.030 temp can't be created");
        }
        eventPublisher.publishEvent(new ResourceCloseTxEvent(resultDir));

        File zipDir = new File(resultDir, "zip");
        //noinspection ResultOfMethodCallIgnored
        zipDir.mkdir();

        storeGlobalBatchStatus(execContext, taskContextId, taskParamsYaml, zipDir);

        Map<String, List<ExecContextData.TaskWithState>> vertices = execContextGraphService.findVerticesByTaskContextIds(
                simpleExecContext.execContextGraphId, simpleExecContext.execContextTaskStateId, prepared.keySet());
        for (Map.Entry<String, List<ExecContextData.TaskWithState>> entry : vertices.entrySet()) {
            boolean isOK = entry.getValue().stream().noneMatch(o->o.state!= EnumsApi.TaskExecState.OK);
            if (isOK) {
                ItemWithStatusWithMapping item = prepared.get(entry.getKey());
                storeResultVariables(zipDir, simpleExecContext.execContextId, item);
            }
        }

        File zipFile = new File(resultDir, Consts.RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile);

        storeBatchResult(simpleExecContext.sourceCodeId, simpleExecContext.execContextId, simpleExecContext.paramsYaml, taskContextId, taskParamsYaml, zipFile);
        return null;
    }

    @SneakyThrows
    private void storeBatchResult(
            Long sourceCodeId, Long execContextId, ExecContextParamsYaml ecpy, String taskContextId, TaskParamsYaml taskParamsYaml,
            File zipFile) {
        String batchResultVarName = taskParamsYaml.task.outputs.stream().filter(o-> BATCH_RESULT.equals(o.type)).findFirst().map(o->o.name).orElse(null);
        if (S.b(batchResultVarName)) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_with_type_not_found,
                            S.f("#993.040 Variable with type 'batch-result' wasn't found in taskContext %s, execContext #%s", taskContextId, execContextId)));
        }
        SimpleVariable batchResultVar = variableRepository.findByNameAndTaskContextIdAndExecContextId(batchResultVarName, taskContextId, execContextId);
        if (batchResultVar==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                            S.f("#993.060 Batch result variable %s wasn't found in taskContext %s, execContext #%s", batchResultVarName, taskContextId, execContextId)));
        }
        SourceCodeImpl sc = sourceCodeCache.findById(sourceCodeId);
        if (sc==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_not_found,
                            S.f("#993.080 Can't find SourceCode #%s", sourceCodeId)));
        }

        String originBatchFilename = batchHelperService.findUploadedFilenameForBatchId(execContextId, ecpy, "batch-result.zip");
        String ext = BatchService.getActualExtension(sc.getSourceCodeStoredParamsYaml(), globals.dispatcher.defaultResultFileExtension);
        if (!S.b(ext)) {
            originBatchFilename = StrUtils.getName(originBatchFilename) + ext;
        }

        // this stream will be closed outside of this transaction
        FileInputStream fis = new FileInputStream(zipFile);
        eventPublisher.publishEvent(new ResourceCloseTxEvent(fis));

        variableService.storeData(fis, zipFile.length(), batchResultVar.id, originBatchFilename);
    }

    @SneakyThrows
    private void storeGlobalBatchStatus(
            ExecContextImpl execContext, String taskContextId, TaskParamsYaml taskParamsYaml, File zipDir) {
        // TODO 2021-03-23 refactor as event
        BatchStatusProcessor status = prepareStatus(execContext);

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);

        String batchStatusVarName = taskParamsYaml.task.outputs.stream().filter(o-> BATCH_STATUS.equals(o.type)).findFirst().map(o->o.name).orElse(null);
        if (S.b(batchStatusVarName)) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_with_type_not_found,
                            S.f("#993.100 Variable with type 'batch-status' wasn't found in taskContext %s, execContext #%s", taskContextId, execContext.id)));
        }
        SimpleVariable batchStatusVar = variableRepository.findByNameAndTaskContextIdAndExecContextId(batchStatusVarName, taskContextId, execContext.id);
        if (batchStatusVar==null) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                            S.f("#993.120 Batch status variable %s wasn't found in taskContext %s, execContext #%s", batchStatusVarName, taskContextId, execContext.id)));
        }

        byte[] bytes = status.getStatus().getBytes();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        // we fire this event to be sure that ref to ByteArrayInputStream live longer than TX
        eventPublisher.publishEvent(new ResourceCloseTxEvent(inputStream));

        variableService.storeData(inputStream, bytes.length, batchStatusVar.id, null);
    }

    /**
     *
     * @param vars - all variables in specific execContext
     * @param nameToVar
     * @param excludeContextIds - list of taskContextIds to exclude
     * @return Map<String, ItemWithStatusWithMapping> - key is taskContextId, value - variables to store as batch item
     */
    private static Map<String, ItemWithStatusWithMapping> groupByTaskContextId(
            List<SimpleVariable> vars, Map<String, ExecContextParamsYaml.Variable> nameToVar, List<String> excludeContextIds,
            Function<String, Boolean> outputTypeFunc, Function<String, Boolean> statusTypeFunc, Function<String, Boolean> mappingTypeFunc) {
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
                log.error(S.f("#993.140 Can't find variable %s in execContext", simpleVariable.variable) );
                continue;
            }
            if (S.b(varFromExecContext.type)) {
                continue;
            }

            if (outputTypeFunc.apply(varFromExecContext.type)) {
                v.items.add(simpleVariable);
            }
            else if (statusTypeFunc.apply(varFromExecContext.type)) {
                v.status = simpleVariable;
            }
            else if (mappingTypeFunc.apply(varFromExecContext.type)) {
                v.mapping = simpleVariable;
            }
            else {
                log.info(S.f("Skip variable %s with type %s", simpleVariable.variable, varFromExecContext.type));
            }
        }

        for (ItemWithStatusWithMapping value : map.values()) {
            if (value.status==null) {
                log.error(S.f("#993.180 TaskContextId #%s has been skipped, (item.status==null)", value.taskContextId));
            }
            if (value.items ==null) {
                log.error(S.f("#993.185 TaskContextId #%s has been skipped, (item.items==null)", value.taskContextId));
            }
            if (value.mapping==null) {
                log.warn(S.f("#993.170 TaskContextId #%s doesn't have a mapping for files. VariableId will be used as a file name", value.taskContextId));
            }
        }
        return map;
    }

    private static Map<String, ExecContextParamsYaml.Variable> gatherVars(ExecContextParamsYaml ecpy) {
        Map<String, ExecContextParamsYaml.Variable> map = ecpy.processes.stream()
                .flatMap(o->o.outputs.stream())
                .collect(Collectors.toMap(o->o.name, o->o, (a, b) -> b, HashMap::new));
        return map;
    }

    private void storeResultVariables(File zipDir, Long execContextId, ItemWithStatusWithMapping item) {

        if (item.status==null) {
            log.error(S.f("#993.180 TaskContextId #%s has been skipped, (item.status==null).", item.taskContextId));
            return;
        }
        if (item.items ==null) {
            log.error(S.f("#993.185 TaskContextId #%s has been skipped, (item.items==null).", item.taskContextId));
            return;
        }
        if (item.mapping==null) {
            log.warn(S.f("#993.190 TaskContextId #%s doesn't have a mapping for files. VariableId will be used as a file name.", item.taskContextId));
        }

        BatchItemMappingYaml bimy = new BatchItemMappingYaml();
        bimy.targetDir = getResultDirNameFromTaskContextId(item.taskContextId);

        if (item.mapping!=null) {
            SimpleVariable sv = item.mapping;
            if (!sv.nullified) {
                String mapping = null;
                try {
                    mapping = variableService.getVariableDataAsString(sv.id);
                    if (!S.b(mapping)) {
                        bimy = BatchItemMappingYamlUtils.BASE_YAML_UTILS.to(mapping);
                    }
                }
                catch (CommonErrorWithDataException e) {
                    log.warn("#993.200 no mapping variables with id #{} were found in execContextId #{}", sv.id, execContextId);
                }
                catch (WrongVersionOfParamsException e) {
                    log.warn("#993.205 error parsing a mapping variable #{} {}:  #{}", sv.id, sv.variable, mapping);
                    throw e;
                }
            }
            else {
                log.info("#993.210 Variable #{} {} is null", sv.id, sv.variable);
            }
        }

        fixPathInMapping(bimy);

        File resultDir = new File(zipDir, bimy.targetDir);
        if (!resultDir.getAbsolutePath().startsWith(zipDir.getAbsolutePath())) {
            throw new IllegalStateException(
                    S.f("#993.213 Attempt to create a file outside of zip tree structure, zipDir: %s, resultDir: %s",
                            zipDir.getAbsolutePath(), resultDir.getAbsolutePath()));
        }
        resultDir.mkdir();

        storeVariableToFile(bimy, resultDir, item.items);
        storeVariableToFile(bimy, resultDir, List.of(item.status));
    }

    private static void fixPathInMapping(BatchItemMappingYaml bimy) {
        if (bimy.targetDir!=null) {
            if (File.separatorChar=='/') {
                bimy.targetDir = bimy.targetDir.replace('\\', '/');
            }
            else {
                bimy.targetDir = bimy.targetDir.replace('/', '\\');
            }
        }
        if (CollectionUtils.isEmpty(bimy.filenames)) {
            return;
        }
        Map<String, String> newMap = new HashMap<>();
        for (Map.Entry<String, String> entry : bimy.filenames.entrySet()) {
            if (entry.getValue()==null) {
                continue;
            }
            String value;
            if (File.separatorChar=='/') {
                value = entry.getValue().replace('\\', '/');
            }
            else {
                value = entry.getValue().replace('/', '\\');
            }
            newMap.put(entry.getKey(), value);
        }
        bimy.filenames = newMap;
    }

    private void storeVariableToFile(BatchItemMappingYaml bimy, File resultDir, List<SimpleVariable> simpleVariables) {
        for (SimpleVariable simpleVariable : simpleVariables) {
            if (simpleVariable.nullified) {
                log.info("#993.215 Variable #{} {} is null", simpleVariable.id, simpleVariable.variable);
                continue;
            }
            String itemFilename = bimy.filenames.get(simpleVariable.id.toString());
            if (S.b(itemFilename)) {
                itemFilename = simpleVariable.id.toString();
            }
            File file = new File(resultDir, itemFilename);
            variableService.storeToFile(simpleVariable.id, file);
        }
    }

    private static String getResultDirNameFromTaskContextId(String taskContextId) {
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

        List<ExecContextData.TaskWithState> taskVertices = execContextGraphService.getAllTasksTopologically(ec.execContextGraphId, ec.execContextTaskStateId);
        for (ExecContextData.TaskWithState taskVertex : taskVertices) {
            if (taskVertex.state== EnumsApi.TaskExecState.NONE || taskVertex.state== EnumsApi.TaskExecState.IN_PROGRESS) {
                continue;
            }
            storeStatusOfTask(bs, ec, taskVertex.taskId);
        }
        initBatchStatus(bs);
        return bs;
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private void storeStatusOfTask(BatchStatusProcessor bs, ExecContextImpl ec, Long taskId) {
        final TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            String msg = "#993.220 Can't find task #" + taskId;
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
                        S.f("#993.360 Task #%s was skipped yet, function: %s, status: %s, execContextId: %s, %s, %s",
                                taskId, func, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, processorId, processorIpAndHost)
                        ,'\n');
                bs.ok = true;
                return;
            case NOT_USED_ANYMORE:
                break;
            case CHECK_CACHE:
                // TODO 2020-11-29 should we do something with this state?
                break;
        }

        bs.getOkStatus().add(
                S.f("#993.380 Task #%s was completed successfully, function: %s, status: %s, execContextId: %s, %s, %s",
                        taskId, func, EnumsApi.TaskExecState.from(task.getExecState()), ec.id, processorId, processorIpAndHost)
                ,'\n');
        bs.ok = true;
    }

    private static String getProcessorIpAndHost(@Nullable Processor processor) {
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
