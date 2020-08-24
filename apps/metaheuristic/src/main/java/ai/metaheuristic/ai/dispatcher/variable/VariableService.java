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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
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
import org.springframework.dao.PessimisticLockingFailureException;
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
import java.util.Optional;
import java.util.Set;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@Service
@Transactional
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableService {

    private final EntityManager em;
    private final VariableRepository variableRepository;
    private final Globals globals;

    @SuppressWarnings({"SameParameterValue"})
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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

    public String getVariableDataAsString(Long variableId) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                log.warn("#087.0286 Binary data for variableId {} wasn't found", variableId);
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

    public void storeToFile(Long variableId, File trgFile) {
        try {
            Blob blob = variableRepository.getDataAsStreamById(variableId);
            if (blob==null) {
                log.warn("#087.030 Binary data for variableId {} wasn't found", variableId);
                throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, "#087.040 Variable data wasn't found, variableId: " + variableId);
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

    public void deleteByExecContextId(Long execContextId) {
        variableRepository.deleteByExecContextId(execContextId);
    }

    @Transactional(readOnly = true)
    public List<SimpleVariable> getSimpleVariablesInExecContext(Long execContextId, String ... variables) {
        if (variables.length==0) {
            return List.of();
        }
        return variableRepository.getIdAndStorageUrlInVarsForExecContext(execContextId, variables);
    }

    public void deleteByVariable(String variable) {
        variableRepository.deleteByName(variable);
    }

    public Variable createInitialized(InputStream is, long size, String variable, @Nullable String filename, Long execContextId, String taskContextId) {
        try {
            Variable data = new Variable();
            data.inited = true;
            data.setName(variable);
            data.setFilename(filename);
            data.setExecContextId(execContextId);
            data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, variable)));
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setTaskContextId(taskContextId);

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            variableRepository.save(data);

            return data;
        }
        catch(VariableSavingException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new VariableSavingException("#087.060 error storing data to db - " + th.getMessage(), th);
        }
    }

    public void initOutputVariables(TaskParamsYaml taskParams, ExecContextImpl execContext, ExecContextParamsYaml.Process p) {
        for (ExecContextParamsYaml.Variable variable : p.outputs) {
            SimpleVariable sv = getVariableAsSimple(variable.name, p.processCode, execContext);
            if (sv!=null) {
                continue;
            }
            String contextId = Boolean.TRUE.equals(variable.parentContext) ? getParentContext(taskParams.task.taskContextId) : taskParams.task.taskContextId;
            if (S.b(contextId)) {
                throw new IllegalStateException(
                        S.f("(S.b(contextId)), process code: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
                                p.processCode, variable.context, p.internalContextId, execContext.id));
            }
            Variable v = createUninitialized(variable.name, execContext.id, contextId);

            taskParams.task.outputs.add(
                    new TaskParamsYaml.OutputVariable(
                            v.id, EnumsApi.VariableContext.local, variable.name, variable.sourcing, variable.git, variable.disk,
                            null, false, variable.type
                    ));
        }
    }

    public Variable createUninitialized(String variable, Long execContextId, String taskContextId) {
        try {
            Variable data = new Variable();
            data.inited = false;
            data.setName(variable);
            data.setExecContextId(execContextId);

            // TODO right now only DataSourcing.dispatcher is supporting as internal variable.
            //  the code has to be added for another type of sourcing
            data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.dispatcher, variable)));

            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setTaskContextId(taskContextId);
            variableRepository.save(data);
            return data;
        }
        catch(PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new VariableSavingException("#087.070 error storing data to db - " + th.getMessage(), th);
        }
    }

    public void update(InputStream is, long size, Variable data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;

        variableRepository.save(data);
    }

    public Page<Variable> findAll(Pageable pageable) {
        return variableRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        variableRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Variable> findById(Long id) {
        return variableRepository.findById(id);
    }

    @Transactional(readOnly = true)
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
}
