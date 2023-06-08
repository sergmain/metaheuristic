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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.event.SetVariableReceivedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskCreatedTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;
import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@SuppressWarnings("unused")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableTxService {

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadVariableStatus.OK, null);

    private final VariableRepository variableRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventPublisherService eventPublisherService;
    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextCache execContextCache;
    private final VariableBlobTxService variableBlobTxService;
    private final VariableBlobRepository variableBlobRepository;


    private Variable createInitialized(
            InputStream is, long size, String variable, @Nullable String filename,
            Long execContextId, String taskContextId, EnumsApi.VariableType type) {
        if (S.b(variable)) {
            throw new ExecContextCommonException("697.040 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        if (size==0) {
            throw new IllegalStateException("#171.600 Variable can't be of zero length");
        }
        TxUtils.checkTxExists();

        Variable data = new Variable();
        data.inited = true;
        data.nullified = false;
        data.setName(variable);
        data.setFilename(filename);
        data.setExecContextId(execContextId);
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, variable, type)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        data.setTaskContextId(taskContextId);

        VariableBlob variableBlob = variableBlobTxService.createWithInputStream(is,size);
        data.variableBlobId = variableBlob.id;
//        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
//        data.setData(blob);

        variableRepository.save(data);

        return data;
    }

    private void update(InputStream is, long size, Variable data) {
        TxUtils.checkTxExists();
        VariableSyncService.checkWriteLockPresent(data.id);

        if (size==0) {
            throw new IllegalStateException("171.690 Variable can't be with zero length");
        }
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        VariableBlob variableBlob = variableBlobTxService.createOrUpdateWithInputStream(data.variableBlobId, is, size);
        data.variableBlobId = variableBlob.id;

//        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
//        data.setData(blob);

        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
    }

    @Transactional
    public void storeData(InputStream is, long size, Long variableId, @Nullable String filename) {
        VariableSyncService.checkWriteLockPresent(variableId);

        if (size==0) {
            throw new IllegalStateException("171.720 Variable can't be with zero length");
        }
        TxUtils.checkTxExists();

        Variable data = variableRepository.findById(variableId).orElse(null);
        if (data==null) {
            log.error("171.750 can't find variable #" + variableId);
            return;
        }
        data.filename = filename;
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        VariableBlob variableBlob = variableBlobTxService.createOrUpdateWithInputStream(data.variableBlobId, is, size);
        data.variableBlobId = variableBlob.id;
//        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
//        data.setData(blob);

        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
    }

    @Transactional
    public void initInputVariableWithNull(Long execContextId, ExecContextParamsYaml execContextParamsYaml, int varIndex) {
        if (execContextParamsYaml.variables.inputs.size()<varIndex+1) {
            throw new ExecContextCommonException(
                    S.f("697.020 varIndex is bigger than number of input variables. varIndex: %s, number: %s",
                            varIndex, execContextParamsYaml.variables.inputs.size()));
        }
        final ExecContextParamsYaml.Variable variable = execContextParamsYaml.variables.inputs.get(varIndex);
        if (!variable.getNullable()) {
            throw new ExecContextCommonException(S.f("697.025 sourceCode %s, input variable %s must be declared as nullable to be set as null",
                    execContextParamsYaml.sourceCodeUid, variable.name));
        }
        String inputVariable = variable.name;
        if (S.b(inputVariable)) {
            throw new ExecContextCommonException("697.040 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("697.060 ExecContext #{} wasn't found", execContextId);
        }
        createInitializedWithNull(inputVariable, execContextId, Consts.TOP_LEVEL_CONTEXT_ID );
    }

    /**
     * varIndex - index of variable, start with 0
     */
    @Transactional
    public Variable createInitializedTx(
            InputStream is, long size, String inputVariableName, String originFilename, Long execContextId, String taskContextId, EnumsApi.VariableType type) {
        Variable v = createInitialized(is, size, inputVariableName, originFilename, execContextId, taskContextId, type );
        return v;
    }

    @SneakyThrows
    @Transactional
    public void storeDataInVariable(TaskParamsYaml.OutputVariable outputVariable, Path file) {
        Variable variable = findVariableInLocalContext(outputVariable);

        final ResourceCloseTxEvent resourceCloseTxEvent = new ResourceCloseTxEvent();
        eventPublisher.publishEvent(resourceCloseTxEvent);
        try {
            InputStream is = Files.newInputStream(file);
            resourceCloseTxEvent.add(is);
            update(is, Files.size(file), variable);
        } catch (FileNotFoundException e) {
            throw new InternalFunctionException(system_error, "#697.180 Can't open file   "+ file.normalize());
        }
    }

    @Transactional
    public void storeStringInVariable(TaskParamsYaml.OutputVariable outputVariable, String value) {
        Variable variable = findVariableInLocalContext(outputVariable);

        byte[] bytes = value.getBytes();
        InputStream is = new ByteArrayInputStream(bytes);
        update(is, bytes.length, variable);
    }

    @Transactional
    public void storeBytesInVariable(TaskParamsYaml.OutputVariable outputVariable, byte[] bytes, EnumsApi.VariableType type) {
        Variable variable = findVariableInLocalContext(outputVariable);
        DataStorageParams dsp = DataStorageParamsUtils.to(variable.params);
        dsp.type = type;
        variable.params = DataStorageParamsUtils.toString(dsp);

        InputStream is = new ByteArrayInputStream(bytes);
        update(is, bytes.length, variable);
    }

    private Variable findVariableInLocalContext(TaskParamsYaml.OutputVariable outputVariable) {
        Variable variable;
        if (outputVariable.context == EnumsApi.VariableContext.local) {
            variable = variableRepository.findById(outputVariable.id).orElse(null);
            if (variable == null) {
                throw new InternalFunctionException(variable_not_found, "697.140 Variable not found for id #" + outputVariable.id);
            }
            return variable;
        }
        else if (outputVariable.context == EnumsApi.VariableContext.global) {
            throw new InternalFunctionException(global_variable_is_immutable, "697.160 Can't store data in a global variable " + outputVariable.name);
        }
        else if (outputVariable.context == EnumsApi.VariableContext.array) {
            throw new InternalFunctionException(general_error, "697.165 variable as array not supported yet " + outputVariable.name);
        }
        throw new IllegalStateException();
    }

    @Transactional
    public void initOutputVariable(Long execContextId, ExecContextParamsYaml.Variable output) {
        TxUtils.checkTxExists();

        Variable sv = findVariableInAllInternalContexts(output.name, Consts.TOP_LEVEL_CONTEXT_ID, execContextId);
        if (sv == null) {
            Variable v = createUninitialized(output.name, execContextId, Consts.TOP_LEVEL_CONTEXT_ID);
        }
    }

    @Transactional
    public void setVariableAsNull(Long taskId, Long variableId) {
        eventPublisherService.publishSetVariableReceivedTxEvent(new SetVariableReceivedTxEvent(taskId, variableId, true));
        setVariableAsNull(variableId);
    }

    @Transactional
    public void storeVariable(InputStream variableIS, long length, Long execContextId, Long taskId, Long variableId) {
        VariableSyncService.checkWriteLockPresent(variableId);

        Variable variable = variableRepository.findById(variableId).orElse(null);
        if (variable ==null) {
            throw new VariableCommonException("#171.030 Variable #"+variableId+" wasn't found", variableId);
        }
        if (!execContextId.equals(variable.execContextId)) {
            final String es = "#171.060 Task #"+taskId+" has the different execContextId than variable #"+variableId+", " +
                    "task execContextId: "+execContextId+", var execContextId: "+variable.execContextId;
            log.warn(es);
            throw new VariableCommonException(es, variableId);
        }
        update(variableIS, length, variable);
    }

    public void resetVariable(Long execContextId, Long variableId) {
        TxUtils.checkTxExists();
        VariableSyncService.checkWriteLockPresent(variableId);

        Variable v = variableRepository.findById(variableId).orElse(null);
        if (v ==null) {
            throw new VariableCommonException("#171.090 Variable #"+variableId+" wasn't found", variableId);
        }
        if (!execContextId.equals(v.execContextId)) {
            final String es = "#171.120 the different execContextId than variable #"+variableId+", " +
                    "task execContextId: #"+execContextId+", var execContextId: #"+v.execContextId;
            log.warn(es);
            throw new VariableCommonException(es, variableId);
        }
        v.uploadTs = new Timestamp(System.currentTimeMillis());
        v.inited = false;
        v.nullified = true;
        // TODO 2023-06-07 p3 add an event to delete related variableBlob
        v.variableBlobId = null;
        variableRepository.save(v);
    }

    @Transactional
    public void setVariableAsNull(Long variableId) {
        VariableSyncService.checkWriteLockPresent(variableId);

        Variable variable = variableRepository.findById(variableId).orElse(null);
        if (variable ==null) {
            String es = S.f("#171.150 variable #%d wasn't found", variableId);
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        variable.inited = true;
        variable.nullified = true;
        // TODO 2023-06-07 p3 add an event to delete related variableBlob
        variable.variableBlobId = null;
        variableRepository.save(variable);
    }

    @Transactional
    public Variable createInitializedWithNull(String variable, Long execContextId, String taskContextId) {
        Variable data = new Variable();
        data.inited = true;
        data.nullified = true;
        data.name = variable;
        data.filename = null;
        data.setExecContextId(execContextId);
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        data.setTaskContextId(taskContextId);
        data.variableBlobId = null;
        variableRepository.save(data);

        return data;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createInputVariablesForSubProcess(
            VariableData.VariableDataSource variableDataSource,
            Long execContextId, String inputVariableName,
            String currTaskContextId, boolean contentAsArray) {
        TxUtils.checkTxExists();

        List<BatchTopLevelService.FileWithMapping> files = variableDataSource.files;
        String inputVariableContent = variableDataSource.inputVariableContent;
        VariableData.Permutation permutation = variableDataSource.permutation;
        List<Pair<String, Boolean>> booleanVariables = variableDataSource.booleanVariables;

        if (files.isEmpty() && inputVariableContent==null && permutation==null && booleanVariables.isEmpty()) {
            throw new IllegalStateException("(files.isEmpty() && inputVariableContent==null && permutation==null && booleanVariables.isEmpty())");
        }

        if (!files.isEmpty() || (inputVariableContent!=null && contentAsArray)) {
            List<VariableUtils.VariableHolder> variableHolders = new ArrayList<>();
            for (BatchTopLevelService.FileWithMapping f : files) {
                String variableName = VariableUtils.getNameForVariableInArray();

                try {
                    FileInputStream fis = new FileInputStream(f.file);
                    eventPublisher.publishEvent(new ResourceCloseTxEvent(fis));
                    Variable v = createInitialized(fis, f.file.length(), variableName, f.originName, execContextId, currTaskContextId, EnumsApi.VariableType.unknown);
                    variableHolders.add(new VariableUtils.VariableHolder(v));
                }
                catch (FileNotFoundException e) {
                    ExceptionUtils.rethrow(e);
                }
            }

            if (!S.b(inputVariableContent)) {
                String variableName = VariableUtils.getNameForVariableInArray();
                Variable v = createInitialized(inputVariableContent, variableName, variableName, execContextId, currTaskContextId, EnumsApi.VariableType.text);

                VariableUtils.VariableHolder variableHolder = new VariableUtils.VariableHolder(v);
                variableHolders.add(variableHolder);
            }

            VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(variableHolders);
            String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
            byte[] bytes = yaml.getBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            // we fire this event to be sure that ref to ByteArrayInputStream live longer than TX
            eventPublisher.publishEvent(new ResourceCloseTxEvent(bais));
            Variable v = createInitialized(bais, bytes.length, inputVariableName, null, execContextId, currTaskContextId, EnumsApi.VariableType.yaml);
        }

        for (Pair<String, Boolean> booleanVariable : booleanVariables) {
            Variable v = createInitialized(booleanVariable.getValue().toString(), booleanVariable.getKey(), null, execContextId, currTaskContextId, EnumsApi.VariableType.text);
        }

        if (inputVariableContent!=null && !contentAsArray) {
            Variable v = createInitialized(inputVariableContent, inputVariableName, null, execContextId, currTaskContextId, EnumsApi.VariableType.text);
        }

        if (permutation!=null) {
            {
                VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(permutation.permutedVariables);
                String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
                Variable v = createInitialized(yaml, permutation.permutedVariableName, null, execContextId, currTaskContextId, EnumsApi.VariableType.yaml);
            }
            if (permutation.permuteInlines) {
                if (permutation.inlineVariableName==null || permutation.inlinePermuted==null) {
                    throw new IllegalStateException("(permutation.inlineVariableName==null || permutation.inlinePermuted==null)");
                }
                Yaml yampUtil = YamlUtils.init(Map.class);
                String yaml = yampUtil.dumpAsMap(permutation.inlinePermuted);
                Variable v = createInitialized(yaml, permutation.inlineVariableName, null, execContextId, currTaskContextId, EnumsApi.VariableType.yaml);
            }
        }
    }

    @Nullable
    public TaskImpl prepareVariables(ExecContextParamsYaml execContextParamsYaml, TaskImpl task, List<Long> parentTaskIds) {
        TxUtils.checkTxExists();

        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        final Long execContextId = task.execContextId;
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("#171.240 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+ execContextId);
            return null;
        }

        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (ec==null) {
            log.error("#171.260 can't find execContext #" + execContextId);
            return null;
        }

        ExecContextGraph ecg = execContextGraphCache.findById(ec.execContextGraphId);
        if (ecg==null) {
            log.error("#171.265 can't find ExecContextGraph #" + ec.execContextGraphId);
            return null;
        }
        Set<String> set = new HashSet<>();
        for (Long parentTaskId : parentTaskIds) {
            ExecContextData.TaskVertex vertex = ExecContextGraphService.findVertexByTaskId(ecg, parentTaskId);
            if (vertex==null) {
                throw new RuntimeException("#171.267 vertex wasn't found for task #"+task.id);
            }
            set.add(vertex.taskContextId);
            Set<ExecContextData.TaskVertex> setTemp = ExecContextGraphService.findAncestors(ecg, vertex);
            setTemp.stream().map(o->o.taskContextId).collect(Collectors.toCollection(()->set));
        }
        set.add(taskParams.task.taskContextId);

        List<String> list = ContextUtils.sortSetAsTaskContextId(set);

        p.inputs.stream()
                .map(v -> toInputVariable(list, v, taskParams.task.taskContextId, execContextId))
                .collect(Collectors.toCollection(()->taskParams.task.inputs));

        return initOutputVariables(execContextId, task, p, taskParams);
    }

    private TaskParamsYaml.InputVariable toInputVariable(List<String> list, ExecContextParamsYaml.Variable v, String taskContextId, Long execContextId) {
        TaskParamsYaml.InputVariable iv = new TaskParamsYaml.InputVariable();
        if (v.context== EnumsApi.VariableContext.local || v.context== EnumsApi.VariableContext.array) {
            String contextId = Boolean.TRUE.equals(v.parentContext) ? VariableUtils.getParentContext(taskContextId) : taskContextId;
            if (S.b(contextId)) {
                throw new TaskCreationException(
                        S.f("#171.270 (S.b(contextId)), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            Object[] variable = findVariableInAllInternalContexts(list, v.name, contextId, execContextId);
            if (variable==null) {
                throw new TaskCreationException(
                        S.f("#171.300 (variable==null), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            iv.id = (Long)variable[0];
            iv.filename = (String)variable[1];
        }
        else {
            SimpleGlobalVariable variable = globalVariableRepository.findIdByName(v.name);
            if (variable==null) {
                throw new TaskCreationException(
                        S.f("#171.330 (variable==null), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            iv.id = variable.id;
        }
        iv.context = v.context;
        iv.name = v.name;
        iv.sourcing = v.sourcing;
        iv.disk = v.disk;
        iv.git = v.git;
        iv.type = v.type;
        iv.setNullable(v.getNullable());
        return iv;
    }

    @SuppressWarnings({"SameParameterValue"})
    @Nullable
    public Variable getVariableAsSimple(Long execContextId, String variable) {
        List<Variable> vars = variableRepository.findByExecContextIdAndNames(execContextId, List.of(variable));

        if (vars.isEmpty()) {
            return null;
        }
        if (vars.size()>1) {
            throw new SourceCodeException("171.360 Too many variable '"+variable+"', actual count: " + vars.size());
        }
        return vars.get(0);
    }

    @Nullable
    public Variable getVariableAsSimple(Long variableId) {
        Variable var = variableRepository.findByIdAsSimple(variableId);
        return var;
    }

    @SuppressWarnings({"SameParameterValue"})
    @Nullable
    public Variable getVariableAsSimple(String variable, String processCode, ExecContextImpl execContext) {
        ExecContextParamsYaml.Process p = execContext.getExecContextParamsYaml().findProcess(processCode);
        if (p==null) {
            return null;
        }
        Variable v = findVariableInAllInternalContexts(variable, p.internalContextId, execContext.id);
        return v;
    }

    @Nullable
    public Variable findVariableInAllInternalContexts(String variable, String taskContextId, Long execContextId) {
        String currTaskContextId = taskContextId;
        while( !S.b(currTaskContextId)) {
            Variable v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variable, currTaskContextId, execContextId);
            if (v!=null) {
                return v;
            }
            currTaskContextId = VariableUtils.getParentContext(currTaskContextId);
        }
        return null;
    }

    @Nullable
    private Object[] findVariableInAllInternalContexts(List<String> taskCtxIds, String variable, String taskContextId, Long execContextId) {
        for (String taskCtxId : taskCtxIds) {
            List<Object[]> obj = variableRepository.findAsObject(variable, taskCtxId, execContextId);
            if (obj!=null && !obj.isEmpty()) {
                return obj.get(0);
            }
        }
        return null;
    }

    @Nullable
    private Variable findVariableInAllInternalContexts_old(List<String> taskCtxIds, String variable, String taskContextId, Long execContextId) {
        for (String taskCtxId : taskCtxIds) {
            Variable obj = variableRepository.findByNameAndTaskContextIdAndExecContextId(variable, taskCtxId, execContextId);
            if (obj!=null) {
                return obj;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableBlobId) {
        final String data = getVariableDataAsString(variableBlobId, false);
        if (S.b(data)) {
            final String es = "#171.390 Variable data wasn't found, variableBlobId: " + variableBlobId;
            log.warn(es);
            throw new VariableDataNotFoundException(variableBlobId, EnumsApi.VariableContext.local, es);
        }
        return data;
    }

    @Nullable
    private String getVariableDataAsString(@Nullable Long variableBlobId, boolean nullable) {
        if (variableBlobId==null) {
            if (nullable) {
                log.info("#171.420 Variable #{} is nullable and current value is null", variableBlobId);
                return null;
            }
            final String es = "#171.450 Variable data wasn't found, variableBlobId: " + variableBlobId;
            log.warn(es);
            throw new VariableDataNotFoundException(null, EnumsApi.VariableContext.local, es);
        }

        try {
            Blob blob = variableBlobRepository.getDataAsStreamById(variableBlobId);
            if (blob==null) {
                if (nullable) {
                    log.info("#171.420 Variable #{} is nullable and current value is null", variableBlobId);
                    return null;
                }
                final String es = "#171.450 Variable data wasn't found, variableBlobId: " + variableBlobId;
                log.warn(es);
                throw new VariableDataNotFoundException(variableBlobId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is, 0x8000)) {
                String s = IOUtils.toString(bis, StandardCharsets.UTF_8);
                return s;
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            log.error("#171.480", th);
            throw new VariableCommonException("#171.510 Error: " + th.getMessage(), variableBlobId);
        }
    }

    @SneakyThrows
    @Transactional(readOnly = true)
    public void storeVariableToFileWithTx(Function<Variable, Path> mappingFunc, List<Variable> variables) {
        storeVariableToFile(mappingFunc, variables);
    }

    public void storeVariableToFile(Function<Variable, Path> mappingFunc, List<Variable> variables) {
        TxUtils.checkTxExists();

        for (Variable variable : variables) {
            if (variable.nullified) {
                log.info("#993.215 Variable #{} {} is null", variable.id, variable.name);
                continue;
            }
            Path file = mappingFunc.apply(variable);
            storeToFile(variable.id, file);
        }
    }

    @Transactional(readOnly = true)
    public void storeToFileWithTx(Long variableId, Path trgFile) {
        Variable sv = variableRepository.findByIdAsSimple(variableId);
        if (sv==null) {
            String es = "#171.535 Variable #"+variableId+" wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        if (sv.nullified) {
            throw new VariableIsNullException(variableId);
        }
        storeToFile(variableId, trgFile);
    }

    public void storeToFile(Long variableId, Path trgFile) {
        TxUtils.checkTxExists();

        Variable v = getVariable(variableId);

        try {
            Blob blob = variableBlobRepository.getDataAsStreamById(Objects.requireNonNull(v.variableBlobId));
            if (blob==null) {
                String es = "171.540 VariableBlob #"+v.variableBlobId+" wasn't found";
                log.warn(es);
                throw new VariableDataNotFoundException(v.variableBlobId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream()) {
                DirUtils.copy(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "171.570 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] getVariableAsBytes(Long variableId) {
        Variable v = getVariable(variableId);

        try {
            Blob blob = variableBlobRepository.getDataAsStreamById(Objects.requireNonNull(v.variableBlobId));
            if (blob==null) {
                String es = "#171.540 Variable #"+v.variableBlobId+" wasn't found";
                log.warn(es);
                throw new VariableDataNotFoundException(v.variableBlobId, EnumsApi.VariableContext.local, es);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
                IOUtils.copy(bis, baos);
            }
            return baos.toByteArray();
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "#171.570 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    private Variable getVariable(Long variableId) {
        Variable v = variableRepository.findById(variableId).orElse(null);
        if (v==null || v.variableBlobId==null) {
            String es = "171.540 Variable #" + variableId + " wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return v;
    }

    public List<Variable> getVariablesInExecContext(Long execContextId, String ... variables) {
        if (variables.length==0) {
            return List.of();
        }
        return variableRepository.getIdAndStorageUrlInVarsForExecContext(execContextId, variables);
    }

    private Variable createInitialized(String data, String variable, @Nullable String filename, Long execContextId, String taskContextId, EnumsApi.VariableType type) {
        final byte[] bytes = data.getBytes();
        InputStream is = new ByteArrayInputStream(bytes);
        // we fire this event to be sure that ref to ByteArrayInputStream live longer than TX
        eventPublisher.publishEvent(new ResourceCloseTxEvent(is));
        return createInitialized(is, bytes.length, variable, filename, execContextId, taskContextId, type);
    }


    public TaskImpl initOutputVariables(Long execContextId, TaskImpl task, ExecContextParamsYaml.Process p, TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        for (ExecContextParamsYaml.Variable variable : p.outputs) {
            String contextId = Boolean.TRUE.equals(variable.parentContext) ? VariableUtils.getParentContext(taskParamsYaml.task.taskContextId) : taskParamsYaml.task.taskContextId;
            if (S.b(contextId)) {
                throw new IllegalStateException(
                        S.f("#171.630 (S.b(contextId)), process code: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
                                p.processCode, variable.context, p.internalContextId, execContextId));
            }

            Variable sv = findVariableInAllInternalContexts(variable.name, contextId, execContextId);
            if (sv == null) {
                Variable v = createUninitialized(variable.name, execContextId, contextId);

                // even that a variable.getNullable() can be false, we set a field 'empty' as true because variable will be inited later
                // and consistency of fields 'empty'  and 'nullable' will be enforced before calling Functions
                taskParamsYaml.task.outputs.add(
                        new TaskParamsYaml.OutputVariable(
                                v.id, EnumsApi.VariableContext.local, variable.name, variable.sourcing, variable.git, variable.disk,
                                null, false, variable.type, true, variable.getNullable(), variable.ext
                        ));
            }
        }
        task.updatedOn = System.currentTimeMillis();
        task.updateParams(taskParamsYaml);

        TaskCreatedTxEvent event = new TaskCreatedTxEvent(
                new ExecContextApiData.VariableState(task.id, task.coreId, execContextId,
                        taskParamsYaml.task.taskContextId, taskParamsYaml.task.processCode, taskParamsYaml.task.function.code,
                        null,
                        taskParamsYaml.task.outputs.stream().map(o -> new ExecContextApiData.VariableInfo(o.id, o.name, o.context, o.ext)).collect(Collectors.toList())));

        eventPublisherService.publishTaskCreatedTxEvent(event);

        return task;
    }

    public Variable createUninitialized(String variable, Long execContextId, String taskContextId) {
        Variable v = new Variable();
        v.variableBlobId = null;
        v.name = variable;
        v.execContextId = execContextId;
        v.taskContextId = taskContextId;
        v.inited = false;
        v.nullified = true;

        // TODO 2020-02-03 right now, only DataSourcing.dispatcher is supported as internal variable.
        //   a new code has to be added for another type of sourcing
        v.params = DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, v.name));
        v.uploadTs = new Timestamp(System.currentTimeMillis());

        return variableRepository.save(v);
    }

    @Transactional
    public void updateWithTx(InputStream is, long size, Long variableId) {
        Variable v = variableRepository.findById(variableId).orElse(null);
        if (v==null) {
            String es = S.f("#171.660 Variable #%d wasn't found", variableId);
            log.error(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        update(is, size, v);
    }

    @Transactional
    public void deleteOrphanVariables(List<Long> ids) {
        variableRepository.deleteByIds(ids);
    }

}
