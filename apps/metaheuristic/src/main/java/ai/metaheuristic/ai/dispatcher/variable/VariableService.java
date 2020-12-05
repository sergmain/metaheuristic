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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.TaskCreatedEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import javax.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@SuppressWarnings("unused")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableService {

    private final EntityManager em;
    private final VariableRepository variableRepository;
    private final ExecContextSyncService execContextSyncService;
    private final GlobalVariableRepository globalVariableRepository;
    private final ApplicationEventPublisher eventPublisher;

    @SneakyThrows
    public void createInputVariablesForSubProcess(
            VariableData.VariableDataSource variableDataSource,
            ExecContextImpl execContext, AtomicInteger currTaskNumber, String inputVariableName,
            String subProcessContextId, DataHolder holder) {

        List<BatchTopLevelService.FileWithMapping> files = variableDataSource.files;
        String inputVariableContent = variableDataSource.inputVariableContent;
        VariableData.Permutation permutation = variableDataSource.permutation;

        if (files.isEmpty() && inputVariableContent==null && permutation==null) {
            throw new IllegalStateException("(files.isEmpty() && inputVariableContent==null && permutation==null)");
        }

        String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));
        if (!files.isEmpty() || inputVariableContent!=null) {
            List<VariableUtils.VariableHolder> variableHolders = new ArrayList<>();
            for (BatchTopLevelService.FileWithMapping f : files) {
                String variableName = S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());

                FileInputStream fis = new FileInputStream(f.file);
                holder.inputStreams.add(fis);
                Variable v = createInitialized(fis, f.file.length(), variableName, f.originName, execContext.id, currTaskContextId);

                SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                variableHolders.add(new VariableUtils.VariableHolder(sv));
            }

            if (!S.b(inputVariableContent)) {
                String variableName = S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());
                final byte[] bytes = inputVariableContent.getBytes();
                InputStream is = new ByteArrayInputStream(bytes);
                holder.inputStreams.add(is);
                Variable v = createInitialized(is, bytes.length, variableName, variableName, execContext.id, currTaskContextId);

                SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                VariableUtils.VariableHolder variableHolder = new VariableUtils.VariableHolder(sv);
                variableHolders.add(variableHolder);
            }

            VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(variableHolders);
            String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
            byte[] bytes = yaml.getBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            holder.inputStreams.add(bais);
            Variable v = createInitialized(bais, bytes.length, inputVariableName, null, execContext.id, currTaskContextId);
        }

        if (permutation!=null) {
            {
                VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(permutation.permutedVariables);
                String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
                byte[] bytes = yaml.getBytes();
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                holder.inputStreams.add(bais);
                Variable v = createInitialized(bais, bytes.length, permutation.permutedVariableName, null, execContext.id, currTaskContextId);
            }
            {
                Yaml yampUtil = YamlUtils.init(Map.class);
                String yaml = yampUtil.dumpAsMap(permutation.inlinePermuted);
                byte[] bytes = yaml.getBytes();
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                holder.inputStreams.add(bais);
                Variable v = createInitialized(bais, bytes.length, permutation.inlineVariableName, null, execContext.id, currTaskContextId);
            }
        }
    }

    @Nullable
    public TaskImpl prepareVariables(ExecContextParamsYaml execContextParamsYaml, TaskImpl task, DataHolder holder) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        final Long execContextId = task.execContextId;
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("#171.020 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+ execContextId);
            return null;
        }

        p.inputs.stream()
                .map(v -> toInputVariable(v, taskParams.task.taskContextId, execContextId))
                .collect(Collectors.toCollection(()->taskParams.task.inputs));

        return initOutputVariables(execContextId, task, p, taskParams, holder);
    }

    private TaskParamsYaml.InputVariable toInputVariable(ExecContextParamsYaml.Variable v, String taskContextId, Long execContextId) {
        TaskParamsYaml.InputVariable iv = new TaskParamsYaml.InputVariable();
        if (v.context== EnumsApi.VariableContext.local || v.context== EnumsApi.VariableContext.array) {
            String contextId = Boolean.TRUE.equals(v.parentContext) ? VariableService.getParentContext(taskContextId) : taskContextId;
            if (S.b(contextId)) {
                throw new TaskCreationException(
                        S.f("#171.040 (S.b(contextId)), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            SimpleVariable variable = findVariableInAllInternalContexts(v.name, contextId, execContextId);
            if (variable==null) {
                throw new TaskCreationException(
                        S.f("#171.060 (variable==null), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
                                v.name, v.context, taskContextId, execContextId));
            }
            iv.id = variable.id;
            iv.filename = variable.filename;
        }
        else {
            SimpleGlobalVariable variable = globalVariableRepository.findIdByName(v.name);
            if (variable==null) {
                throw new TaskCreationException(
                        S.f("#171.080 (variable==null), name: %s, variableContext: %s, taskContextId: %s, execContextId: %s",
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
            throw new SourceCodeException("#171.100 Too many variable '"+variable+"', actual count: " + vars.size());
        }
        return vars.get(0);
    }

    @SuppressWarnings({"SameParameterValue"})
    @Nullable
    public SimpleVariable getVariableAsSimple(String variable, String processCode, ExecContextImpl execContext) {
        ExecContextParamsYaml.Process p = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params).findProcess(processCode);
        if (p==null) {
            return null;
        }
        SimpleVariable v = findVariableInAllInternalContexts(variable, p.internalContextId, execContext.id);
        return v;
    }

    @Nullable
    private SimpleVariable findVariableInAllInternalContexts(String variable, String taskContextId, Long execContextId) {
        String currTaskContextId = taskContextId;
        while( !S.b(currTaskContextId)) {
            SimpleVariable v = variableRepository.findByNameAndTaskContextIdAndExecContextId(variable, currTaskContextId, execContextId);
            if (v!=null) {
                return v;
            }
            currTaskContextId = getParentContext(currTaskContextId);
        }
        return null;
    }

    @Nullable
    private static String getParentContext(String taskContextId) {
        if (!taskContextId.contains(",")) {
            return null;
        }
        return taskContextId.substring(0, taskContextId.lastIndexOf(',')).strip();
    }

    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableId) {
        final String data = getVariableDataAsString(variableId, false);
        if (S.b(data)) {
            final String es = "#171.120 Variable data wasn't found, variableId: " + variableId;
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return data;
    }

    @Nullable
    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableId, boolean nullable) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                if (nullable) {
                    log.info("#171.140 Variable #{} is nullable and current value is null", variableId);
                    return null;
                }
                else {
                    final String es = "#171.160 Variable data wasn't found, variableId: " + variableId;
                    log.warn(es);
                    throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
                }
            }
            try (InputStream is = blob.getBinaryStream()) {
                String s = IOUtils.toString(is, StandardCharsets.UTF_8);
                return s;
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Throwable th) {
            log.error("#171.180", th);
            throw new VariableCommonException("#171.200 Error: " + th.getMessage(), variableId);
        }
    }

    @Nullable
    @Transactional(readOnly = true)
    public Void storeToFile(Long variableId, File trgFile) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                String es = S.f("#171.220 Variable #%d wasn't found", variableId);
                log.warn(es);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "#171.240 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<SimpleVariable> getSimpleVariablesInExecContext(Long execContextId, String ... variables) {
        if (variables.length==0) {
            return List.of();
        }
        return variableRepository.getIdAndStorageUrlInVarsForExecContext(execContextId, variables);
    }

    public Variable createInitialized(InputStream is, long size, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContextId);

        Variable data = new Variable();
        data.inited = true;
        data.nullified = false;
        data.setName(variable);
        data.setFilename(filename);
        data.setExecContextId(execContextId);
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        data.setTaskContextId(taskContextId);

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);

