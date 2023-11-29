/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.DeleteExecContextInListTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.ProcessDeletedExecContextTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TaskQueueCleanByExecContextIdTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.HttpUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextsListItem;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.OperationStatus;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
@SuppressWarnings("UnusedReturnValue")
public class ExecContextTxService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final ExecContextCache execContextCache;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;
    private final TaskTxService taskTxService;
    private final VariableRepository variableRepository;
    private final VariableTxService variableService;
    private final EventPublisherService eventPublisherService;
    private final ExecContextUtilsService execContextUtilsServices;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
        initInfoAboutSourceCode(sourceCodeId, result);
        return result;
    }

    @Transactional
    public void changeValidStatus(Long execContextId, boolean status) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        execContext.setValid(status);
        execContextCache.save(execContext);
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId, true);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("705.180 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("705.200 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

    public ExecContextApiData.RawExecContextStateResult getRawExecContextState(Long sourceCodeId, Long execContextId) {
        TxUtils.checkTxNotExists();

        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult(sourceCodeId, globals.dispatcher.asset.mode);
        initInfoAboutSourceCode(sourceCodeId, result);

        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (ec == null) {
            ExecContextApiData.RawExecContextStateResult resultWithError = new ExecContextApiData.RawExecContextStateResult("705.220 Can't find execContext for Id " + execContextId);
            return resultWithError;
        }
        ExecContextApiData.ExecContextVariableStates info = execContextUtilsServices.getExecContextVariableStates(ec.execContextVariableStateId);
        ExecContextParamsYaml ecpy = ec.getExecContextParamsYaml();

        List<String> processCodes = ExecContextProcessGraphService.getTopologyOfProcesses(ecpy);
        return new ExecContextApiData.RawExecContextStateResult(
                sourceCodeId, info.states, processCodes, result.sourceCodeType, result.sourceCodeUid, result.sourceCodeValid,
                taskTxService.getExecStateOfTasks(execContextId)
        );
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

    private ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        pageable = PageUtils.fixPageSize(globals.dispatcher.rowsLimit.execContext, pageable);
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult(sourceCodeId, globals.dispatcher.asset.mode);
        result.instances = execContextRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        for (ExecContextsListItem instance : result.instances) {
            ExecContextImpl ec = execContextCache.findById(instance.id, true);
            if (ec==null) {
                continue;
            }
            instance.rootExecContext = ec.rootExecContextId==null;
        }
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
    public void deleteExecContext(Long execContextId) {
        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (ec==null) {
            return;
        }
        eventPublisherService.publishProcessDeletedExecContextTxEvent(new ProcessDeletedExecContextTxEvent(
                execContextId, ec.execContextGraphId, ec.execContextTaskStateId, ec.execContextVariableStateId));

        List<Long> relatedIds = execContextRepository.findAllRelatedExecContextIds(execContextId);
        eventPublisherService.publishDeleteExecContextInListTxEvent(new DeleteExecContextInListTxEvent(relatedIds));

        eventPublisherService.publishTaskQueueCleanByExecContextIdTxEvent(new TaskQueueCleanByExecContextIdTxEvent(execContextId));
        execContextCache.deleteById(execContextId);
        // tasks and variables will be deleted in another thread launched by Scheduler
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.ExecContextForDeletion getExecContextExtendedForDeletion(Long execContextId, DispatcherContext context) {
        ExecContextImpl execContext = execContextCache.findById(execContextId, true);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextForDeletion("705.260 execContext wasn't found, execContextId: " + execContextId);
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
        ExecContext wb = execContextCache.findById(execContextId, true);
        if (wb==null) {
            return new OperationStatusRest(OperationStatus.ERROR, "705.280 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }


    public CleanerInfo downloadVariable(Long execContextId, Long variableId, Long companyId) {
        CleanerInfo resource = new CleanerInfo();
        try {
            ExecContextImpl execContext = execContextCache.findById(execContextId, true);
            if (execContext==null) {
                return resource;
            }

            Path resultDir = DirUtils.createMhTempPath("prepare-file-processing-result-");
            if (resultDir==null) {
                throw new RuntimeException("705.290 can't create temp directory");
            }
            resource.toClean.add(resultDir);

            Path zipDir = resultDir.resolve("zip");
            Files.createDirectory(zipDir);

            SourceCodeImpl sc = sourceCodeCache.findById(execContext.sourceCodeId);
            if (sc==null) {
                final String es = "705.300 SourceCode wasn't found, sourceCodeId: " + execContext.sourceCodeId;
                log.warn(es);
                return resource;
            }

            Variable v = variableRepository.findByIdAsSimple(variableId);
            if (v==null) {
                final String es = "705.330 Can't find variable #"+variableId;
                log.warn(es);
                return resource;
            }

            String ext = execContextUtilsServices.getExtensionForVariable(execContext.execContextVariableStateId, variableId, CommonConsts.BIN_EXT);

            String filename = S.f("variable-%s-%s%s", variableId, v.name, ext);

            Path varFile = resultDir.resolve("variable-"+variableId+ CommonConsts.BIN_EXT);
            variableService.storeToFileWithTx(v.id, varFile);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpUtils.setContentDisposition(httpHeaders, filename);
            resource.entity = new ResponseEntity<>(new FileSystemResource(varFile), RestUtils.getHeader(httpHeaders, Files.size(varFile)), HttpStatus.OK);
            return resource;
        } catch (VariableDataNotFoundException e) {
            log.error("705.350 Variable #{}, context: {}, {}", e.variableId, e.context, e.getMessage());
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        } catch (Throwable th) {
            log.error("705.370 General error", th);
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        }
    }

    @Transactional
    public void deleteOrphanExecContextGraph(Long execContextGraphId) {
        execContextGraphRepository.deleteById(execContextGraphId);

    }

    @Transactional
    public void deleteOrphanExecContextTaskState(Long execContextTaskStateId) {
        execContextTaskStateRepository.deleteById(execContextTaskStateId);
    }

    @Transactional
    public void deleteOrphanExecContextVariableState(Long execContextVariableStateId) {
        execContextVariableStateRepository.deleteById(execContextVariableStateId);
    }
}
