/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.SetVariableReceivedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TaskCreatedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.VariableUploadedTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.storage.DispatcherBlobStorage;
import ai.metaheuristic.ai.dispatcher.storage.GeneralBlobService;
import ai.metaheuristic.ai.dispatcher.storage.GeneralBlobTxService;
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.commons.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;
import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VariableTxService {

    private final VariableRepository variableRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventPublisherService eventPublisherService;
    private final ExecContextCache execContextCache;
    private final GeneralBlobService generalBlobService;
    private final GeneralBlobTxService generalBlobTxService;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    private Variable createInitialized(
            InputStream is, long size, String variable, @Nullable String filename,
            Long execContextId, String taskContextId, EnumsApi.VariableType type) {
        if (S.b(variable)) {
            throw new ExecContextCommonException("171.040 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        if (size==0) {
            throw new IllegalStateException("171.080 Variable can't be of zero length");
        }
        TxUtils.checkTxExists();

        Variable data = new Variable();
        data.inited = true;
        data.nullified = false;
        data.setName(variable);
        data.setFilename(filename);
        data.setExecContextId(execContextId);
        final DataStorageParams dsp = new DataStorageParams(DataSourcing.dispatcher, variable, type);
        dsp.size = size;
        data.updateParams(dsp);
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        data.setTaskContextId(taskContextId);

        data.variableBlobId = generalBlobTxService.createEmptyVariable();;
        dispatcherBlobStorage.storeVariableData(data.variableBlobId, is, size);

        variableRepository.save(data);

        return data;
    }

    private void update(InputStream is, long size, Variable data) {
        TxUtils.checkTxExists();
        VariableSyncService.checkWriteLockPresent(data.id);

        if (size==0) {
            throw new IllegalStateException("171.120 Variable can't be with zero length");
        }
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        DataStorageParams dsp = data.getDataStorageParams();
        dsp.size = size;
        data.updateParams(dsp);

        data.variableBlobId = generalBlobService.createVariableIfNotExist(data.variableBlobId);
        dispatcherBlobStorage.storeVariableData(data.variableBlobId, is, size);

        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
    }

    @Transactional
    public void storeData(Long taskId, InputStream is, long size, Long variableId, @Nullable String filename) {
        VariableSyncService.checkWriteLockPresent(variableId);

        if (size==0) {
            throw new IllegalStateException("171.160 Variable can't be with zero length");
        }
        TxUtils.checkTxExists();

        Variable data = getVariableNotNull(variableId);

        data.filename = filename;
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        data.variableBlobId = generalBlobService.createVariableIfNotExist(data.variableBlobId);
        dispatcherBlobStorage.storeVariableData(data.variableBlobId, is, size);

        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
        eventPublisherService.publishSetVariableReceivedTxEvent(new SetVariableReceivedTxEvent(taskId, variableId, false));
    }

    @Transactional
    public void initInputVariableWithNull(String sourceCodeUid, Long execContextId, final ExecContextParamsYaml.Variable variable) {
/*
        if (execContextParamsYaml.variables.inputs.size()<varIndex+1) {
            throw new ExecContextCommonException(
                    S.f("171.200 varIndex is bigger than number of input variables. varIndex: %s, number: %s",
                            varIndex, execContextParamsYaml.variables.inputs.size()));
        }
        final ExecContextParamsYaml.Variable variable = execContextParamsYaml.variables.inputs.get(varIndex);
*/
        if (!variable.getNullable()) {
            throw new ExecContextCommonException(S.f("171.240 sourceCode %s, input variable %s must be declared as nullable to be set as null",
                    sourceCodeUid, variable.name));
        }
        String inputVariable = variable.name;
        if (S.b(inputVariable)) {
            throw new ExecContextCommonException("171.280 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("171.320 ExecContext #{} wasn't found", execContextId);
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

    @Transactional
    public void storeDataInVariable(TaskParamsYaml.OutputVariable outputVariable, Path file) {
        Variable variable = findVariableInLocalContext(outputVariable);

        final ResourceCloseTxEvent resourceCloseTxEvent = new ResourceCloseTxEvent();
        eventPublisher.publishEvent(resourceCloseTxEvent);
        try {
            InputStream is = Files.newInputStream(file);
            resourceCloseTxEvent.add(is);
            update(is, Files.size(file), variable);
        } catch (IOException e) {
            throw new InternalFunctionException(system_error, "171.360 Can't open file   "+ file.normalize());
        }
    }

    @Transactional
    public void storeStringInVariable(Long execContextId, Long taskId, TaskParamsYaml.OutputVariable outputVariable, String value) {
        Variable variable = findVariableInLocalContext(outputVariable);

        byte[] bytes = value.getBytes();
        InputStream is = new ByteArrayInputStream(bytes);
        update(is, bytes.length, variable);
        eventPublisherService.publishVariableUploadedTxEvent(new VariableUploadedTxEvent(execContextId, taskId, variable.id, false));
    }

    @Transactional
    public void storeBytesInVariable(TaskParamsYaml.OutputVariable outputVariable, byte[] bytes, EnumsApi.VariableType type) {
        Variable variable = findVariableInLocalContext(outputVariable);
        DataStorageParams dsp = variable.getDataStorageParams();
        dsp.type = type;
        variable.updateParams(dsp);

        InputStream is = new ByteArrayInputStream(bytes);
        update(is, bytes.length, variable);
    }

    private Variable findVariableInLocalContext(TaskParamsYaml.OutputVariable outputVariable) {
        Variable variable;
        if (outputVariable.context == EnumsApi.VariableContext.local) {
            variable = variableRepository.findById(outputVariable.id).orElse(null);
            if (variable == null) {
                throw new InternalFunctionException(variable_not_found, "171.400 Variable not found for id #" + outputVariable.id);
            }
            return variable;
        }
        else if (outputVariable.context == EnumsApi.VariableContext.global) {
            throw new InternalFunctionException(global_variable_is_immutable, "171.430 Can't store data in a global variable " + outputVariable.name);
        }
        else if (outputVariable.context == EnumsApi.VariableContext.array) {
            throw new InternalFunctionException(general_error, "171.460 variable as array not supported yet " + outputVariable.name);
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
        setVariableAsNull(variableId);
        eventPublisherService.publishSetVariableReceivedTxEvent(new SetVariableReceivedTxEvent(taskId, variableId, true));
    }

    @Transactional
    public void storeVariable(InputStream variableIS, long length, Long execContextId, Long taskId, Long variableId) {
        VariableSyncService.checkWriteLockPresent(variableId);

        Variable v = getVariableNotNull(variableId);

        if (!execContextId.equals(v.execContextId)) {
            final String es = "171.490 Task #"+taskId+" has the different execContextId than variable #"+variableId+", " +
                    "task execContextId: "+execContextId+", var execContextId: "+v.execContextId;
            log.warn(es);
            throw new VariableCommonException(es, variableId);
        }
        update(variableIS, length, v);
    }

    @Transactional
    public void resetVariableTx(Long execContextId, Long variableId) {
        resetVariable(execContextId, variableId);
    }

    public void resetVariable(Long execContextId, Long variableId) {
        TxUtils.checkTxExists();
        VariableSyncService.checkWriteLockPresent(variableId);

        Variable v = getVariableNotNull(variableId);

        if (!execContextId.equals(v.execContextId)) {
            final String es = "171.520 the different execContextId than variable #"+variableId+", " +
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

    private void setVariableAsNull(Long variableId) {
        VariableSyncService.checkWriteLockPresent(variableId);

        Variable v = getVariableNotNull(variableId);

        v.inited = true;
        v.nullified = true;
        // TODO 2023-06-07 p3 add an event to delete related variableBlob
        v.variableBlobId = null;
        variableRepository.save(v);
    }

    @Transactional
    public Variable createInitializedWithNull(String variable, Long execContextId, String taskContextId) {
        Variable data = new Variable();
        data.inited = true;
        data.nullified = true;
        data.name = variable;
        data.filename = null;
        data.setExecContextId(execContextId);
        data.setParams(DataStorageParamsUtils.UTILS.toString(new DataStorageParams(DataSourcing.dispatcher, variable)));
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
                    InputStream fis = Files.newInputStream(f.file);
                    eventPublisher.publishEvent(new ResourceCloseTxEvent(fis));
                    Variable v = createInitialized(fis, Files.size(f.file), variableName, f.originName, execContextId, currTaskContextId, EnumsApi.VariableType.unknown);
                    variableHolders.add(new VariableUtils.VariableHolder(v));
                }
                catch (IOException e) {
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

    @SuppressWarnings({"SameParameterValue"})
    @Nullable
    public Variable getVariableAsSimple(Long execContextId, String variable) {
        List<Variable> vars = variableRepository.findByExecContextIdAndNames(execContextId, List.of(variable));

        if (vars.isEmpty()) {
            return null;
        }
        if (vars.size()>1) {
            throw new SourceCodeException("171.550 Too many variable '"+variable+"', actual count: " + vars.size());
        }
        return vars.get(0);
    }

    @Nullable
    @Transactional(readOnly = true)
    public Variable getVariable(Long variableId) {
        Variable var = variableRepository.findById(variableId).orElse(null);
        return var;
    }

    @SuppressWarnings({"SameParameterValue"})
    @Nullable
    public Variable getVariable(String variable, String processCode, ExecContextImpl execContext) {
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
    public Object[] findVariableInAllInternalContexts(List<String> taskCtxIds, String variable, Long execContextId) {
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
    public String getVariableDataAsString(Long variableId) {

        Variable v = getVariableNotNull(variableId);

        final String data = getVariableDataAsString(v.variableBlobId, false);
        if (S.b(data)) {
            final String es = "171.580 Variable data wasn't found, variableId: " + variableId;
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return data;
    }

    @Transactional(readOnly = true)
    public String getVariableBlobDataAsString(Long variableBlobId) {
        final String data = getVariableDataAsString(variableBlobId, false);
        if (S.b(data)) {
            final String es = "171.610 Variable data wasn't found, variableBlobId: " + variableBlobId;
            log.warn(es);
            throw new VariableDataNotFoundException(variableBlobId, EnumsApi.VariableContext.local, es);
        }
        return data;
    }

    @Nullable
    private String getVariableDataAsString(@Nullable Long variableBlobId, boolean nullable) {
        if (variableBlobId==null) {
            if (nullable) {
                log.info("171.640 Variable #{} is nullable and current value is null", variableBlobId);
                return null;
            }
            final String es = "171.670 Variable data wasn't found, variableBlobId: " + variableBlobId;
            log.warn(es);
            throw new VariableDataNotFoundException(null, EnumsApi.VariableContext.local, es);
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            dispatcherBlobStorage.accessVariableData(variableBlobId, (is)-> {
                try {
                    IOUtils.copy(is, baos);
                } catch (IOException e) {
                    String es = "171.700 "+e;
                    log.error(es, e);
                    throw new VariableCommonException(es, variableBlobId);
                }
            });
            String s = baos.toString(StandardCharsets.UTF_8);
            return s;
/*
            Blob blob = variableBlobRepository.getDataAsStreamById(variableBlobId);
            if (blob==null) {
                if (nullable) {
                    log.info("171.420 Variable #{} is nullable and current value is null", variableBlobId);
                    return null;
                }
                final String es = "171.450 Variable data wasn't found, variableBlobId: " + variableBlobId;
                log.warn(es);
                throw new VariableDataNotFoundException(variableBlobId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is, 0x8000)) {
                String s = IOUtils.toString(bis, StandardCharsets.UTF_8);
                return s;
            }
*/
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            final String es = "171.720 Error: " + th.getMessage();
            log.error(es, th);
            throw new VariableCommonException(es, variableBlobId);
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
                log.info("171.740 Variable #{} {} is null", variable.id, variable.name);
                continue;
            }
            Path file = mappingFunc.apply(variable);
            storeToFile(variable.id, file);
        }
    }

    @Transactional(readOnly = true)
    public void storeToFileWithTx(Long variableId, Path trgFile) {
        Variable sv = getVariableNotNull(variableId);
        if (sv.nullified) {
            throw new VariableIsNullException(variableId);
        }
        storeToFile(variableId, trgFile);
    }

    private Variable getVariableNotNull(Long variableId) {
        Variable v = variableRepository.findByIdAsSimple(variableId);
        if (v==null) {
            String es = "171.760 Variable #" + variableId + " wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return v;
    }

    public void storeToFile(Long variableId, Path trgFile) {
        final Long variableBlobId = getVariableBlobIdNotNull(variableId);

        try {
            dispatcherBlobStorage.accessVariableData(variableBlobId, (is)-> DirUtils.copy(is, trgFile));
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "171.780 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] getVariableAsBytes(Long variableId) {
        final Long variableBlobId = getVariableBlobIdNotNull(variableId);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            dispatcherBlobStorage.accessVariableData(variableBlobId, (is)-> {
                try {
                    IOUtils.copy(is, baos);
                } catch (IOException e) {
                    String es = "171.800 "+e;
                    log.error(es, e);
                    throw new VariableCommonException(es, variableId);
                }
            });
            return baos.toByteArray();
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "171.820 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    private Long getVariableBlobIdNotNull(Long variableId) {
        Variable v = variableRepository.findByIdReadOnly(variableId);
        if (v==null || v.variableBlobId==null) {
            String es = "171.840 Variable #" + variableId + " wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return v.variableBlobId;
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


    public void initOutputVariables(Long execContextId, TaskImpl task, ExecContextParamsYaml.Process p, TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        for (ExecContextParamsYaml.Variable variable : p.outputs) {
            String contextId = Boolean.TRUE.equals(variable.parentContext) ? VariableUtils.getParentContext(taskParamsYaml.task.taskContextId) : taskParamsYaml.task.taskContextId;
            if (S.b(contextId)) {
                throw new IllegalStateException(
                        S.f("171.860 (S.b(contextId)), process code: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
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
        v.updateParams(new DataStorageParams(DataSourcing.dispatcher, v.name));
        v.uploadTs = new Timestamp(System.currentTimeMillis());

        return variableRepository.save(v);
    }

    @Transactional
    public void updateWithTx(@Nullable Long taskId, InputStream is, long size, Long variableId) {
        Variable v = getVariableNotNull(variableId);
        update(is, size, v);
        if (taskId!=null) {
            eventPublisherService.publishSetVariableReceivedTxEvent(new SetVariableReceivedTxEvent(taskId, variableId, false));
        }
    }

    @Transactional
    public void deleteOrphanVariables(List<Long> ids) {
        variableRepository.deleteByIds(ids);
    }

}