//            log.info("Start to create an initialized variable {}, execContextId: {}, taskContextId: {}", variable, execContextId, taskContextId);
        variableRepository.save(data);

        return data;
    }

    public TaskImpl initOutputVariables(Long execContextId, TaskImpl task, ExecContextParamsYaml.Process p, TaskParamsYaml taskParamsYaml, DataHolder holder) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContextId);

        for (ExecContextParamsYaml.Variable variable : p.outputs) {
            String contextId = Boolean.TRUE.equals(variable.parentContext) ? getParentContext(taskParamsYaml.task.taskContextId) : taskParamsYaml.task.taskContextId;
            if (S.b(contextId)) {
                throw new IllegalStateException(
                        S.f("(S.b(contextId)), process code: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
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

        TaskCreatedEvent event = new TaskCreatedEvent(
                new ExecContextApiData.TaskStateInfo(task.id, execContextId,
                        taskParamsYaml.task.taskContextId, taskParamsYaml.task.processCode, taskParamsYaml.task.function.code,
                        null,
                        taskParamsYaml.task.outputs.stream().map(o -> new ExecContextApiData.VariableInfo(o.id, o.name, o.context, o.ext)).collect(Collectors.toList())));
        holder.events.add(event);

        return task;
    }

    private Variable createUninitialized(String variable, Long execContextId, String taskContextId) {
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

    public void update(InputStream is, long size, Variable data) {
        TxUtils.checkTxExists();
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;
        data.nullified = false;
    }

    public void storeData(InputStream is, long size, Long variableId, @Nullable String filename) {
        TxUtils.checkTxExists();

        Variable data = variableRepository.findById(variableId).orElse(null);
        if (data==null) {
            log.error("#171.260 can't find variable #" + variableId);
            return;
        }
        data.filename = filename;
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
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
    public Void deleteOrphanVariables(Long execContextId) {
        List<Long> ids = variableRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId) ;
        if (ids.isEmpty()) {
            return null;
        }
        variableRepository.deleteAllByIdIn(ids);
        return null;
    }

}
