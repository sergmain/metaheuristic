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
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.SnippetCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
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
        final WorkbookImpl workbook = workbookCache.findById(workbookId);
        if (workbook==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.003 Can't find workbook with id #"+workbookId);
        }
        List<WorkbookParamsYaml.TaskVertex> vertices = workbookGraphTopLevelService.findAllBroken(workbook);
        if (vertices==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.005 Can't find workbook with id #"+workbookId);
        }
        for (WorkbookParamsYaml.TaskVertex vertex : vertices) {
            resetTask(vertex.taskId);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest workbookTargetExecState(Long workbookId, EnumsApi.WorkbookExecState execState) {
        SourceCodeApiData.WorkbookResult result = getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        final WorkbookImpl workbook = (WorkbookImpl) result.workbook;
        final SourceCode sourceCode = result.sourceCode;
        if (sourceCode ==null || workbook ==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#701.110 Error: (result.sourceCode==null || result.workbook==null)");
        }

        if (workbook.execState!=execState.code) {
            workbookFSM.toState(workbook.id, execState);
            applicationEventPublisher.publishEvent(new LaunchpadInternalEvent.PlanLockingEvent(sourceCode.getId(), sourceCode.getCompanyId(), true));
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
                        "#705.020 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
            }

            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.040 Can't re-run task #" + taskId + ", see log for more information");
            }
            taskPersistencer.updateTasksStateInDb(withTaskList);

            WorkbookImpl workbook = workbookCache.findById(task.workbookId);
            if (workbook.execState != EnumsApi.WorkbookExecState.STARTED.code) {
                workbookFSM.toState(workbook.id, EnumsApi.WorkbookExecState.STARTED);
            }
        }

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public long getCountUnfinishedTasks(WorkbookImpl workbook) {
        return getCountUnfinishedTasks(workbook.id);
    }

    public Long getCountUnfinishedTasks(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbookGraphService::getCountUnfinishedTasks);
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllVertices(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbookGraphService::findAll);
    }

    public SourceCodeApiData.TaskProducingResultComplex createWorkbook(Long planId, WorkbookParamsYaml.WorkbookYaml workbookYaml) {
        return createWorkbook(planId, workbookYaml, true);
    }

    public SourceCodeApiData.TaskProducingResultComplex createWorkbook(Long planId, WorkbookParamsYaml.WorkbookYaml workbookYaml, boolean checkResources) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();

        WorkbookImpl wb = new WorkbookImpl();
        wb.setPlanId(planId);
        wb.setCreatedOn(System.currentTimeMillis());
        wb.setExecState(EnumsApi.WorkbookExecState.NONE.code);
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

        result.workbook = workbookCache.save(wb);
        result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.OK;

        return result;
    }

    public Void changeValidStatus(Long workbookId, boolean status) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            workbook.setValid(status);
            workbookCache.save(workbook);
            return null;
        });
    }

    public EnumsApi.SourceCodeProducingStatus toProducing(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState == EnumsApi.WorkbookExecState.PRODUCING.code) {
                return EnumsApi.SourceCodeProducingStatus.OK;
            }
            workbook.setExecState(EnumsApi.WorkbookExecState.PRODUCING.code);
            workbookCache.save(workbook);
            return EnumsApi.SourceCodeProducingStatus.OK;
        });
    }

    public SourceCodeApiData.WorkbookResult getWorkbookExtended(Long workbookId) {
        if (workbookId==null) {
            return new SourceCodeApiData.WorkbookResult("#705.090 workbookId is null");
        }
        WorkbookImpl workbook = workbookCache.findById(workbookId);
        if (workbook == null) {
            return new SourceCodeApiData.WorkbookResult("#705.100 workbook wasn't found, workbookId: " + workbookId);
        }
        SourceCodeImpl plan = sourceCodeCache.findById(workbook.getPlanId());
        if (plan == null) {
            return new SourceCodeApiData.WorkbookResult("#705.110 sourceCode wasn't found, planId: " + workbook.getPlanId());
        }

        if (!plan.getId().equals(workbook.getPlanId())) {
            changeValidStatus(workbookId, false);
            return new SourceCodeApiData.WorkbookResult("#705.120 planId doesn't match to workbook.planId, planId: " + workbook.getPlanId()+", workbook.planId: " + workbook.getPlanId());
        }

        //noinspection UnnecessaryLocalVariable
        SourceCodeApiData.WorkbookResult result = new SourceCodeApiData.WorkbookResult(plan, workbook);
        return result;
    }

    public SourceCodeApiData.WorkbooksResult getWorkbooksOrderByCreatedOnDescResult(
            @PathVariable Long planId, @PageableDefault(size = 5) Pageable pageable, LaunchpadContext context) {
        pageable = ControllerUtils.fixPageSize(globals.workbookRowsLimit, pageable);
        SourceCodeApiData.WorkbooksResult result = new SourceCodeApiData.WorkbooksResult();
        result.instances = workbookRepository.findByPlanIdOrderByCreatedOnDesc(pageable, planId);
        result.currentPlanId = planId;

        for (Workbook workbook : result.instances) {
            WorkbookParamsYaml wpy = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(workbook.getParams());
            wpy.graph = null;
            workbook.setParams( WorkbookParamsYamlUtils.BASE_YAML_UTILS.toString(wpy) );
            SourceCode sourceCode = sourceCodeCache.findById(workbook.getPlanId());
            if (sourceCode ==null) {
                log.warn("#705.130 Found workbook with wrong planId. planId: {}", workbook.getPlanId());
                continue;
            }
            result.plans.put(workbook.getId(), sourceCode);
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
        // find task in specific workbook ( i.e. workbookId==null)?
        if (workbookId==null) {
            workbookIds = workbookRepository.findByExecStateOrderByCreatedOnAsc(EnumsApi.WorkbookExecState.STARTED.code);
        }
        else {
            WorkbookImpl workbook = workbookCache.findById(workbookId);
            if (workbook==null) {
                log.warn("#705.170 Workbook wasn't found for id: {}", workbookId);
                return null;
            }
            if (workbook.getExecState()!= EnumsApi.WorkbookExecState.STARTED.code) {
                log.warn("#705.180 Workbook wasn't started. Current exec state: {}", EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
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
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
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

    public SourceCodeApiData.TaskProducingResultComplex produceTasks(boolean isPersist, SourceCodeImpl plan, Long workbookId) {

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        long mill = System.currentTimeMillis();

        WorkbookImpl workbook = workbookCache.findById(workbookId);
        if (workbook == null) {
            log.error("#701.175 Can't find workbook #{}", workbookId);
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
        SourceCodeParamsYaml planParams = plan.getPlanParamsYaml();

        int idx = Consts.PROCESS_ORDER_START_VALUE;
        List<Long> parentTaskIds = new ArrayList<>();
        int numberOfTasks=0;
        String contextId = "" + idsRepository.save(new Ids()).id;

        for (SourceCodeParamsYaml.Process process : planParams.source.getProcesses()) {
            Monitoring.log("##026", Enums.Monitor.MEMORY);
            SourceCodeService.ProduceTaskResult produceTaskResult = taskProducingService.produceTasksForProcess(isPersist, plan.getId(), contextId, planParams, workbookId, process, pools, parentTaskIds);
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
            workbookFSM.toProduced(workbookId);
        }
        result.workbook = workbookCache.findById(workbookId);
        result.sourceCodeYaml = planParams.source;
        result.numberOfTasks = numberOfTasks;
        result.sourceCodeValidateStatus = EnumsApi.SourceCodeValidateStatus.OK;
        result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.OK;

        return result;
    }

    public SourceCodeApiData.WorkbookResult createWorkbookInternal(@NonNull SourceCodeImpl plan, String variable) {
        WorkbookParamsYaml.WorkbookYaml wrc = SourceCodeUtils.asWorkbookParamsYaml(variable);
        SourceCodeApiData.TaskProducingResultComplex producingResult = createWorkbook(plan.getId(), wrc);
        if (producingResult.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            return new SourceCodeApiData.WorkbookResult("#560.072 Error creating workbook: " + producingResult.sourceCodeProducingStatus);
        }

        SourceCodeApiData.TaskProducingResultComplex countTasks = produceTasks(false, plan, producingResult.workbook.getId());
        if (countTasks.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            changeValidStatus(producingResult.workbook.getId(), false);
            return new SourceCodeApiData.WorkbookResult("#560.077 sourceCode producing was failed, status: " + countTasks.sourceCodeProducingStatus);
        }

        if (globals.maxTasksPerWorkbook < countTasks.numberOfTasks) {
            changeValidStatus(producingResult.workbook.getId(), false);
            return new SourceCodeApiData.WorkbookResult("#560.081 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerWorkbook + ", tasks in this workbook:  " + countTasks.numberOfTasks);
        }

        SourceCodeApiData.WorkbookResult result = new SourceCodeApiData.WorkbookResult(plan);
        result.workbook = producingResult.workbook;

        changeValidStatus(producingResult.workbook.getId(), true);

        return result;
    }

    public void deleteWorkbook(Long workbookId, Long companyUniqueId) {
//        experimentService.resetExperimentByWorkbookId(workbookId);
        applicationEventPublisher.publishEvent(new LaunchpadInternalEvent.ExperimentResetEvent(workbookId));
        variableService.deleteByWorkbookId(workbookId);
        Workbook workbook = workbookCache.findById(workbookId);
        if (workbook != null) {
            // unlock sourceCode if this is the last workbook in the sourceCode
            List<Long> ids = workbookRepository.findIdsByPlanId(workbook.getPlanId());
            if (ids.size()==1) {
                if (ids.get(0).equals(workbookId)) {
                    if (workbook.getPlanId() != null) {
//                        setLockedTo(workbook.getPlanId(), companyUniqueId, false);
                        applicationEventPublisher.publishEvent(new LaunchpadInternalEvent.PlanLockingEvent(workbook.getPlanId(), companyUniqueId, false));
                    }
                }
                else {
                    log.warn("#701.300 unexpected state, workbookId: {}, ids: {}, ", workbookId, ids);
                }
            }
            else if (ids.isEmpty()) {
                log.warn("#701.310 unexpected state, workbookId: {}, ids is empty", workbookId);
            }
            workbookCache.deleteById(workbookId);
        }
    }



}
