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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.exceptions.BreakFromForEachException;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.*;
import ai.metaheuristic.ai.launchpad.variable.VariableService;
import ai.metaheuristic.ai.launchpad.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.launchpad.event.LaunchpadEventService;
import ai.metaheuristic.ai.launchpad.event.LaunchpadInternalEvent;
import ai.metaheuristic.ai.launchpad.repositories.IdsRepository;
import ai.metaheuristic.ai.launchpad.task.TaskProducingService;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeService;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeUtils;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.holders.LongHolder;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.ExecContext;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.SnippetCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("UnusedReturnValue")
public class WorkbookService {

    private final Globals globals;
    private final WorkbookRepository workbookRepository;
    private final SourceCodeCache sourceCodeCache;
    private final VariableService variableService;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final StationCache stationCache;
    private final WorkbookCache workbookCache;
    private final WorkbookGraphService workbookGraphService;
    private final WorkbookSyncService workbookSyncService;
    private final LaunchpadEventService launchpadEventService;
    private final WorkbookFSM workbookFSM;
    private final TaskProducingService taskProducingService;
    private final WorkbookGraphTopLevelService workbookGraphTopLevelService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final IdsRepository idsRepository;

    public OperationStatusRest resetBrokenTasks(Long workbookId) {
        final ExecContextImpl workbook = workbookCache.findById(workbookId);
        if (workbook==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.003 Can't find execContext with id #"+workbookId);
        }
        List<WorkbookParamsYaml.TaskVertex> vertices = workbookGraphTopLevelService.findAllBroken(workbook);
        if (vertices==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.005 Can't find execContext with id #"+workbookId);
        }
        for (WorkbookParamsYaml.TaskVertex vertex : vertices) {
            resetTask(vertex.taskId);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest workbookTargetExecState(Long workbookId, EnumsApi.ExecContextState execState) {
        SourceCodeApiData.ExecContextResult result = getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        final ExecContextImpl workbook = (ExecContextImpl) result.execContext;
        final SourceCode sourceCode = result.sourceCode;
        if (sourceCode ==null || workbook ==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#701.110 Error: (result.sourceCode==null || result.execContext==null)");
        }

        if (workbook.execState!=execState.code) {
            workbookFSM.toState(workbook.id, execState);
            applicationEventPublisher.publishEvent(new LaunchpadInternalEvent.SourceCodeLockingEvent(sourceCode.getId(), sourceCode.getCompanyId(), true));
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest resetTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.010 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        // TODO 2019-11-03 need to investigate why without this call nothing is working
        Task t = taskPersistencer.resetTask(task.id);
        if (t==null) {
            WorkbookOperationStatusWithTaskList withTaskList = workbookGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsBroken(task.getWorkbookId(), task.id);
            taskPersistencer.updateTasksStateInDb(withTaskList);
            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.030 Can't re-run task #" + taskId + ", see log for more information");
            }
        }
        else {
            WorkbookOperationStatusWithTaskList withTaskList = workbookGraphTopLevelService.updateGraphWithResettingAllChildrenTasks(task.workbookId, task.id);
            if (withTaskList == null) {
                taskPersistencer.finishTaskAsBrokenOrError(taskId, EnumsApi.TaskExecState.BROKEN);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#705.020 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any execContext");
            }

            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.040 Can't re-run task #" + taskId + ", see log for more information");
            }
            taskPersistencer.updateTasksStateInDb(withTaskList);

            ExecContextImpl workbook = workbookCache.findById(task.workbookId);
            if (workbook.execState != EnumsApi.ExecContextState.STARTED.code) {
                workbookFSM.toState(workbook.id, EnumsApi.ExecContextState.STARTED);
            }
        }

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public long getCountUnfinishedTasks(ExecContextImpl workbook) {
        return getCountUnfinishedTasks(workbook.id);
    }

    public Long getCountUnfinishedTasks(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbookGraphService::getCountUnfinishedTasks);
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllVertices(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbookGraphService::findAll);
    }

    public SourceCodeApiData.TaskProducingResultComplex createWorkbook(Long sourceCodeId, WorkbookParamsYaml.WorkbookYaml workbookYaml) {
        return createWorkbook(sourceCodeId, workbookYaml, true);
    }

    public SourceCodeApiData.TaskProducingResultComplex createWorkbook(Long sourceCodeId, WorkbookParamsYaml.WorkbookYaml workbookYaml, boolean checkResources) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();

        ExecContextImpl wb = new ExecContextImpl();
        wb.setSourceCodeId(sourceCodeId);
        wb.setCreatedOn(System.currentTimeMillis());
        wb.setExecState(EnumsApi.ExecContextState.NONE.code);
        wb.setCompletedOn(null);
        WorkbookParamsYaml params = new WorkbookParamsYaml();
        params.workbookYaml = workbookYaml;
        params.graph = WorkbookGraphService.EMPTY_GRAPH;
        wb.updateParams(params);
        wb.setValid(true);

        if (checkResources) {
            WorkbookParamsYaml resourceParam = wb.getWorkbookParamsYaml();
            List<SimpleVariableAndStorageUrl> inputResourceCodes = variableService.getIdInVariables(resourceParam.getAllPoolCodes());
            if (inputResourceCodes == null || inputResourceCodes.isEmpty()) {
                result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return result;
            }
        }

        result.execContext = workbookCache.save(wb);
        result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.OK;

        return result;
    }

