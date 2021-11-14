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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.event.DeleteExecContextTxEvent;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextTxEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskQueueCleanByExecContextIdTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.EnumsApi.OperationStatus;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("UnusedReturnValue")
public class ExecContextService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final ExecContextCache execContextCache;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;
    private final TaskRepository taskRepository;
    private final VariableRepository variableRepository;
    private final EntityManager em;
    private final VariableService variableService;
    private final EventPublisherService eventPublisherService;
    private final ExecContextUtilsService execContextUtilsServices;

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
        initInfoAboutSourceCode(sourceCodeId, result);
        return result;
    }

    @Nullable
    public ExecContextImpl findById(Long id) {
        return execContextCache.findById(id);
    }

    @Transactional
    public Void changeValidStatus(Long execContextId, boolean status) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        execContext.setValid(status);
        save(execContext);
        return null;
    }

    public ExecContextImpl save(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        if (execContext.id!=null) {
            ExecContextSyncService.checkWriteLockPresent(execContext.id);
        }
        if (execContext.id==null) {
            final ExecContextImpl ec = execContextCache.save(execContext);
            return ec;
        }
        else if (!em.contains(execContext) ) {
//            https://stackoverflow.com/questions/13135309/how-to-find-out-whether-an-entity-is-detached-in-jpa-hibernate
            throw new IllegalStateException(S.f("#705.020 Bean %s isn't managed by EntityManager", execContext));
        }
        return execContext;
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("#705.180 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("#705.200 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

    public ExecContextApiData.RawExecContextStateResult getRawExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context) {
        TxUtils.checkTxNotExists();

        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult(sourceCodeId, globals.dispatcher.asset.mode);
        initInfoAboutSourceCode(sourceCodeId, result);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            ExecContextApiData.RawExecContextStateResult resultWithError = new ExecContextApiData.RawExecContextStateResult("#705.220 Can't find execContext for Id " + execContextId);
            return resultWithError;
        }
        ExecContextApiData.ExecContextVariableStates info = execContextUtilsServices.getExecContextVariableStates(ec.execContextVariableStateId);
        ExecContextParamsYaml ecpy = ec.getExecContextParamsYaml();

        List<String> processCodes = ExecContextProcessGraphService.getTopologyOfProcesses(ecpy);
        return new ExecContextApiData.RawExecContextStateResult(
                sourceCodeId, info.tasks, processCodes, result.sourceCodeType, result.sourceCodeUid, result.sourceCodeValid,
                getExecStateOfTasks(execContextId)
        );
    }

    public Map<Long, TaskApiData.TaskState> getExecStateOfTasks(Long execContextId) {
        List<Object[]> list = taskRepository.findExecStateByExecContextId(execContextId);

        Map<Long, TaskApiData.TaskState> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            TaskApiData.TaskState taskState = new TaskApiData.TaskState(o);
            states.put(taskState.taskId, taskState);
        }
        return states;
    }

    private void initInfoAboutSourceCode(Long sourceCodeId, ExecContextApiData.ExecContextsResult result) {
        SourceCodeImpl sc = sourceCodeCache.findById(sourceCodeId);
        if (sc != null) {
            result.sourceCodeUid = sc.uid;
            result.sourceCodeValid = sc.valid;
            result.sourceCodeType = getType(sc.uid);
        } else {
            result.sourceCodeUid = "SourceCode was deleted";
            result.sourceCodeValid = false;
            result.sourceCodeType = EnumsApi.SourceCodeType.not_exist;
        }
    }

    private EnumsApi.SourceCodeType getType(String uid) {
        if (dispatcherParamsTopLevelService.getBatches().contains(uid)) {
            return EnumsApi.SourceCodeType.batch;
        } else if (dispatcherParamsTopLevelService.getExperiments().contains(uid)) {
            return EnumsApi.SourceCodeType.experiment;
        }
        return EnumsApi.SourceCodeType.common;
    }

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        pageable = ControllerUtils.fixPageSize(globals.dispatcher.rowsLimit.execContext, pageable);
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult(sourceCodeId, globals.dispatcher.asset.mode);
        result.instances = execContextRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        return result;
    }

    @Transactional
    public void deleteExecContext(Long execContextId, Long companyUniqueId) {
        deleteExecContext(execContextId);
    }

    @Transactional
    public void deleteExecContextFromCache(Long execContextId) {
        execContextCache.deleteById(execContextId);
    }

    @Transactional
    public Void deleteExecContext(Long execContextId) {
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return null;
        }
        eventPublisherService.publishProcessDeletedExecContextTxEvent(new ProcessDeletedExecContextTxEvent(
                execContextId, ec.execContextGraphId, ec.execContextTaskStateId, ec.execContextVariableStateId));

        List<Long> relatedIds = execContextRepository.findAllRelatedExecContextIds(execContextId);
        for (Long relatedExecContextId : relatedIds) {
            eventPublisherService.publishDeleteExecContextTxEvent(new DeleteExecContextTxEvent(relatedExecContextId));
        }

        eventPublisherService.publishTaskQueueCleanByExecContextIdTxEvent(new TaskQueueCleanByExecContextIdTxEvent(execContextId));
        execContextCache.deleteById(execContextId);
        // tasks and variables will be deleted in another thread launched by Scheduler
        return null;
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.ExecContextForDeletion getExecContextExtendedForDeletion(Long execContextId, DispatcherContext context) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextForDeletion("#705.260 execContext wasn't found, execContextId: " + execContextId);
        }
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
        SourceCodeApiData.ExecContextForDeletion result = new SourceCodeApiData.ExecContextForDeletion(execContext.sourceCodeId, execContext.id, ecpy.sourceCodeUid, EnumsApi.ExecContextState.from(execContext.state));
        return result;
    }

    @Transactional
    public OperationStatusRest deleteExecContextById(Long execContextId, DispatcherContext context) {
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        deleteExecContext(execContextId, context.getCompanyId());

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId, DispatcherContext context) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(OperationStatus.ERROR, "#705.280 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }


    public CleanerInfo downloadVariable(Long execContextId, Long variableId, Long companyId) {
        CleanerInfo resource = new CleanerInfo();
        try {
            ExecContextImpl execContext = execContextCache.findById(execContextId);
            if (execContext==null) {
                return resource;
            }

            File resultDir = DirUtils.createMhTempDir("prepare-file-processing-result-");
            resource.toClean.add(resultDir);

            File zipDir = new File(resultDir, "zip");
            //noinspection ResultOfMethodCallIgnored
            zipDir.mkdir();

            SourceCodeImpl sc = sourceCodeCache.findById(execContext.sourceCodeId);
            if (sc==null) {
                final String es = "#705.300 SourceCode wasn't found, sourceCodeId: " + execContext.sourceCodeId;
                log.warn(es);
                return resource;
            }

            SimpleVariable variable = variableRepository.findByIdAsSimple(variableId);
            if (variable==null) {
                final String es = "#705.330 Can't find variable #"+variableId;
                log.warn(es);
                return resource;
            }

            String ext = execContextUtilsServices.getExtensionForVariable(execContext.execContextVariableStateId, variableId, ".bin");

            String filename = S.f("variable-%s-%s%s", variableId, variable.variable, ext);

            File varFile = new File(resultDir, "variable-"+variableId+".bin");
            variableService.storeToFileWithTx(variable.id, varFile);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            // https://stackoverflow.com/questions/93551/how-to-encode-the-filename-parameter-of-content-disposition-header-in-http
            httpHeaders.setContentDisposition(ContentDisposition.parse(
                    "filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())));
            resource.entity = new ResponseEntity<>(new FileSystemResource(varFile), RestUtils.getHeader(httpHeaders, varFile.length()), HttpStatus.OK);
            return resource;
        } catch (VariableDataNotFoundException e) {
            log.error("#705.350 Variable #{}, context: {}, {}", e.variableId, e.context, e.getMessage());
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        } catch (Throwable th) {
            log.error("#705.370 General error", th);
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        }
    }
}
