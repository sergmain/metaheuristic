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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
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
import ai.metaheuristic.commons.yaml.batch.BatchItemMappingYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Hibernate;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import javax.persistence.EntityManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@SuppressWarnings("unused")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableService {

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadVariableStatus.OK, null);

    private final EntityManager em;
    private final VariableRepository variableRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventPublisherService eventPublisherService;
    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextCache execContextCache;

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
        v.setData(null);
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
        variable.setData(null);
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
        data.setData(null);
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
                    Variable v = createInitialized(fis, f.file.length(), variableName, f.originName, execContextId, currTaskContextId);

                    SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                    variableHolders.add(new VariableUtils.VariableHolder(sv));
                }
                catch (FileNotFoundException e) {
                    ExceptionUtils.rethrow(e);
                }
            }

            if (!S.b(inputVariableContent)) {
                String variableName = VariableUtils.getNameForVariableInArray();
                Variable v = createInitialized(inputVariableContent, variableName, variableName, execContextId, currTaskContextId);

                SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                VariableUtils.VariableHolder variableHolder = new VariableUtils.VariableHolder(sv);
                variableHolders.add(variableHolder);
            }

            VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(variableHolders);
            String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
            byte[] bytes = yaml.getBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            // we fire this event to be sure that ref to ByteArrayInputStream live longer than TX
            eventPublisher.publishEvent(new ResourceCloseTxEvent(bais));
            Variable v = createInitialized(bais, bytes.length, inputVariableName, null, execContextId, currTaskContextId);
        }

        for (Pair<String, Boolean> booleanVariable : booleanVariables) {
            Variable v = createInitialized(""+booleanVariable.getValue(), booleanVariable.getKey(), null, execContextId, currTaskContextId);
        }

        if (inputVariableContent!=null && !contentAsArray) {
            Variable v = createInitialized(inputVariableContent, inputVariableName, null, execContextId, currTaskContextId);
        }

        if (permutation!=null) {
            {
                VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(permutation.permutedVariables);
                String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
                Variable v = createInitialized(yaml, permutation.permutedVariableName, null, execContextId, currTaskContextId);
            }
            if (permutation.permuteInlines) {
                if (permutation.inlineVariableName==null || permutation.inlinePermuted==null) {
                    throw new IllegalStateException("(permutation.inlineVariableName==null || permutation.inlinePermuted==null)");
                }
                Yaml yampUtil = YamlUtils.init(Map.class);
                String yaml = yampUtil.dumpAsMap(permutation.inlinePermuted);
                Variable v = createInitialized(yaml, permutation.inlineVariableName, null, execContextId, currTaskContextId);
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

        ExecContextImpl ec = execContextCache.findById(execContextId);
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
    public SimpleVariable getVariableAsSimple(Long execContextId, String variable) {
        List<SimpleVariable> vars = variableRepository.findByExecContextIdAndNames(execContextId, List.of(variable));

        if (vars.isEmpty()) {
            return null;
        }
        if (vars.size()>1) {
            throw new SourceCodeException("#171.360 Too many variable '"+variable+"', actual count: " + vars.size());
        }
        return vars.get(0);
    }

    @Nullable
    public SimpleVariable getVariableAsSimple(Long variableId) {
        SimpleVariable var = variableRepository.findByIdAsSimple(variableId);
        return var;
    }

    @SuppressWarnings({"SameParameterValue"})
    @Nullable
    public SimpleVariable getVariableAsSimple(String variable, String processCode, ExecContextImpl execContext) {
        ExecContextParamsYaml.Process p = execContext.getExecContextParamsYaml().findProcess(processCode);
        if (p==null) {
            return null;
        }
        SimpleVariable v = findVariableInAllInternalContexts(variable, p.internalContextId, execContext.id);
        return v;
    }

    @Nullable
    public SimpleVariable findVariableInAllInternalContexts(String variable, String taskContextId, Long execContextId) {
        String currTaskContextId = taskContextId;
        while( !S.b(currTaskContextId)) {
            SimpleVariable v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variable, currTaskContextId, execContextId);
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
    private SimpleVariable findVariableInAllInternalContexts_old(List<String> taskCtxIds, String variable, String taskContextId, Long execContextId) {
        for (String taskCtxId : taskCtxIds) {
            SimpleVariable obj = variableRepository.findByNameAndTaskContextIdAndExecContextId(variable, taskCtxId, execContextId);
            if (obj!=null) {
                return obj;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableId) {
        final String data = getVariableDataAsString(variableId, false);
        if (S.b(data)) {
            final String es = "#171.390 Variable data wasn't found, variableId: " + variableId;
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return data;
    }

    @Nullable
    private String getVariableDataAsString(Long variableId, boolean nullable) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                if (nullable) {
                    log.info("#171.420 Variable #{} is nullable and current value is null", variableId);
                    return null;
                }
                else {
                    final String es = "#171.450 Variable data wasn't found, variableId: " + variableId;
                    log.warn(es);
                    throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
                }
            }
            try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is, 0x8000)) {
                String s = IOUtils.toString(bis, StandardCharsets.UTF_8);
                return s;
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            log.error("#171.480", th);
            throw new VariableCommonException("#171.510 Error: " + th.getMessage(), variableId);
        }
    }

    @SneakyThrows
    @Transactional(readOnly = true)
    public void storeVariableToFileWithTx(Function<SimpleVariable, Path> mappingFunc, List<SimpleVariable> simpleVariables) {
        storeVariableToFile(mappingFunc, simpleVariables);
    }

    public void storeVariableToFile(Function<SimpleVariable, Path> mappingFunc, List<SimpleVariable> simpleVariables) {
        TxUtils.checkTxExists();

        for (SimpleVariable simpleVariable : simpleVariables) {
            if (simpleVariable.nullified) {
                log.info("#993.215 Variable #{} {} is null", simpleVariable.id, simpleVariable.variable);
                continue;
            }
            Path file = mappingFunc.apply(simpleVariable);
            storeToFile(simpleVariable.id, file);
        }
    }

    @Nullable
    @Transactional(readOnly = true)
    public Void storeToFileWithTx(Long variableId, Path trgFile) {
        return storeToFile(variableId, trgFile);
    }

    public Void storeToFile(Long variableId, Path trgFile) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                String es = "#171.540 Variable #"+variableId+" wasn't found";
                log.warn(es);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream()) {
                DirUtils.copy(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "#171.570 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        return null;
    }

    public List<SimpleVariable> getSimpleVariablesInExecContext(Long execContextId, String ... variables) {
        if (variables.length==0) {
            return List.of();
        }
        return variableRepository.getIdAndStorageUrlInVarsForExecContext(execContextId, variables);
    }

    public Variable createInitialized(String data, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
        final byte[] bytes = data.getBytes();
        InputStream is = new ByteArrayInputStream(bytes);
        // we fire this event to be sure that ref to ByteArrayInputStream live longer than TX
        eventPublisher.publishEvent(new ResourceCloseTxEvent(is));
        return createInitialized(is, bytes.length, variable, filename, execContextId, taskContextId);
    }

    public Variable createInitialized(InputStream is, long size, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
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
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        data.setTaskContextId(taskContextId);

        Blob blob = Hibernate.getLobCreator(em.unwrap(SessionImplementor.class)).createBlob(is, size);
        data.setData(blob);

        variableRepository.save(data);

        return data;
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

            SimpleVariable sv = findVariableInAllInternalContexts(variable.name, contextId, execContextId);
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
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParamsYaml);

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
    public Void updateWithTx(InputStream is, long size, Long variableId) {
        Variable v = variableRepository.findById(variableId).orElse(null);
        if (v==null) {
            String es = S.f("#171.660 Variable #%d wasn't found", variableId);
            log.error(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        update(is, size, v);
        return null;
    }

    public void update(InputStream is, long size, Variable data) {
        VariableSyncService.checkWriteLockPresent(data.id);

        if (size==0) {
            throw new IllegalStateException("#171.690 Variable can't be with zero length");
        }
        TxUtils.checkTxExists();
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(SessionImplementor.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;
        data.nullified = false;
        variableRepository.save(data);
    }

    @Transactional
    public void storeData(InputStream is, long size, Long variableId, @Nullable String filename) {
        VariableSyncService.checkWriteLockPresent(variableId);

        if (size==0) {
            throw new IllegalStateException("#171.720 Variable can't be with zero length");
        }
        TxUtils.checkTxExists();

        Variable data = variableRepository.findById(variableId).orElse(null);
        if (data==null) {
            log.error("#171.750 can't find variable #" + variableId);
            return;
        }
        data.filename = filename;
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(SessionImplementor.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
    }

    public void deleteById(Long id) {
        variableRepository.deleteById(id);
    }

    public List<String> getFilenameByVariableAndExecContextId(Long execContextId, String variable) {
        return variableRepository.findFilenameByVariableAndExecContextId(execContextId, variable);
    }

    @Transactional
    public void deleteOrphanVariables(List<Long> ids) {
        variableRepository.deleteByIds(ids);
    }

    @Transactional
    public void deleteOrphanVariable(Long id) {
        variableRepository.deleteById(id);
    }

}
