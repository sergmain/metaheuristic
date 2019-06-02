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

package ai.metaheuristic.ai.pilot.process_resource;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.pilot.beans.Batch;
import ai.metaheuristic.ai.pilot.beans.BatchParams;
import ai.metaheuristic.ai.pilot.beans.BatchStatus;
import ai.metaheuristic.ai.pilot.data.BatchData;
import ai.metaheuristic.ai.yaml.input_resource_param.InputResourceParamUtils;
import ai.metaheuristic.ai.yaml.pilot.BatchParamsUtils;
import ai.metaheuristic.ai.yaml.plan.PlanYamlUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.ai.yaml.task.TaskParamYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.*;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Task;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/1/2019
 * Time: 4:18 PM
 */
@SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
@Service
@Slf4j
public class BatchService {

    private static final String PLAN_NOT_FOUND = "Plan wasn't found";
    private static final String IP_HOST = "IP: %s, host: %s";

    private final Globals globals;
    private final PlanCache planCache;
    private final BatchCache batchCache;
    private final WorkbookRepository workbookRepository;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final BinaryDataService binaryDataService;
    private final TaskRepository taskRepository;
    private final StationCache stationCache;

    public BatchService(Globals globals, PlanCache planCache, BatchCache batchCache, WorkbookRepository workbookRepository, BatchWorkbookRepository batchWorkbookRepository, BinaryDataService binaryDataService, TaskRepository taskRepository, StationCache stationCache) {
        this.globals = globals;
        this.planCache = planCache;
        this.batchCache = batchCache;
        this.workbookRepository = workbookRepository;
        this.batchWorkbookRepository = batchWorkbookRepository;
        this.binaryDataService = binaryDataService;
        this.taskRepository = taskRepository;
        this.stationCache = stationCache;
    }

    private final static Object syncObj = new Object();

    public Batch changeStateToPreparing(Long batchId) {
        synchronized (syncObj) {
            Batch b = batchCache.findById(batchId);
            if (b == null) {
                log.warn("#990.505 batch wasn't found {}", batchId);
                return null;
            }
            if (b.execState != Enums.BatchExecState.Unknown.code && b.execState != Enums.BatchExecState.Stored.code &&
                    b.execState != Enums.BatchExecState.Preparing.code) {
                throw new IllegalStateException("\"#990.515 Can't change state to Preparing, " +
                        "current state: " + Enums.BatchExecState.toState(b.execState));
            }
            if (b.execState == Enums.BatchExecState.Preparing.code) {
                return b;
            }
            b.execState = Enums.BatchExecState.Preparing.code;
            return batchCache.save(b);
        }
    }

    public Batch changeStateToProcessing(Long batchId) {
        synchronized (syncObj) {
            Batch b = batchCache.findById(batchId);
            if (b == null) {
                log.warn("#990.540 batch wasn't found {}", batchId);
                return null;
            }
            if (b.execState != Enums.BatchExecState.Preparing.code && b.execState != Enums.BatchExecState.Processing.code) {
                throw new IllegalStateException("\"#990.530 Can't change state to Finished, " +
                        "current state: " + Enums.BatchExecState.toState(b.execState));
            }
            if (b.execState == Enums.BatchExecState.Processing.code) {
                return b;
            }
            b.execState = Enums.BatchExecState.Processing.code;
            return batchCache.save(b);
        }
    }

    public Batch changeStateToFinished(Long batchId) {
        synchronized (syncObj) {
            Batch b = batchCache.findById(batchId);
            if (b == null) {
                log.warn("#990.525 batch wasn't found {}", batchId);
                return null;
            }
            if (b.execState != Enums.BatchExecState.Processing.code && b.execState != Enums.BatchExecState.Finished.code) {
                throw new IllegalStateException("\"#990.530 Can't change state to Finished, " +
                        "current state: " + Enums.BatchExecState.toState(b.execState));
            }
            if (b.execState == Enums.BatchExecState.Finished.code) {
                return b;
            }
            b.execState = Enums.BatchExecState.Finished.code;
            return batchCache.save(b);
        }
    }

    @SuppressWarnings("unused")
    public Batch changeStateToError(Batch batch, String error) {
        synchronized (syncObj) {
            Batch b = batchCache.findById(batch.id);
            if (b == null) {
                log.warn("#990.410 batch not found in db, batchId: #{}", batch.id);
                return null;
            }
            b.setExecState(Enums.BatchExecState.Error.code);
            // TODO 2019.05.25 add storing of error message
            b = batchCache.save(b);
            return b;
        }
    }


    public Batch createNewBatch(Long planId) {
        return batchCache.save(new Batch(planId, Enums.BatchExecState.Stored));
    }

    public Batch findById(Long id) {
        return batchCache.findById(id);
    }

    public void deleteById(Long id) {
        batchCache.deleteById(id);
    }


