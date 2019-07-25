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

package ai.metaheuristic.ai.launchpad.batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.NeedRetryAfterCacheCleanException;
import ai.metaheuristic.ai.launchpad.batch.beans.Batch;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchParams;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchStatus;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.yaml.pilot.BatchParamsUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Serge
 * Date: 6/1/2019
 * Time: 4:18 PM
 */
@SuppressWarnings({"UnusedReturnValue"})
@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class BatchService {

    private static final String PLAN_NOT_FOUND = "Plan wasn't found";
    private static final String IP_HOST = "IP: %s, host: %s";

    private final Globals globals;
    private final PlanCache planCache;
    private final BatchCache batchCache;
    private final BatchRepository batchRepository;
    private final WorkbookRepository workbookRepository;
    private final WorkbookCache workbookCache;
    private final WorkbookService workbookService;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final BinaryDataService binaryDataService;
    private final TaskRepository taskRepository;
    private final StationCache stationCache;

    private static final ConcurrentHashMap<Long, Object> batchMap = new ConcurrentHashMap<>(100, 0.75f, 10);

    public Batch changeStateToPreparing(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                Batch b = batchCache.findById(batchId);
                if (b == null) {
                    log.warn("#990.010 batch wasn't found {}", batchId);
                    return null;
                }
                if (b.execState != Enums.BatchExecState.Unknown.code && b.execState != Enums.BatchExecState.Stored.code &&
                        b.execState != Enums.BatchExecState.Preparing.code) {
                    throw new IllegalStateException("\"#990.020 Can't change state to Preparing, " +
                            "current state: " + Enums.BatchExecState.toState(b.execState));
                }
                if (b.execState == Enums.BatchExecState.Preparing.code) {
                    return b;
                }
                b.execState = Enums.BatchExecState.Preparing.code;
                return batchCache.save(b);
            }
            finally {
                batchMap.remove(batchId);
            }
        }
    }

    public Batch changeStateToProcessing(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                Batch b = batchCache.findById(batchId);
                if (b == null) {
                    log.warn("#990.030 batch wasn't found {}", batchId);
                    return null;
                }
                if (b.execState != Enums.BatchExecState.Preparing.code && b.execState != Enums.BatchExecState.Processing.code) {
                    throw new IllegalStateException("\"#990.040 Can't change state to Finished, " +
                            "current state: " + Enums.BatchExecState.toState(b.execState));
                }
                if (b.execState == Enums.BatchExecState.Processing.code) {
                    return b;
                }
                b.execState = Enums.BatchExecState.Processing.code;
                return batchCache.save(b);
            }
            finally {
                batchMap.remove(batchId);
            }
        }
    }

    public Batch changeStateToFinished(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                updateStatus(batchId, false);
                Batch b = batchCache.findById(batchId);
                if (b == null) {
                    log.warn("#990.050 batch wasn't found {}", batchId);
                    return null;
                }
                if (b.execState != Enums.BatchExecState.Processing.code
                        && b.execState != Enums.BatchExecState.Finished.code
                        && b.execState != Enums.BatchExecState.Error.code) {
                    throw new IllegalStateException("#990.060 Can't change state to Finished, " +
                            "current state: " + Enums.BatchExecState.toState(b.execState));
                }
                if (b.execState == Enums.BatchExecState.Finished.code) {
                    return b;
                }
                if (b.execState == Enums.BatchExecState.Error.code) {
                    return b;
                }
                b.execState = Enums.BatchExecState.Finished.code;
                return batchCache.save(b);
            }
            finally {
                batchMap.remove(batchId);
            }
        }
    }

    @SuppressWarnings("unused")
    public Batch changeStateToError(Long batchId, String error) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                Batch b = batchCache.findById(batchId);
                if (b == null) {
                    log.warn("#990.070 batch not found in db, batchId: #{}", batchId);
                    return null;
                }
                b.setExecState(Enums.BatchExecState.Error.code);

                BatchParams batchParams = BatchParamsUtils.to(b.params);
                if (batchParams == null) {
                    batchParams = new BatchParams();
                }
                batchParams.batchStatus = new BatchStatus();
                batchParams.batchStatus.add(error);
                batchParams.batchStatus.init();

                b.params = BatchParamsUtils.toString(batchParams);

                b = batchCache.save(b);
                return b;
            }
            finally {
                batchMap.remove(batchId);
            }
        }
    }

    private static String getMainDocumentPoolCode(WorkbookImpl workbook) {
        WorkbookParamsYaml resourceParams = workbook.getWorkbookParamsYaml();
        List<String> codes = resourceParams.workbookYaml.poolCodes.get(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH);
        if (codes.isEmpty()) {
            throw new IllegalStateException("#990.080 Main document section is missed. inputResourceParams:\n" + workbook.getParams());
        }
        if (codes.size()>1) {
            throw new IllegalStateException("#990.090 Main document section contains more than one main document. inputResourceParams:\n" + workbook.getParams());
        }
        return codes.get(0);
    }

    List<BatchData.ProcessResourceItem> getBatches(Page<Long> batchIds) {
        List<BatchData.ProcessResourceItem> items = new ArrayList<>();
        for (Long batchId : batchIds) {
            Batch batch;
            final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (obj) {
                try {
                    batch = batchCache.findById(batchId);
                    if (batch.execState != Enums.BatchExecState.Finished.code &&
                            batch.execState != Enums.BatchExecState.Error.code &&
                            batch.execState != Enums.BatchExecState.Archived.code) {
                        Boolean isFinished = null;
                        for (Workbook fi : workbookRepository.findWorkbookByBatchId(batch.id)) {
                            isFinished = Boolean.TRUE;
                            if (fi.getExecState() != EnumsApi.WorkbookExecState.ERROR.code &&
                                    fi.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                                isFinished = Boolean.FALSE;
                                break;
                            }
                        }
                        if (Boolean.TRUE.equals(isFinished)) {
                            batch = changeStateToFinished(batch.id);
                        }
                    }
                } finally {
                    batchMap.remove(batchId);
                }
            }
            String planCode = PLAN_NOT_FOUND;
            Plan plan = planCache.findById(batch.getPlanId());
            boolean ok = true;
            if (plan!=null) {
                planCode = plan.getCode();
            }
            else {
                if (batch.execState!=Enums.BatchExecState.Preparing.code) {
                    ok = false;
                }
            }
            String execStateStr = Enums.BatchExecState.toState(batch.execState).toString();
            items.add( new BatchData.ProcessResourceItem(batch, planCode, execStateStr, batch.execState, ok));
        }
        return items;
    }

    public BatchStatus updateStatus(Long batchId, boolean fullConsole) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return updateStatusInternal(batchId, fullConsole);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                log.warn("#990.097 NeedRetryAfterCacheCleanException was caught");
            }
            try {
                return updateStatusInternal(batchId, fullConsole);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                final BatchStatus status = new BatchStatus();
                status.add("#990.100 Can't update batch status, Try later");
                return status;
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private BatchStatus updateStatusInternal(Long batchId, boolean fullConsole)  {
        Batch b=null;
        try {
            b = batchCache.findById(batchId);
            if (b == null) {
                final BatchStatus bs = new BatchStatus();
                bs.add("#990.110, Batch wasn't found, batchId: " + batchId, '\n');
                bs.ok = false;
                return bs;
            }

            if (b.getParams() != null && !b.getParams().isBlank() &&
                    (b.execState == Enums.BatchExecState.Finished.code || b.execState == Enums.BatchExecState.Error.code)) {
                BatchParams batchParams = BatchParamsUtils.to(b.getParams());
                return batchParams.batchStatus;
            }

            BatchStatus batchStatus = prepareStatus(batchId, fullConsole);

            b = batchCache.findById(batchId);
            if (b == null) {
                final BatchStatus bs = new BatchStatus();
                bs.add("#990.113, Batch wasn't found, batchId: " + batchId, '\n');
                bs.ok = false;
                return bs;
            }
            BatchParams batchParams = BatchParamsUtils.to(b.getParams());
            if (batchParams == null) {
                batchParams = new BatchParams();
            }
            batchParams.batchStatus = batchStatus;
            b.params = BatchParamsUtils.toString(batchParams);
            batchCache.save(b);

            return batchStatus;

        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#990.120 Error updating batch, new: {}, curr: {}", b, batchRepository.findById(batchId).orElse(null));
            log.error("#990.121 Error updating batch", e);
            batchCache.evictById(batchId);
            // because this error is somehow related to stationCache, let's invalidate it
            stationCache.clearCache();
            throw new NeedRetryAfterCacheCleanException();
        }
        finally {
            batchMap.remove(batchId);
        }
    }

    @SuppressWarnings("Duplicates")
    private BatchStatus prepareStatus(Long batchId, boolean fullConsole) {
        final BatchStatus bs = new BatchStatus();

        List<Long> ids = batchWorkbookRepository.findWorkbookIdsByBatchId(batchId);
        if (ids.isEmpty()) {
            bs.add("#990.130 Batch is empty, there isn't any task, batchId: " + batchId, '\n');
            bs.ok = true;
            return bs;
        }

        boolean isOk = true;
        for (Long workbookId : ids) {
            WorkbookImpl wb = workbookCache.findById(workbookId);
            if (wb == null) {
                String msg = "#990.140 Batch #" + batchId + " contains broken workbookId - #" + workbookId;
                bs.add(msg, '\n');
                log.warn(msg);
                isOk = false;
                continue;
            }
            String mainDocumentPoolCode = getMainDocumentPoolCode(wb);

            final String fullMainDocument = getMainDocumentFilenameForPoolCode(mainDocumentPoolCode);
            if (fullMainDocument == null) {
                String msg = "#990.150 " + mainDocumentPoolCode + ", Can't determine actual file name of main document, " +
                        "batchId: " + batchId + ", workbookId: " + workbookId;
                log.warn(msg);
                bs.add(msg, '\n');
                isOk = false;
                continue;
            }
            String mainDocument = StrUtils.getName(fullMainDocument) + getActualExtension(wb.getPlanId());

            List<WorkbookParamsYaml.TaskVertex> taskVertices;
            try {
                taskVertices = workbookService.findLeafs(wb);
            } catch (ObjectOptimisticLockingFailureException e) {
                String msg = "#990.167 Can't find tasks for workbookId #" + wb.getId() + ", error: " + e.getMessage();
                log.warn(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            if (taskVertices.isEmpty()) {
                String msg = "#990.170 " + mainDocument + ", Can't find any task for batchId: " + batchId;
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            if (taskVertices.size() > 1) {
                String msg = "#990.180 " + mainDocument + ", Can't download file because there are more than one task " +
                        "at the final state, batchId: " + batchId + ", workbookId: " + wb.getId();
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            final Task task = taskRepository.findById(taskVertices.get(0).taskId).orElse(null);
            if (task==null) {
                String msg = "#990.183 " + mainDocument + ", Can't find task #" + taskVertices.get(0).taskId;
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }

            EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());
            SnippetApiData.SnippetExec snippetExec;
            try {
                snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            } catch (YAMLException e) {
                bs.add("#990.190 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                        ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(),'\n');
                isOk = false;
                continue;
            }
            Station s = null;
            if (task.getStationId()!=null) {
                s = stationCache.findById(task.getStationId());
            }
            final String stationIpAndHost = getStationIpAndHost(s);
            switch (execState) {
                case NONE:
                case IN_PROGRESS:
                    bs.add("#990.200 " + mainDocument + ", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                                    ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                    "taskId: " + task.getId() + ", stationId: " + task.getStationId() +
                                    ", " + stationIpAndHost
                            ,'\n');
                    isOk = true;
                    continue;
                case ERROR:
                    bs.add(getStatusForError(batchId, wb, mainDocument, task, snippetExec, stationIpAndHost));
                    isOk = true;
                    continue;
                case OK:
                    if (fullConsole) {
                        bs.add("#990.220 " + mainDocument + ", Task completed without any error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + "\n" +
                                "stationId: " + task.getStationId() + "\n" +
                                stationIpAndHost + "\n" +
                                "isOk: " + snippetExec.exec.isOk + "\n" +
                                "exitCode: " + snippetExec.exec.exitCode + "\n" +
                                "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>") + "\n\n");
                    }
                    isOk = true;
                    // !!! Don't change to continue;
                    break;
            }

            if (wb.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                bs.add("#990.230 " + mainDocument + ", Task hasn't completed yet, " +
                                "batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + ", " +
                                "stationId: " + task.getStationId() + ", " + stationIpAndHost
                        ,'\n');
                isOk = true;
                continue;
            }

            if (!fullConsole) {
                String msg = "#990.240 status - Ok, doc: " + mainDocument + ", batchId: " + batchId + ", workbookId: " + workbookId +
                        ", taskId: " + task.getId() + ", stationId: " + task.getStationId() + ", " + stationIpAndHost;
                bs.add(msg,'\n');
                isOk = true;
            }
        }

        bs.ok = isOk;
        bs.init();

        return bs;
    }

    private String getStatusForError(Long batchId, Workbook wb, String mainDocument, Task task, SnippetApiData.SnippetExec snippetExec, String stationIpAndHost) {

        final String header =
                "#990.210 " + mainDocument + ", Task was completed with an error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                "taskId: " + task.getId() + "\n" +
                "stationId: " + task.getStationId() + "\n" +
                stationIpAndHost + "\n\n";
        StringBuilder sb = new StringBuilder(header);
        if (snippetExec.preExecs!=null && !snippetExec.preExecs.isEmpty()) {
            sb.append("Pre snippets:\n");
            for (SnippetApiData.SnippetExecResult preExec : snippetExec.preExecs) {
                sb.append(execResultAsStr(preExec));
            }
        }
        if (StringUtils.isNotBlank(snippetExec.exec.snippetCode)) {
            sb.append("Main snippet:\n");
            sb.append(execResultAsStr(snippetExec.exec));
        }

        if (snippetExec.postExecs!=null && !snippetExec.postExecs.isEmpty()) {
            sb.append("Post snippets:\n");
            for (SnippetApiData.SnippetExecResult postExec : snippetExec.postExecs) {
                sb.append(execResultAsStr(postExec));
            }
        }

        return sb.toString();
    }

    private String execResultAsStr(SnippetApiData.SnippetExecResult execResult) {
        return
                "snippet: " + execResult.snippetCode + "\n" +
                "isOk: " + execResult.isOk + "\n" +
                "exitCode: " + execResult.exitCode + "\n" +
                "console:\n" + (StringUtils.isNotBlank(execResult.console) ? execResult.console : "<output to console is blank>") + "\n\n";
    }

    public BatchStatus prepareStatusAndData(Long batchId, File zipDir, boolean fullConsole, boolean storeToDisk) {
        final BatchStatus bs = new BatchStatus();
        if (zipDir == null) {
            bs.add("#990.268 zipDir is null", '\n');
            bs.ok = false;
            return bs;
        }

        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            bs.add("#990.270 Batch #"+batchId+" wasn't found", '\n');
            bs.ok = false;
            return bs;
        }

        List<Long> ids = batchWorkbookRepository.findWorkbookIdsByBatchId(batchId);
        if (ids.isEmpty()) {
            bs.add("#990.250 Batch is empty, there isn't any task, batchId: " + batchId, '\n');
            bs.ok = true;
            return bs;
        }

        boolean isOk = true;
        for (Long workbookId : ids) {
            WorkbookImpl wb = workbookCache.findById(workbookId);
            if (wb == null) {
                String msg = "#990.260 Batch #" + batchId + " contains broken workbookId - #" + workbookId;
                bs.add(msg, '\n');
                log.warn(msg);
                isOk = false;
                continue;
            }
            String mainDocumentPoolCode = getMainDocumentPoolCode(wb);

            final String fullMainDocument = getMainDocumentFilenameForPoolCode(mainDocumentPoolCode);
            if (fullMainDocument == null) {
                String msg = "#990.270 " + mainDocumentPoolCode + ", Can't determine actual file name of main document, " +
                        "batchId: " + batchId + ", workbookId: " + workbookId;
                log.warn(msg);
                bs.add(msg, '\n');
                isOk = false;
                continue;
            }
            String mainDocument = StrUtils.getName(fullMainDocument) + getActualExtension(wb.getPlanId());

            List<WorkbookParamsYaml.TaskVertex> taskVertices;
            taskVertices = workbookService.findLeafs(wb);
            if (taskVertices.isEmpty()) {
                String msg = "#990.290 " + mainDocument + ", Can't find any task for batchId: " + batchId;
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            if (taskVertices.size() > 1) {
                String msg = "#990.300 " + mainDocument + ", Can't download file because there are more than one task " +
                        "at the final state, batchId: " + batchId + ", workbookId: " + wb.getId();
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            final Task task = taskRepository.findById(taskVertices.get(0).taskId).orElse(null);
            if (task==null) {
                String msg = "#990.303 " + mainDocument + ", Can't find task #" + taskVertices.get(0).taskId;
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }

            EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());
            SnippetApiData.SnippetExec snippetExec;
            try {
                snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            } catch (YAMLException e) {
                bs.add("#990.310 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                        ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(),'\n');
                isOk = false;
                continue;
            }
            Station s = null;
            if (task.getStationId()!=null) {
                s = stationCache.findById(task.getStationId());
            }
            final String stationIpAndHost = getStationIpAndHost(s);
            switch (execState) {
                case NONE:
                case IN_PROGRESS:
                    bs.add("#990.320 " + mainDocument + ", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                                    ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                    "taskId: " + task.getId() + ", stationId: " + task.getStationId() +
                                    ", " + stationIpAndHost
                            ,'\n');
                    isOk = true;
                    continue;
                case ERROR:
                    bs.add("#990.330 " + mainDocument + ", Task was completed with an error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                            "taskId: " + task.getId() + "\n" +
                            "stationId: " + task.getStationId() + "\n" +
                            stationIpAndHost + "\n" +
                            "isOk: " + snippetExec.exec.isOk + "\n" +
                            "exitCode: " + snippetExec.exec.exitCode + "\n" +
                            "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>") + "\n\n");
                    isOk = true;
                    continue;
                case OK:
                    if (fullConsole) {
                        bs.add("#990.340 " + mainDocument + ", Task completed without any error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + "\n" +
                                "stationId: " + task.getStationId() + "\n" +
                                stationIpAndHost + "\n" +
                                "isOk: " + snippetExec.exec.isOk + "\n" +
                                "exitCode: " + snippetExec.exec.exitCode + "\n" +
                                "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>") + "\n\n");
                    }
                    isOk = true;
                    // !!! Don't change to continue;
                    break;
            }

            final TaskParamsYaml taskParamYaml;
            try {
                taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            } catch (YAMLException e) {
                bs.add("#990.350 " + mainDocument + ", Task has broken data in params, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                        ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(), '\n');
                isOk = false;
                continue;
            }

            if (wb.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                bs.add("#990.360 " + mainDocument + ", Task hasn't completed yet, " +
                                "batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + ", " +
                                "stationId: " + task.getStationId() + ", " + stationIpAndHost
                        ,'\n');
                isOk = true;
                continue;
            }

            File tempFile;
            try {
                tempFile = File.createTempFile("doc-", ".xml", zipDir);
            } catch (IOException e) {
                String msg = "#990.370 Error create a temp file in "+zipDir.getAbsolutePath();
                log.error(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }

            // all documents are sored in zip folder
            bs.renameTo.put("zip/" + tempFile.getName(), "zip/" + mainDocument);

            if (storeToDisk) {
                try {
                    binaryDataService.storeToFile(taskParamYaml.taskYaml.outputResourceCode, tempFile);
                } catch (BinaryDataNotFoundException e) {
                    String msg = "#990.375 Error store data to temp file, data doesn't exist in db, code " + taskParamYaml.taskYaml.outputResourceCode +
                            ", file: " + tempFile.getPath();
                    log.error(msg);
                    bs.add(msg,'\n');
                    isOk = false;
                    continue;
                }
            }

            if (!fullConsole) {
                String msg = "#990.380 status - Ok, doc: " + mainDocument + ", batchId: " + batchId + ", workbookId: " + workbookId +
                        ", taskId: " + task.getId() + ", stationId: " + task.getStationId() + ", " + stationIpAndHost;
                bs.add(msg,'\n');
                isOk = true;
            }

        }

        bs.ok = isOk;
        bs.init();

        return bs;
    }

    private String getMainDocumentFilenameForPoolCode(String mainDocumentPoolCode) {
        final String filename = binaryDataService.getFilenameByPool1CodeAndType(mainDocumentPoolCode, EnumsApi.BinaryDataType.DATA);
        if (StringUtils.isBlank(filename)) {
            log.error("#990.390 Filename is blank for poolCode: {}, data type: {}", mainDocumentPoolCode, EnumsApi.BinaryDataType.DATA);
            return null;
        }
        return filename;
    }

    private String getActualExtension(Long planId) {
        Plan plan = planCache.findById(planId);
        if (plan == null) {
            return (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                    ? globals.defaultResultFileExtension
                    : ".bin");
        }

        PlanParamsYaml planParams = PlanParamsYamlUtils.BASE_YAML_UTILS.to(plan.getParams());
        Meta meta = planParams.planYaml.getMeta(Consts.RESULT_FILE_EXTENSION);

        return meta != null && StringUtils.isNotBlank(meta.getValue())
                ? meta.getValue()
                :
                (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                        ? globals.defaultResultFileExtension
                        : ".bin");
    }

    private String getStationIpAndHost(Station station) {
        if (station==null) {
            return String.format(IP_HOST, Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO);
        }

        StationStatus status = StationStatusUtils.to(station.status);
        final String ip = StringUtils.isNotBlank(status.ip) ? status.ip : Consts.UNKNOWN_INFO;
        final String host = StringUtils.isNotBlank(status.host) ? status.host : Consts.UNKNOWN_INFO;

        return String.format(IP_HOST, ip, host);
    }

}