    public Void changeValidStatus(Long execContextId, boolean status) {
        return workbookSyncService.getWithSync(execContextId, workbook -> {
            workbook.setValid(status);
            workbookCache.save(workbook);
            return null;
        });
    }

    public EnumsApi.SourceCodeProducingStatus toProducing(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState == EnumsApi.ExecContextState.PRODUCING.code) {
                return EnumsApi.SourceCodeProducingStatus.OK;
            }
            workbook.setExecState(EnumsApi.ExecContextState.PRODUCING.code);
            workbookCache.save(workbook);
            return EnumsApi.SourceCodeProducingStatus.OK;
        });
    }

    public SourceCodeApiData.ExecContextResult getWorkbookExtended(Long execContextId) {
        if (execContextId==null) {
            return new SourceCodeApiData.ExecContextResult("#705.090 execContextId is null");
        }
        ExecContextImpl execContext = workbookCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("#705.100 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("#705.110 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        if (!sourceCode.getId().equals(execContext.getSourceCodeId())) {
            changeValidStatus(execContextId, false);
            return new SourceCodeApiData.ExecContextResult("#705.120 sourceCodeId doesn't match to execContext.sourceCodeId, sourceCodeId: " + execContext.getSourceCodeId()+", execContext.sourceCodeId: " + execContext.getSourceCodeId());
        }

        //noinspection UnnecessaryLocalVariable
        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

    public SourceCodeApiData.ExecContextsResult getWorkbooksOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, LaunchpadContext context) {
        pageable = ControllerUtils.fixPageSize(globals.execContextRowsLimit, pageable);
        SourceCodeApiData.ExecContextsResult result = new SourceCodeApiData.ExecContextsResult();
        result.instances = workbookRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        result.currentSourceCodeId = sourceCodeId;

        for (ExecContext execContext : result.instances) {
            WorkbookParamsYaml wpy = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(execContext.getParams());
            wpy.graph = null;
            execContext.setParams( WorkbookParamsYamlUtils.BASE_YAML_UTILS.toString(wpy) );
            SourceCode sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
            if (sourceCode ==null) {
                log.warn("#705.130 Found execContext with wrong sourceCodeId. sourceCodeId: {}", execContext.getSourceCodeId());
                continue;
            }
            result.sourceCodes.put(execContext.getId(), sourceCode);
        }
        return result;
    }

    // TODO 2019.08.27 is it good to synchronize the whole method?
    //  but it's working actually
    public synchronized LaunchpadCommParamsYaml.AssignedTask getTaskAndAssignToStation(long stationId, boolean isAcceptOnlySigned, Long workbookId) {

        final Station station = stationCache.findById(stationId);
        if (station == null) {
            log.error("#705.140 Station wasn't found for id: {}", stationId);
            return null;
        }
        List<Long> anyTaskId = taskRepository.findAnyActiveForStationId(Consts.PAGE_REQUEST_1_REC, stationId);
        if (!anyTaskId.isEmpty()) {
            // this station already has active task
            log.info("#705.160 can't assign any new task to the station #{} because this station has an active task #{}", stationId, anyTaskId);
            return null;
        }

        List<Long> workbookIds;
        // find task in specific execContext ( i.e. workbookId==null)?
        if (workbookId==null) {
            workbookIds = workbookRepository.findByExecStateOrderByCreatedOnAsc(EnumsApi.ExecContextState.STARTED.code);
        }
        else {
            ExecContextImpl workbook = workbookCache.findById(workbookId);
            if (workbook==null) {
                log.warn("#705.170 ExecContext wasn't found for id: {}", workbookId);
                return null;
            }
            if (workbook.getExecState()!= EnumsApi.ExecContextState.STARTED.code) {
                log.warn("#705.180 ExecContext wasn't started. Current exec state: {}", EnumsApi.ExecContextState.toState(workbook.getExecState()));
                return null;
            }
            workbookIds = List.of(workbook.id);
        }

        StationStatusYaml ss;
        try {
            ss = StationStatusYamlUtils.BASE_YAML_UTILS.to(station.status);
        } catch (Throwable e) {
            log.error("#705.150 Error parsing current status of station:\n{}", station.status);
            log.error("#705.151 Error ", e);
            return null;
        }

        for (Long wbId : workbookIds) {
            LaunchpadCommParamsYaml.AssignedTask result = findUnassignedTaskAndAssign(wbId, station, ss, isAcceptOnlySigned);
            if (result!=null) {
                return result;
            }
        }
        return null;
    }

    private final Map<Long, LongHolder> bannedSince = new HashMap<>();

    public static List<Long> getIdsForSearch(List<WorkbookParamsYaml.TaskVertex> vertices, int page, int pageSize) {
        final int fromIndex = page * pageSize;
        if (vertices.size()== fromIndex) {
            return List.of();
        }
        int toIndex = fromIndex + (vertices.size()-pageSize>=fromIndex ? pageSize : vertices.size() - fromIndex);
        return vertices.subList(fromIndex, toIndex).stream()
                .map(v -> v.taskId)
                .collect(Collectors.toList());
    }

    private LaunchpadCommParamsYaml.AssignedTask findUnassignedTaskAndAssign(Long workbookId, Station station, StationStatusYaml ss, boolean isAcceptOnlySigned) {

        LongHolder longHolder = bannedSince.computeIfAbsent(station.getId(), o -> new LongHolder(0));
        if (longHolder.value!=0 && System.currentTimeMillis() - longHolder.value < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }
        ExecContextImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
        if (workbook==null) {
            return null;
        }
        final List<WorkbookParamsYaml.TaskVertex> vertices = workbookGraphTopLevelService.findAllForAssigning(workbook);
        final StationStatusYaml stationStatus = StationStatusYamlUtils.BASE_YAML_UTILS.to(station.status);

        int page = 0;
        Task resultTask = null;
        String resultTaskParams = null;
        List<Task> tasks;
        while ((tasks = getAllByStationIdIsNullAndWorkbookIdAndIdIn(workbookId, vertices, page++)).size()>0) {
            for (Task task : tasks) {
                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#705.190 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    taskPersistencer.finishTaskAsBrokenOrError(task.getId(), EnumsApi.TaskExecState.BROKEN);
                    continue;
                }
                catch (Exception e) {
                    throw new RuntimeException("#705.200 Error", e);
                }

                if (taskParamYaml.taskYaml.snippet.sourcing== EnumsApi.SnippetSourcing.git &&
                        stationStatus.gitStatusInfo.status!= Enums.GitStatus.installed) {
                    log.warn("#705.210 Can't assign task #{} to station #{} because this station doesn't correctly installed git, git status info: {}",
                            station.getId(), task.getId(), stationStatus.gitStatusInfo
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (taskParamYaml.taskYaml.snippet.env!=null) {
                    String interpreter = stationStatus.env.getEnvs().get(taskParamYaml.taskYaml.snippet.env);
                    if (interpreter == null) {
                        log.warn("#705.213 Can't assign task #{} to station #{} because this station doesn't have defined interpreter for snippet's env {}",
                                station.getId(), task.getId(), taskParamYaml.taskYaml.snippet.env
                        );
                        longHolder.value = System.currentTimeMillis();
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = getSupportedOS(taskParamYaml);
                if (stationStatus.os!=null && !supportedOS.isEmpty() && !supportedOS.contains(stationStatus.os)) {
                    log.info("#705.217 Can't assign task #{} to station #{}, " +
                                    "because this station doesn't support required OS version. station: {}, snippet: {}",
                            station.getId(), task.getId(), stationStatus.os, supportedOS
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (!taskParamYaml.taskYaml.snippet.info.isSigned()) {
                        log.warn("#705.220 Snippet with code {} wasn't signed", taskParamYaml.taskYaml.snippet.getCode());
                        continue;
                    }
                }
                resultTask = task;
                try {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(resultTask.getParams());
                    resultTaskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, ss.taskParamsVersion);
                } catch (DowngradeNotSupportedException e) {
                    log.warn("Task #{} can't be assigned to station #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            resultTask.getId(), station.id, ss.taskParamsVersion);
                    resultTask = null;
                }
                if (resultTask!=null) {
                    break;
                }
            }
            if (resultTask!=null) {
                break;
            }
        }
        if (resultTask==null) {
            return null;
        }

        if (resultTaskParams==null) {
            throw new IllegalStateException("(resultTaskParams==null)");
        }

        // normal way of operation
        longHolder.value = 0;

        LaunchpadCommParamsYaml.AssignedTask assignedTask = new LaunchpadCommParamsYaml.AssignedTask();
        assignedTask.setTaskId(resultTask.getId());
        assignedTask.setWorkbookId(workbookId);
        assignedTask.setParams(resultTaskParams);

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setStationId(station.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);

        taskRepository.save((TaskImpl)resultTask);
        workbookGraphTopLevelService.updateTaskExecStateByWorkbookId(workbookId, resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value);
        launchpadEventService.publishTaskEvent(EnumsApi.LaunchpadEventType.TASK_ASSIGNED, station.getId(), resultTask.getId(), workbookId);

        return assignedTask;
    }

    private List<EnumsApi.OS> getSupportedOS(TaskParamsYaml taskParamYaml) {
        if (taskParamYaml.taskYaml.snippet!=null) {
            return SnippetCoreUtils.getSupportedOS(taskParamYaml.taskYaml.snippet.metas);
        }
        return List.of();
    }

    private List<Task> getAllByStationIdIsNullAndWorkbookIdAndIdIn(Long workbookId, List<WorkbookParamsYaml.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(workbookId, idsForSearch);
    }

    public List<Long> storeAllConsoleResults(List<StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {

        List<Long> ids = new ArrayList<>();
        for (StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            taskPersistencer.storeExecResult(result, t -> {
                if (t!=null) {
                    workbookGraphTopLevelService.updateTaskExecStateByWorkbookId(t.getWorkbookId(), t.getId(), t.getExecState());
                }
            });
        }
        return ids;
    }

    public SourceCodeApiData.TaskProducingResultComplex produceTasks(boolean isPersist, SourceCodeImpl sourceCode, Long execContextId) {

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        long mill = System.currentTimeMillis();

        ExecContextImpl workbook = workbookCache.findById(execContextId);
        if (workbook == null) {
            log.error("#701.175 Can't find execContext #{}", execContextId);
            return new SourceCodeApiData.TaskProducingResultComplex(EnumsApi.SourceCodeValidateStatus.WORKBOOK_NOT_FOUND_ERROR);
        }

        WorkbookParamsYaml resourceParams = workbook.getWorkbookParamsYaml();
        List<SimpleVariableAndStorageUrl> initialInputResourceCodes = variableService.getIdInVariables(resourceParams.getAllPoolCodes());
        log.info("#701.180 Resources was acquired for " + (System.currentTimeMillis() - mill) +" ms" );

        SourceCodeService.ResourcePools pools = new SourceCodeService.ResourcePools(initialInputResourceCodes);
        if (pools.status!= EnumsApi.SourceCodeProducingStatus.OK) {
            return new SourceCodeApiData.TaskProducingResultComplex(pools.status);
        }

        if (resourceParams.workbookYaml.preservePoolNames) {
            final Map<String, List<String>> collectedInputs = new HashMap<>();
            try {
                pools.collectedInputs.forEach( (key, value) -> {
                    String newKey = resourceParams.workbookYaml.poolCodes.entrySet().stream()
                            .filter(entry -> entry.getValue().contains(key))
                            .findFirst()
                            .map(Map.Entry::getKey)
                            .orElseThrow(()-> {
                                log.error("#701.190 Can't find key for pool code {}", key );
                                throw new BreakFromForEachException();
                            });
                    collectedInputs.put(newKey, value);
                });
            } catch (BreakFromForEachException e) {
                return new SourceCodeApiData.TaskProducingResultComplex(EnumsApi.SourceCodeProducingStatus.ERROR);
            }

            pools.collectedInputs.clear();
            pools.collectedInputs.putAll(collectedInputs);
        }

        Monitoring.log("##025", Enums.Monitor.MEMORY);
        SourceCodeParamsYaml sourceCodeParams = sourceCode.getSourceCodeParamsYaml();

        int idx = Consts.PROCESS_ORDER_START_VALUE;
        List<Long> parentTaskIds = new ArrayList<>();
        int numberOfTasks=0;
        String contextId = "" + idsRepository.save(new Ids()).id;

        for (SourceCodeParamsYaml.Process process : sourceCodeParams.source.getProcesses()) {
            Monitoring.log("##026", Enums.Monitor.MEMORY);
            SourceCodeService.ProduceTaskResult produceTaskResult = taskProducingService.produceTasksForProcess(isPersist, sourceCode.getId(), contextId, sourceCodeParams, execContextId, process, pools, parentTaskIds);
            Monitoring.log("##027", Enums.Monitor.MEMORY);
            parentTaskIds.clear();
            parentTaskIds.addAll(produceTaskResult.taskIds);

            numberOfTasks += produceTaskResult.numberOfTasks;
            if (produceTaskResult.status != EnumsApi.SourceCodeProducingStatus.OK) {
                return new SourceCodeApiData.TaskProducingResultComplex(produceTaskResult.status);
            }
            Monitoring.log("##030", Enums.Monitor.MEMORY);

            // this part of code replaces the code below
            for (SourceCodeParamsYaml.Variable variable : process.output) {
                pools.add(variable.name, produceTaskResult.outputResourceCodes);
                for (String outputResourceCode : produceTaskResult.outputResourceCodes) {
                    pools.inputStorageUrls.put(outputResourceCode, variable);
                }
            }
/*
            if (process.outputParams.storageType!=null) {
                pools.add(process.outputParams.storageType, produceTaskResult.outputResourceCodes);
                for (String outputResourceCode : produceTaskResult.outputResourceCodes) {
                    pools.inputStorageUrls.put(outputResourceCode, process.outputParams);
                }
            }
*/
            Monitoring.log("##031", Enums.Monitor.MEMORY);
        }

        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        if (isPersist) {
            workbookFSM.toProduced(execContextId);
        }
        result.execContext = workbookCache.findById(execContextId);
        result.sourceCodeYaml = sourceCodeParams.source;
        result.numberOfTasks = numberOfTasks;
        result.sourceCodeValidateStatus = EnumsApi.SourceCodeValidateStatus.OK;
        result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.OK;

        return result;
    }

    public SourceCodeApiData.ExecContextResult createWorkbookInternal(@NonNull SourceCodeImpl sourceCode, String variable) {
        WorkbookParamsYaml.WorkbookYaml wrc = SourceCodeUtils.asWorkbookParamsYaml(variable);
        SourceCodeApiData.TaskProducingResultComplex producingResult = createWorkbook(sourceCode.getId(), wrc);
        if (producingResult.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            return new SourceCodeApiData.ExecContextResult("#560.072 Error creating execContext: " + producingResult.sourceCodeProducingStatus);
        }

        SourceCodeApiData.TaskProducingResultComplex countTasks = produceTasks(false, sourceCode, producingResult.execContext.getId());
        if (countTasks.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            changeValidStatus(producingResult.execContext.getId(), false);
            return new SourceCodeApiData.ExecContextResult("#560.077 sourceCode producing was failed, status: " + countTasks.sourceCodeProducingStatus);
        }

        if (globals.maxTasksPerWorkbook < countTasks.numberOfTasks) {
            changeValidStatus(producingResult.execContext.getId(), false);
            return new SourceCodeApiData.ExecContextResult("#560.081 number of tasks for this execContext exceeded the allowed maximum number. ExecContext was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerWorkbook + ", tasks in this execContext:  " + countTasks.numberOfTasks);
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode);
        result.execContext = producingResult.execContext;

        changeValidStatus(producingResult.execContext.getId(), true);

        return result;
    }

    public void deleteWorkbook(Long execContextId, Long companyUniqueId) {
//        experimentService.resetExperimentByWorkbookId(execContextId);
        applicationEventPublisher.publishEvent(new LaunchpadInternalEvent.ExperimentResetEvent(execContextId));
        variableService.deleteByWorkbookId(execContextId);
        ExecContext execContext = workbookCache.findById(execContextId);
        if (execContext != null) {
            // unlock sourceCode if this is the last execContext in the sourceCode
            List<Long> ids = workbookRepository.findIdsBysourceCodeId(execContext.getSourceCodeId());
            if (ids.size()==1) {
                if (ids.get(0).equals(execContextId)) {
                    if (execContext.getSourceCodeId() != null) {
//                        setLockedTo(execContext.getSourceCodeId(), companyUniqueId, false);
                        applicationEventPublisher.publishEvent(new LaunchpadInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, false));
                    }
                }
                else {
                    log.warn("#701.300 unexpected state, execContextId: {}, ids: {}, ", execContextId, ids);
                }
            }
            else if (ids.isEmpty()) {
                log.warn("#701.310 unexpected state, execContextId: {}, ids is empty", execContextId);
            }
            workbookCache.deleteById(execContextId);
        }
    }



}