    private static String getMainDocumentPoolCode(String inputResourceParams) {
        InputResourceParam resourceParams = InputResourceParamUtils.to(inputResourceParams);
        List<String> codes = resourceParams.poolCodes.get(ProcessResourceController.MAIN_DOCUMENT_POOL_CODE);
        if (codes.isEmpty()) {
            throw new IllegalStateException("#990.92 Main document section is missed. inputResourceParams:\n" + inputResourceParams);
        }
        if (codes.size()>1) {
            throw new IllegalStateException("#990.92 Main document section contains more than one main document. inputResourceParams:\n" + inputResourceParams);
        }
        return codes.get(0);
    }

    List<BatchData.ProcessResourceItem> getBatches(Page<Long> batchIds) {
        List<BatchData.ProcessResourceItem> items = new ArrayList<>();
        for (Long batchId : batchIds) {
            Batch batch = batchCache.findById(batchId);
            if (batch.execState!= Enums.BatchExecState.Finished.code && batch.execState!= Enums.BatchExecState.Archived.code) {
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
                    batch.setExecState(Enums.BatchExecState.Finished.code);
                    batch = batchCache.save(batch);

                }
            }
            BatchStatus status = prepareStatusAndData(batch.id, null, false, false);
            boolean ok = status.ok;
            String planCode = PLAN_NOT_FOUND;
            Plan plan = planCache.findById(batch.getPlanId());
            if (plan!=null) {
                planCode = plan.getCode();
            }
            else {
                if (batch.execState!=Enums.BatchExecState.Preparing.code) {
                    ok = false;
                }
            }
            // fix current state in case when data is preparing right now
            if (batch.execState==Enums.BatchExecState.Preparing.code) {
                ok = true;
            }
            String execStateStr = Enums.BatchExecState.toState(batch.execState).toString();
            items.add( new BatchData.ProcessResourceItem(batch, planCode, execStateStr, batch.execState, ok));
        }
        return items;
    }

    @SuppressWarnings("SameParameterValue")
    BatchStatus prepareStatusAndData(Long batchId, File zipDir, boolean fullConsole, boolean storeToDisk)  {
        final BatchStatus bs = new BatchStatus();

        Batch b = batchCache.findById(batchId);
        if (b==null) {
            bs.add("#990.520, Batch wasn't found, batchId: " + batchId, '\n');
            bs.ok = false;
            return bs;
        }

        if (b.getParams()!=null && !b.getParams().isBlank() &&
                (b.execState==Enums.BatchExecState.Finished.code || b.execState==Enums.BatchExecState.Error.code)) {
            BatchParams batchParams = BatchParamsUtils.to(b.getParams());
            return batchParams.batchStatus;
        }

        List<Long> ids = batchWorkbookRepository.findWorkbookIdsByBatchId(batchId);
        if (ids.isEmpty()) {
            bs.add("#990.107, Batch is empty, there isn't any task, batchId: " + batchId, '\n');
            bs.ok = true;
            return bs;
        }

        boolean isOk = true;
        for (Long workbookId : ids) {
            Workbook wb = workbookRepository.findById(workbookId).orElse(null);
            if (wb == null) {
                String msg = "#990.114 Batch #" + batchId + " contains broken workbookId - #" + workbookId;
                bs.add(msg, '\n');
                log.warn(msg);
                isOk = false;
                continue;
            }
            String mainDocumentPoolCode = getMainDocumentPoolCode(wb.getInputResourceParam());

            final String fullMainDocument = getMainDocumentForPoolCode(mainDocumentPoolCode);
            if (fullMainDocument == null) {
                String msg = "#990.123, " + mainDocumentPoolCode + ", Can't determine actual file name of main document, " +
                        "batchId: " + batchId + ", workbookId: " + workbookId;
                log.warn(msg);
                bs.add(msg, '\n');
                isOk = false;
                continue;
            }
            String mainDocument = StrUtils.getName(fullMainDocument) + getActualExtension(wb.getPlanId());

            Integer taskOrder = taskRepository.findMaxConcreteOrder(wb.getId());
            // TODO 2019-05-23 investigate all cases when this is happened
            if (taskOrder == null) {
                String msg = "#990.128, " + mainDocument + ", Tasks weren't created correctly for this batch, need to re-upload documents, " +
                        "batchId: " + batchId + ", workbookId: " + workbookId;
                log.warn(msg);
                bs.add(msg, '\n');
                isOk = false;
                continue;
            }
            List<Task> tasks = taskRepository.findAnyWithConcreteOrder(wb.getId(), taskOrder);
            if (tasks.isEmpty()) {
                String msg = "#990.133, " + mainDocument + ", Can't find any task for batchId: " + batchId;
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            if (tasks.size() > 1) {
                String msg = "#990.137, " + mainDocument + ", Can't download file because there are more than one task " +
                        "at the final state, batchId: " + batchId + ", workbookId: " + wb.getId();
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            final Task task = tasks.get(0);
            EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());
            SnippetApiData.SnippetExec snippetExec;
            try {
                snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            } catch (YAMLException e) {
                bs.add("#990.139, " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                        ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(),'\n');
                isOk = false;
                continue;
            }
            Station s = null;
            if (task.getStationId()!=null) {
                s = stationCache.findById(task.getStationId());
            }
            switch (execState) {
                case NONE:
                case IN_PROGRESS:
                    bs.add("#990.142, " + mainDocument + ", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                            ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                            "taskId: " + task.getId() + ", stationId: " + task.getStationId() +
                            ", " + getStationIpAndHost(s)
                            ,'\n');
                    isOk = true;
                    continue;
                case ERROR:
                    bs.add("#990.149, " + mainDocument + ", Task was completed with error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                            "taskId: " + task.getId() + "\n" +
                            "stationId: " + task.getStationId() + "\n" +
                            getStationIpAndHost(s) + "\n" +
                            "isOk: " + snippetExec.exec.isOk + "\n" +
                            "exitCode: " + snippetExec.exec.exitCode + "\n" +
                            "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>") + "\n\n");
                    isOk = true;
                    continue;
                case OK:
                    if (fullConsole) {
                        bs.add("#990.151, " + mainDocument + ", Task completed without any error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + "\n" +
                                "stationId: " + task.getStationId() + "\n" +
                                getStationIpAndHost(s) + "\n" +
                                "isOk: " + snippetExec.exec.isOk + "\n" +
                                "exitCode: " + snippetExec.exec.exitCode + "\n" +
                                "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>") + "\n\n");
                    }
                    isOk = true;
                    // !!! Don't change to continue;
                    break;
            }

            final TaskApiData.TaskParamYaml taskParamYaml;
            try {
                taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
            } catch (YAMLException e) {
                bs.add("#990.153, " + mainDocument + ", Task has broken data in params, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                        ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(), '\n');
                isOk = false;
                continue;
            }

            if (wb.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                bs.add("#990.155, " + mainDocument + ", Task hasn't completed yet, " +
                        "batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId() + ", " +
                        "stationId: " + task.getStationId() + ", " + getStationIpAndHost(s)
                        ,'\n');
                isOk = true;
                continue;
            }

            File mainDocFile = zipDir!=null ? new File(zipDir, mainDocument) : new File(mainDocument);

            if (storeToDisk) {
                try {
                    binaryDataService.storeToFile(taskParamYaml.outputResourceCode, mainDocFile);
                } catch (BinaryDataNotFoundException e) {
                    String msg = "#990.161 Error store data to temp file, data doesn't exist in db, code " + taskParamYaml.outputResourceCode +
                            ", file: " + mainDocFile.getPath();
                    log.error(msg);
                    bs.add(msg,'\n');
                    isOk = false;
                    continue;
                }
            }

            if (!fullConsole) {
                String msg = "#990.167 status - Ok, doc: " + mainDocFile.getName() + ", batchId: " + batchId + ", workbookId: " + workbookId +
                        ", taskId: " + task.getId() + ", stationId: " + task.getStationId() + ", " + getStationIpAndHost(s);
                bs.add(msg,'\n');
                isOk = true;
            }

        }

        if (b.getParams()!=null && !b.getParams().isBlank() &&
                (b.execState==Enums.BatchExecState.Finished.code || b.execState==Enums.BatchExecState.Error.code)) {
            BatchParams batchParams = BatchParamsUtils.to(b.getParams());
            return batchParams.batchStatus;
        }

        bs.ok = isOk;

        BatchParams batchParams = BatchParamsUtils.to(b.getParams());
        if (batchParams==null) {
            batchParams = new BatchParams();
        }
        bs.init();
        batchParams.batchStatus = bs;

        b.params = BatchParamsUtils.toString(batchParams);
        batchCache.save(b);

        return bs;
    }

    private String getMainDocumentForPoolCode(String mainDocumentPoolCode) {
        final String filename = binaryDataService.getFilenameByPool1CodeAndType(mainDocumentPoolCode, EnumsApi.BinaryDataType.DATA);
        if (StringUtils.isBlank(filename)) {
            log.error("#990.15 Filename is blank for poolCode: {}, data type: {}", mainDocumentPoolCode, EnumsApi.BinaryDataType.DATA);
            return null;
        }
        return filename;
    }

    private String getActualExtension(Long planId) {
        Plan plan = planCache.findById(planId);
        if (plan == null) {
            return (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                    ? globals.defaultResultFileExtension
                    : ".bin(???)");
        }

        PlanApiData.PlanYaml planYaml = PlanYamlUtils.toPlanYaml(plan.getParams());
        Meta meta = planYaml.getMeta(Consts.RESULT_FILE_EXTENSION);

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
