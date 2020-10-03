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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableService {

    private final EntityManager em;
    private final VariableRepository variableRepository;
    private final Globals globals;
    private final ExecContextSyncService execContextSyncService;

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

    @Nullable
    @Transactional(readOnly = true)
    public Variable getBinaryData(Long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("#087.010 this method intended to be only for test cases");
        }
        return getBinaryData(id, true);
    }

    @Nullable
    private Variable getBinaryData(Long id, @SuppressWarnings("SameParameterValue") boolean isInitBytes) {
        try {
            Variable v = variableRepository.findById(id).orElse(null);
            if (v==null) {
                return null;
            }
            if (isInitBytes) {
                Blob blob = v.getData();
                v.bytes = blob.getBytes(1, (int) blob.length());
            }
            return v;
        } catch (Throwable th) {
            throw new VariableCommonException("#087.020 Error: " + th.getMessage(), id);
        }
    }

    @Transactional(readOnly = true)
    public String getVariableDataAsString(Long variableId) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                log.warn("#087.028 Binary data for variableId {} wasn't found", variableId);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, "#087.028 Variable data wasn't found, variableId: " + variableId);
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

    @Transactional
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
    public Variable createInitialized(InputStream is, long size, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
//        try {
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
//        }
//        catch(VariableSavingException | PessimisticLockingFailureException e) {
//            throw e;
//        } catch(Throwable th) {
//            throw new VariableSavingException("#087.060 error storing data to db - " + th.getMessage(), th);
//        }
    }

    public void initOutputVariables(TaskParamsYaml taskParams, ExecContextImpl execContext, ExecContextParamsYaml.Process p) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContext.id);

        for (ExecContextParamsYaml.Variable variable : p.outputs) {
            String contextId = Boolean.TRUE.equals(variable.parentContext) ? getParentContext(taskParams.task.taskContextId) : taskParams.task.taskContextId;
            if (S.b(contextId)) {
                throw new IllegalStateException(
                        S.f("(S.b(contextId)), process code: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
                                p.processCode, variable.context, p.internalContextId, execContext.id));
            }

            SimpleVariable sv = findVariableInAllInternalContexts(variable.name, contextId, execContext.id);
            if (sv == null) {
//                log.info(S.f("Variable %s wasn't initialized for process %s.", variable.name, p.processCode));
                Variable v = createUninitialized(variable.name, execContext.id, contextId);

                // even variable.getNullable() can be false, we set empty to true because variable will be inited later
                // and consistency of fields 'empty'  and 'nullable' will be enforced before calling Functions
                taskParams.task.outputs.add(
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
    }

    private Variable createUninitialized(String variable, Long execContextId, String taskContextId) {
        Variable v = new Variable();
        v.name = variable;
        v.setExecContextId(execContextId);
        v.setTaskContextId(taskContextId);
        return createOrUpdateUninitialized(v);
    }

    private Variable createOrUpdateUninitialized(Variable v) {
//        try {
            v.inited = false;
            v.nullified = true;

            // TODO 2020-02-03 right now, only DataSourcing.dispatcher is supported as internal variable.
            //   a new code has to be added for another type of sourcing
            v.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, v.name)));

            v.setUploadTs(new Timestamp(System.currentTimeMillis()));
            log.info("Start to create an uninitialized variable {}, execContextId: {}, taskContextId: {}, id: {}", v.name, v.execContextId, v.taskContextId, v.id);
            return variableRepository.save(v);
/*
        }
        catch (PessimisticLockingFailureException e) {
            throw e;
        }
        catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Throwable th) {
            throw new VariableSavingException("#087.070 error storing v to db - " + th.getMessage(), th);
        }
*/
    }

    public void update(InputStream is, long size, Variable data) {
        TxUtils.checkTxExists();
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
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

    public Page<Variable> findAll(Pageable pageable) {
        return variableRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        variableRepository.deleteById(id);
    }

    public List<String> getFilenameByVariableAndExecContextId(Long execContextId, String variable) {
        return variableRepository.findFilenameByVariableAndExecContextId(execContextId, variable);
    }

    public Variable createInitializedFromFile(File tempFile, String variable, String filename, Long execContextId, String internalContextId) {
        try {
            try (InputStream is = new FileInputStream(tempFile)) {
                return createInitialized(is, tempFile.length(), variable, filename, execContextId, internalContextId);
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new StoreNewFileException("#087.080 Error while storing", e, tempFile.getPath(), filename);
        }
    }

    public Set<String> findAllByExecContextIdAndVariableNames(Long execContextId, Set<String> vars) {
        return variableRepository.findTaskContextIdsByExecContextIdAndVariableNames(execContextId, vars);
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
