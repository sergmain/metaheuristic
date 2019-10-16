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
import ai.metaheuristic.ai.exceptions.NeedRetryAfterCacheCleanException;
import ai.metaheuristic.ai.launchpad.batch.beans.Batch;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchParams;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchStatus;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadEventService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.yaml.pilot.BatchParamsUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

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
    private final LaunchpadEventService launchpadEventService;

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
                launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_PROCESSING_STARTED, null, null, batchId, null, null );
                batchMap.remove(batchId);
            }
        }
    }

    public Batch changeStateToFinished(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                updateStatus(batchId);
                Batch b = batchCache.findById(batchId);
                if (b == null) {
                    log.warn("#990.050 batch wasn't found {}", batchId);
                    return null;
                }
                if (b.execState != Enums.BatchExecState.Processing.code
                        && b.execState != Enums.BatchExecState.Finished.code
                ) {
                    throw new IllegalStateException("#990.060 Can't change state to Finished, " +
                            "current state: " + Enums.BatchExecState.toState(b.execState));
                }
                if (b.execState == Enums.BatchExecState.Finished.code) {
                    return b;
                }
                b.execState = Enums.BatchExecState.Finished.code;
                return batchCache.save(b);
            }
            finally {
                launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_PROCESSING_FINISHED, null, null, batchId, null, null );
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
                batchParams.batchStatus.getGeneralStatus().add(error);
                batchParams.batchStatus.init();

                b.params = BatchParamsUtils.toString(batchParams);

                b = batchCache.save(b);
                return b;
            }
            finally {
                launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_FINISHED_WITH_ERROR, null, null, batchId, null, null );
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
        List<Object[]> batchInfos = binaryDataService.getFilenamesForBatchIds(batchIds.getContent());
        for (Long batchId : batchIds) {
            Batch batch;
            final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (obj) {
                try {
                    batch = batchCache.findById(batchId);
                    if (batch!=null && batch.execState != Enums.BatchExecState.Finished.code &&
                            batch.execState != Enums.BatchExecState.Error.code &&
                            batch.execState != Enums.BatchExecState.Archived.code) {
                        Boolean isFinished = null;
                        for (Integer execState : workbookRepository.findWorkbookExecStateByBatchId(batch.id)) {
                            isFinished = Boolean.TRUE;
                            if (execState != EnumsApi.WorkbookExecState.ERROR.code && execState != EnumsApi.WorkbookExecState.FINISHED.code) {
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
            if (batch!=null) {
                Plan plan = planCache.findById(batch.getPlanId());
                boolean ok = true;
                if (plan != null) {
                    planCode = plan.getCode();
                } else {
                    if (batch.execState != Enums.BatchExecState.Preparing.code) {
                        ok = false;
                    }
                }
                String execStateStr = Enums.BatchExecState.toState(batch.execState).toString();
                String filename = batchInfos.stream().filter(o->o[0].equals(batchId)).map(o->(String)o[1]).findFirst().orElse("[unknown]");
                items.add(new BatchData.ProcessResourceItem(batch, planCode, execStateStr, batch.execState, ok, filename));
            }
        }
        return items;
    }

    // TODO 2019-10-13 change synchronization to use AtomicInteger
    public BatchStatus updateStatus(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return updateStatusInternal(batchId);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                log.warn("#990.097 NeedRetryAfterCacheCleanException was caught");
            }
            try {
                return updateStatusInternal(batchId);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                final BatchStatus status = new BatchStatus();
                status.getGeneralStatus().add("#990.100 Can't update batch status, Try later");
                return status;
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private BatchStatus updateStatusInternal(Long batchId)  {
        Batch b=null;
        try {
            b = batchCache.findById(batchId);
            if (b == null) {
                final BatchStatus bs = new BatchStatus();
                bs.getGeneralStatus().add("#990.110, Batch wasn't found, batchId: " + batchId, '\n');
                bs.ok = false;
                return bs;
            }

            if (b.getParams() != null && !b.getParams().isBlank() &&
                    (b.execState == Enums.BatchExecState.Finished.code || b.execState == Enums.BatchExecState.Error.code)) {
                BatchParams batchParams = BatchParamsUtils.to(b.getParams());
                return batchParams.batchStatus;
            }

            BatchStatus batchStatus = prepareStatusAndData(batchId, (PrepareZipData prepareZipData, File file) -> true, null);

            b = batchCache.findById(batchId);
            if (b == null) {
                final BatchStatus bs = new BatchStatus();
                bs.getGeneralStatus().add("#990.113, Batch wasn't found, batchId: " + batchId, '\n');
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

    private String getStatusForError(Long batchId, Workbook wb, String mainDocument, Task task, SnippetApiData.SnippetExec snippetExec, String stationIpAndHost) {

        final String header =
                "#990.210 " + mainDocument + ", Task was completed with an error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                "taskId: " + task.getId() + "\n" +
                "stationId: " + task.getStationId() + "\n" +
                stationIpAndHost + "\n\n";
        StringBuilder sb = new StringBuilder(header);
        if (snippetExec.generalExec!=null) {
            sb.append("General execution state:\n");
            sb.append(execResultAsStr(snippetExec.generalExec));
        }
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

    @Data
    @AllArgsConstructor
    public static class PrepareZipData {
        public BatchStatus bs;
        public Task task;
        public File zipDir;
        public String mainDocument;
        public Long batchId;
        public Long workbookId;
    }

    public BatchStatus prepareStatusAndData(Long batchId, BiFunction<PrepareZipData, File, Boolean> prepareZip, File zipDir) {
        final BatchStatus bs = new BatchStatus();

        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            bs.getGeneralStatus().add("#990.270 Batch #"+batchId+" wasn't found", '\n');
            bs.ok = false;
            return bs;
        }

        List<Long> ids = batchWorkbookRepository.findWorkbookIdsByBatchId(batchId);
        if (ids.isEmpty()) {
            bs.getGeneralStatus().add("#990.250 Batch is empty, there isn't any task, batchId: " + batchId, '\n');
            bs.ok = true;
            return bs;
        }

        boolean isOk = true;
        for (Long workbookId : ids) {
            WorkbookImpl wb = workbookCache.findById(workbookId);
            if (wb == null) {
                String msg = "#990.260 Batch #" + batchId + " contains broken workbookId - #" + workbookId;
                bs.getGeneralStatus().add(msg, '\n');
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
                bs.getGeneralStatus().add(msg, '\n');
                isOk = false;
                continue;
            }
            final String mainDocument = StrUtils.getName(fullMainDocument) + getActualExtension(wb.getPlanId());

            List<WorkbookParamsYaml.TaskVertex> taskVertices;
            try {
                taskVertices = workbookService.findLeafs(wb);
            } catch (ObjectOptimisticLockingFailureException e) {
                String msg = "#990.167 Can't find tasks for workbookId #" + wb.getId() + ", error: " + e.getMessage();
                log.warn(msg);
                bs.getGeneralStatus().add(msg,'\n');
                isOk = false;
                continue;
            }
            if (taskVertices.isEmpty()) {
                String msg = "#990.290 " + mainDocument + ", Can't find any task for batchId: " + batchId;
                log.info(msg);
                bs.getGeneralStatus().add(msg,'\n');
                isOk = false;
                continue;
            }
            if (taskVertices.size() > 1) {
                String msg = "#990.300 " + mainDocument + ", Can't download file because there are more than one task " +
                        "at the final state, batchId: " + batchId + ", workbookId: " + wb.getId();
                log.info(msg);
                bs.getGeneralStatus().add(msg,'\n');
                isOk = false;
                continue;
            }
            final Task task = taskRepository.findById(taskVertices.get(0).taskId).orElse(null);
            if (task==null) {
                String msg = "#990.303 " + mainDocument + ", Can't find task #" + taskVertices.get(0).taskId;
                log.info(msg);
                bs.getGeneralStatus().add(msg,'\n');
                isOk = false;
                continue;
            }

            EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());
            SnippetApiData.SnippetExec snippetExec;
            try {
                snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            } catch (YAMLException e) {
                bs.getGeneralStatus().add("#990.310 " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
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
                    bs.getProgressStatus().add("#990.320 " + mainDocument + ", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                                    ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                    "taskId: " + task.getId() + ", stationId: " + task.getStationId() +
                                    ", " + stationIpAndHost
                            ,'\n');
                    isOk = true;
                    continue;
                case ERROR:
                case BROKEN:
                    bs.getErrorStatus().add(getStatusForError(batchId, wb, mainDocument, task, snippetExec, stationIpAndHost));
                    isOk = true;
                    continue;
                case OK:
                    isOk = true;
                    // !!! Don't change to continue;
                    break;
            }

            if (wb.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                bs.getProgressStatus().add("#990.360 " + mainDocument + ", Task hasn't completed yet, " +
                                "batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + ", " +
                                "stationId: " + task.getStationId() + ", " + stationIpAndHost
                        ,'\n');
                isOk = true;
                continue;
            }

            PrepareZipData prepareZipData = new PrepareZipData(bs, task, zipDir, mainDocument, batchId, workbookId);
            isOk = prepareZip.apply(prepareZipData, zipDir);
            if (!isOk) {
                continue;
            }

            String msg = "#990.380 status - Ok, doc: " + mainDocument + ", batchId: " + batchId + ", workbookId: " + workbookId +
                    ", taskId: " + task.getId() + ", stationId: " + task.getStationId() + ", " + stationIpAndHost;
            bs.getOkStatus().add(msg,'\n');
            isOk = true;
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

    @SuppressWarnings("deprecation")
    private String getActualExtension(Long planId) {
        PlanImpl plan = planCache.findById(planId);
        if (plan == null) {
            return (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                    ? globals.defaultResultFileExtension
                    : ".bin");
        }

        PlanParamsYaml planParams = plan.getPlanParamsYaml();
        final Meta meta = MetaUtils.getMeta(planParams.planYaml.metas, ConstsApi.META_MH_RESULT_FILE_EXTENSION, Consts.RESULT_FILE_EXTENSION);

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

        StationStatusYaml status = StationStatusYamlUtils.BASE_YAML_UTILS.to(station.status);
        final String ip = StringUtils.isNotBlank(status.ip) ? status.ip : Consts.UNKNOWN_INFO;
        final String host = StringUtils.isNotBlank(status.host) ? status.host : Consts.UNKNOWN_INFO;

        return String.format(IP_HOST, ip, host);
    }

}
