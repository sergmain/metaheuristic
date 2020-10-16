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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableService {

    private final EntityManager em;
    private final VariableRepository variableRepository;
    private final ExecContextSyncService execContextSyncService;
    private final GlobalVariableRepository globalVariableRepository;

    /**
     * @param execContextParamsYaml
     * @param task
     * @return
     */
    @Nullable
    public TaskImpl prepareVariables(ExecContextParamsYaml execContextParamsYaml, TaskImpl task) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        final Long execContextId = task.execContextId;
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("#303.600 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+ execContextId);
            return null;
        }

        p.inputs.stream()
                .map(v -> toInputVariable(v, taskParams.task.taskContextId, execContextId))
                .collect(Collectors.toCollection(()->taskParams.task.inputs));

        return initOutputVariables(execContextId, task, p, taskParams);
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
            throw new SourceCodeException("Too many variable '"+variable+"', actual count: " + vars.size());
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
    public SimpleVariable findVariableInAllInternalContexts(String variable, String taskContextId, Long execContextId) {
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
    public static String getParentContext(String taskContextId) {
        if (!taskContextId.contains(",")) {
            return null;
        }
        return taskContextId.substring(0, taskContextId.lastIndexOf(',')).strip();
    }

    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableId) {
        final String data = getVariableDataAsString(variableId, false);
        if (S.b(data)) {
            final String es = "#087.028 Variable data wasn't found, variableId: " + variableId;
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
                    log.info("#087.027 Variable #{} is nullable and current value is null", variableId);
                    return null;
                }
                else {
                    final String es = "#087.028 Variable data wasn't found, variableId: " + variableId;
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
            log.error("#087.020", th);
            throw new VariableCommonException("#087.020 Error: " + th.getMessage(), variableId);
        }
    }

    @Transactional(readOnly = true)
    public void storeToFile(Long variableId, File trgFile) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                String es = S.f("#087.030 Data for variableId #%d wasn't found", variableId);
                log.warn(es);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (CommonErrorWithDataException e) {
            throw e;
        } catch (Exception e) {
            String es = "#087.050 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    @Transactional(readOnly = true)
    public List<SimpleVariable> getSimpleVariablesInExecContext(Long execContextId, String ... variables) {
        if (variables.length==0) {
            return List.of();
        }
        return variableRepository.getIdAndStorageUrlInVarsForExecContext(execContextId, variables);
    }

    @Transactional
    public Variable createInitializedWithTx(InputStream is, long size, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
        return createInitialized(is, size, variable, filename, execContextId, taskContextId);
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

    public TaskImpl initOutputVariables(Long execContextId, TaskImpl task, ExecContextParamsYaml.Process p, TaskParamsYaml taskParamsYaml) {
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
//                log.info(S.f("Variable %s wasn't initialized for process %s.", variable.name, p.processCode));
                Variable v = createUninitialized(variable.name, execContextId, contextId);

                // even variable.getNullable() can be false, we set empty to true because variable will be inited later
                // and consistency of fields 'empty'  and 'nullable' will be enforced before calling Functions
                taskParamsYaml.task.outputs.add(
                        new TaskParamsYaml.OutputVariable(
                                v.id, EnumsApi.VariableContext.local, variable.name, variable.sourcing, variable.git, variable.disk,
                                null, false, variable.type, true, variable.getNullable()
                        ));
            }
/*
            else {
                Variable v = variableRepository.findById(sv.id).orElse(null);
                if (v!=null) {
//                    log.warn(S.f("Variable %s was already initialized for process %s. May be there is double declaration of variables", variable.name, p.processCode));
                    // TODO 2020-09-28 do we need  taskParams.task.outputs.add()  here?
//                    v = createOrUpdateUninitialized(v);
                    continue;
                }
                else {
                    throw new IllegalStateException("!!! A fatal error");
                }
            }
*/
        }
        task.updatedOn = System.currentTimeMillis();
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParamsYaml);
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
//            log.info("Start creating an uninitialized variable {}, execContextId: {}, taskContextId: {}, id: {}", v.name, v.execContextId, v.taskContextId, v.id);
        return variableRepository.save(v);
    }

    @Transactional
    public Void updateWithTx(InputStream is, long size, Variable data) {
        update(is, size, data);
        return null;
    }

    public void update(InputStream is, long size, Variable data) {
        TxUtils.checkTxExists();
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;
        data.nullified = false;
    }

    public void storeData(InputStream is, long size, SimpleVariable simpleVariable, @Nullable String filename) {
        TxUtils.checkTxExists();

        Variable data = variableRepository.findById(simpleVariable.id).orElse(null);
        if (data==null) {
            log.error("#087.075 can't find variable #" + simpleVariable.id);
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
