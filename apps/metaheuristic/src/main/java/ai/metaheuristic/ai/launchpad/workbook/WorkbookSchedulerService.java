/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 7/16/2019
 * Time: 12:23 AM
 */
@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class WorkbookSchedulerService {

    private final WorkbookGraphService workbookGraphService;
    private final WorkbookCache workbookCache;
    private final WorkbookService workbookService;
    private final WorkbookRepository workbookRepository;
    private final TaskRepository taskRepository;
    private final AtlasService atlasService;

    public void updateWorkbookStatuses(boolean needReconciliation) {
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.STARTED.code);
        for (WorkbookImpl workbook : workbooks) {
            updateWorkbookStatus(workbook, needReconciliation);
        }

        List<Long> workbooksIds = workbookRepository.findIdsByExecState(EnumsApi.WorkbookExecState.EXPORTING_TO_ATLAS.code);
        for (Long workbookId : workbooksIds) {
            log.info("Start exporting workbook #{} to atlas", workbookId);
            OperationStatusRest status = atlasService.storeExperimentToAtlas(workbookId);
            if (status.status!= EnumsApi.OperationStatus.OK) {
                log.error("Error exporting experiment to atlas, workbookID #{}\n{}", workbookId, status.getErrorMessagesAsStr());
            }
            log.info("Exporting of workbook #{} was finished", workbookId);
        }
    }

    /**
     *
     * @param workbook WorkbookImpl much be real object from db, not from cache
     * @param needReconciliation
     * @return WorkbookImpl updated workbook
     */
    public void updateWorkbookStatus(WorkbookImpl workbook, boolean needReconciliation) {

        long countUnfinishedTasks = workbookGraphService.getCountUnfinishedTasks(workbook);
        if (countUnfinishedTasks==0) {
            // workaround for situation when states in graph and db are different
            reconsileStates(workbook);
            countUnfinishedTasks = workbookGraphService.getCountUnfinishedTasks(workbook);
            if (countUnfinishedTasks==0) {
                log.info("Workbook #{} was finished", workbook.getId());
                workbookService.toFinished(workbook.getId());
            }
        }
        else {
            if (needReconciliation) {
                reconsileStates(workbook);
            }
        }
    }

    public void reconsileStates(WorkbookImpl workbook) {
        List<Object[]> list = taskRepository.findAllExecStateByWorkbookId(workbook.getId());
        final Long workbookId = workbook.id;

        // Reconcile states in db and in graph
        List<WorkbookParamsYaml.TaskVertex> vertices = workbookGraphService.findAll(workbook);
        Map<Long, Integer> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            Long taskId = (Long) o[0];
            Integer execState = (Integer) o[1];
            states.put(taskId, execState);
        }
        ConcurrentHashMap<Long, Integer> taskStates = new ConcurrentHashMap<>();
        AtomicBoolean isNullState = new AtomicBoolean(false);
        vertices.stream().parallel().forEach(tv -> {
            Integer state = states.get(tv.taskId);
            if (state==null) {
                isNullState.set(true);
            }
            else if (tv.execState.value!=state) {
                log.info("#705.054 Found different states for task #"+tv.taskId+", " +
                        "db: "+ EnumsApi.TaskExecState.from(state)+", " +
                        "graph: "+tv.execState);
                taskStates.put(tv.taskId, state);
            }
        });

        if (isNullState.get()) {
            log.info("#705.052 Found non-created task, graph consistency is failed");
            workbookService.toError(workbook.getId());
        }
        else {
            workbookService.updateTaskExecStates(workbook, taskStates);
        }

        // fix actual state of tasks (can be as a result of OptimisticLockingException)
        // fix IN_PROCESSING state
        // find and reset all hanging up tasks
        states.entrySet().stream()
                .filter(e-> EnumsApi.TaskExecState.IN_PROGRESS.value==e.getValue())
                .forEach(e->{
                    Long taskId = e.getKey();
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                        // did this task hang up at station?
                        if (task.assignedOn!=null && tpy.taskYaml.timeoutBeforeTerminate != null && tpy.taskYaml.timeoutBeforeTerminate!=0L) {
                            final long multiplyBy2 = tpy.taskYaml.timeoutBeforeTerminate * 2 * 1000;
                            final long oneHourToMills = TimeUnit.HOURS.toMillis(1);
                            long timeout = multiplyBy2 > oneHourToMills ? oneHourToMills : multiplyBy2;
                            if ((System.currentTimeMillis() - task.assignedOn) > timeout) {
                                log.info("Reset task #{}, multiplyBy2: {}, timeout: {}", task.id, multiplyBy2, timeout);
                                workbookService.resetTask(task.id);
                            }
                        }
                        else if (task.resultReceived && task.isCompleted) {
                            workbookService.updateTaskExecStateByWorkbookId(workbookId, task.id, EnumsApi.TaskExecState.OK.value);
                        }
                    }
                });
    }
}
