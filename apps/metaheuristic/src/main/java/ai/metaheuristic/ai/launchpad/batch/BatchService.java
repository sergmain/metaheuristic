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
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.launchpad.batch.data.BatchAndWorkbookExecStates;
import ai.metaheuristic.ai.launchpad.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.launchpad.beans.*;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadEventService;
import ai.metaheuristic.ai.launchpad.launchpad_resource.ResourceService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.plan.PlanUtils;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml", ".yml");
    public static final String ATTACHMENTS_POOL_CODE = "attachments";
    private static final String CONFIG_FILE = "config.yaml";
    private static final String DELEMITER_2 = "\n====================================================================\n";

    private final Globals globals;
    private final PlanCache planCache;
    private final PlanService planService;
    private final BatchCache batchCache;
    private final BatchRepository batchRepository;
    private final WorkbookCache workbookCache;
    private final WorkbookService workbookService;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final BinaryDataService binaryDataService;
    private final TaskRepository taskRepository;
    private final StationCache stationCache;
    private final LaunchpadEventService launchpadEventService;
    private final ResourceService resourceService;

    private static final ConcurrentHashMap<Long, Object> batchMap = new ConcurrentHashMap<>(100, 0.75f, 10);

    /**
     * Don't forget to call this method before storing in db
     * @param batchStatus
     */
    public static BatchStatusProcessor initBatchStatus(BatchStatusProcessor batchStatus) {
        {
            String generalStr = batchStatus.getGeneralStatus().asString();
            if (!generalStr.isBlank()) {
                batchStatus.status = generalStr + DELEMITER_2;
            }
        }
        {
            String progressStr = batchStatus.getProgressStatus().asString();
            if (!progressStr.isBlank()) {
                batchStatus.status += progressStr + DELEMITER_2;
            }
        }
        {
            String okStr = batchStatus.getOkStatus().asString();
            if (!okStr.isBlank()) {
                batchStatus.status += okStr + DELEMITER_2;
            }
        }
        {
            String errorStr = batchStatus.getErrorStatus().asString();
            if (!errorStr.isBlank()) {
                batchStatus.status += errorStr + DELEMITER_2;
            }
        }
        return batchStatus;
    }

    public void loadFilesFromDirAfterZip(Batch batch, File srcDir, final Map<String, String> mapping) throws IOException {

        final AtomicBoolean isEmpty = new AtomicBoolean(true);
        Files.list(srcDir.toPath())
                .filter(o -> {
                    File f = o.toFile();
                    return !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .forEach( dataFilePath ->  {
                    isEmpty.set(false);
                    File file = dataFilePath.toFile();
                    try {
                        if (file.isDirectory()) {
                            final File mainDocFile = getMainDocumentFileFromConfig(file, mapping);
                            final Stream<BatchTopLevelService.FileWithMapping> files = Files.list(dataFilePath)
                                    .filter(o -> o.toFile().isFile())
                                    .map(f -> {
                                        final String currFileName = file.getName() + File.separatorChar + f.toFile().getName();
                                        final String actualFileName = mapping.get(currFileName);
                                        return new BatchTopLevelService.FileWithMapping(f.toFile(), actualFileName);
                                    });
                            createAndProcessTask(batch, files, mainDocFile);
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            createAndProcessTask(batch, Stream.of(new BatchTopLevelService.FileWithMapping(file, actualFileName)), file);
                        }
                    } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.130 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });


        if (isEmpty.get()) {
            changeStateToFinished(batch.id);
        }

    }

    public static File getMainDocumentFileFromConfig(File srcDir, Map<String, String> mapping) throws IOException {
        File configFile = new File(srcDir, CONFIG_FILE);
        if (!configFile.exists()) {
            throw new BatchResourceProcessingException("#995.140 config.yaml file wasn't found in path " + srcDir.getPath());
        }

        if (!configFile.isFile()) {
            throw new BatchResourceProcessingException("#995.150 config.yaml must be a file, not a directory");
        }
        String mainDocumentTemp = getMainDocument(configFile);

        Map.Entry<String, String> entry =
                mapping.entrySet()
                        .stream()
                        .filter(e -> e.getValue().equals(srcDir.getName() + '/' + mainDocumentTemp))
                        .findFirst().orElse(null);

        String mainDocument = entry!=null ? new File(entry.getKey()).getName() : mainDocumentTemp;

        final File mainDocFile = new File(srcDir, mainDocument);
        if (!mainDocFile.exists()) {
            throw new BatchResourceProcessingException("#995.170 main document file "+mainDocument+" wasn't found in path " + srcDir.getPath());
        }
        return mainDocFile;
    }

    private static String getMainDocument(File configFile) throws IOException {
        try (InputStream is = new FileInputStream(configFile)) {
            return getMainDocument(is);
        }
    }

    public static String getMainDocument(InputStream is) {
        String s;
        try {
            s = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BatchConfigYamlException("#995.153 Can't read config.yaml file, bad content, Error: " + e.getMessage());
        }
        if (!s.contains("mainDocument:")) {
            throw new BatchConfigYamlException("#995.154 Wrong format of config.yaml file, mainDocument field wasn't found");
        }

        // let's try to fix customer's error
        if (s.charAt("mainDocument:".length())!=' ') {
            s = s.replace("mainDocument:", "mainDocument: ");
        }

        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(s);
        String mainDocumentTemp = config.get(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH).toString();
        if (StringUtils.isBlank(mainDocumentTemp)) {
            throw new BatchResourceProcessingException("#995.160 config.yaml must contain non-empty field '" + Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH + "' ");
        }
        return mainDocumentTemp;
    }

    private void createAndProcessTask(Batch batch, Stream<BatchTopLevelService.FileWithMapping> dataFiles, File mainDocFile) {

        Long planId = batch.planId;
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", planId, Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", planId, ATTACHMENTS_POOL_CODE, nanoTime);
        final AtomicBoolean isMainDocPresent = new AtomicBoolean(false);
        AtomicReference<String> mainDocFilename = new AtomicReference<>();
        dataFiles.forEach( fileWithMapping -> {
            String originFilename = fileWithMapping.originName!=null ? fileWithMapping.originName : fileWithMapping.file.getName();
            if (EXCLUDE_EXT.contains(StrUtils.getExtension(originFilename))) {
                return;
            }
            final String code = ResourceUtils.toResourceCode(fileWithMapping.file.getName());

            String poolCode;
            if (fileWithMapping.file.equals(mainDocFile)) {
                poolCode = mainPoolCode;
                isMainDocPresent.set(true);
                mainDocFilename.set(fileWithMapping.originName);
            }
            else {
                poolCode = attachPoolCode;
                attachments.add(code);
            }

            resourceService.storeInitialResource(fileWithMapping.file, code, poolCode, originFilename);
        });

        if (!isMainDocPresent.get()) {
            throw new BatchResourceProcessingException("#995.180 main document wasn't found");
        }

        final WorkbookParamsYaml.WorkbookResourceCodes params = PlanUtils.initWorkbookParamsYaml(mainPoolCode, attachPoolCode, attachments);
        PlanApiData.TaskProducingResultComplex producingResult = workbookService.createWorkbook(planId, params);
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            throw new BatchResourceProcessingException("#995.190 Error creating workbook: " + producingResult.planProducingStatus);
        }
        BatchWorkbook bw = new BatchWorkbook();
        bw.batchId=batch.id;
        bw.workbookId=producingResult.workbook.getId();
        batchWorkbookRepository.save(bw);
        launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_WORKBOOK_CREATED, null, mainDocFilename.get(), null, batch.id, bw.workbookId, null );

        PlanImpl plan = planCache.findById(planId);
        if (plan == null) {
            throw new BatchResourceProcessingException("#995.200 plan wasn't found, planId: " + planId);
        }

        PlanApiData.TaskProducingResultComplex countTasks = planService.produceTasks(false, plan, producingResult.workbook.getId());
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            workbookService.changeValidStatus(bw.workbookId, false);
            throw new BatchResourceProcessingException("#995.220 validation of plan was failed, status: " + countTasks.planValidateStatus);
        }

        if (globals.maxTasksPerWorkbook < countTasks.numberOfTasks) {
            workbookService.changeValidStatus(producingResult.workbook.getId(), false);
            throw new BatchResourceProcessingException(
                    "#995.220 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerWorkbook +", tasks in this workbook:  " + countTasks.numberOfTasks);
        }
        workbookService.changeValidStatus(producingResult.workbook.getId(), true);

        // start producing new tasks
        OperationStatusRest operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        planService.createAllTasks();
        operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
    }

    public Batch changeStateToPreparing(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
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
    }

    public Batch changeStateToProcessing(Long batchId) {
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
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
            launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_PROCESSING_STARTED, null, null, null, batchId, null, null );
            return batchCache.save(b);
        }
    }

    private void changeStateToFinished(Long batchId) {
        if (batchId==null) {
            return;
        }
        final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                Batch b = batchRepository.findByIdForUpdate(batchId);
                if (b == null) {
                    log.warn("#990.050 batch wasn't found {}", batchId);
                    return;
                }
                if (b.execState != Enums.BatchExecState.Processing.code
                        && b.execState != Enums.BatchExecState.Finished.code
                ) {
                    throw new IllegalStateException("#990.060 Can't change state to Finished, " +
                            "current state: " + Enums.BatchExecState.toState(b.execState));
                }
                if (b.execState == Enums.BatchExecState.Finished.code) {
                    return;
                }
                b.execState = Enums.BatchExecState.Finished.code;
                batchCache.save(b);
            }
            finally {
                try {
                    updateBatchStatusWithoutSync(batchId);
                } catch (Throwable th) {
                    log.warn("#990.065 error while updating the status of batch #" + batchId, th);
                    // TODO 2019-12-15 this isn't good solution but need more info about behaviour with this error
                }
                launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_PROCESSING_FINISHED, null, null, null, batchId, null, null );
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

                BatchParamsYaml batchParams = BatchParamsYamlUtils.BASE_YAML_UTILS.to(b.params);
                if (batchParams == null) {
                    batchParams = new BatchParamsYaml();
                }
                if (batchParams.batchStatus==null) {
                    batchParams.batchStatus = new BatchParamsYaml.BatchStatus();
                }
                BatchStatusProcessor batchStatusProcessor = new BatchStatusProcessor();
                batchStatusProcessor.getGeneralStatus().add(error);
                initBatchStatus(batchStatusProcessor);
                batchParams.batchStatus.status = batchStatusProcessor.status;
                b.params = BatchParamsYamlUtils.BASE_YAML_UTILS.toString(batchParams);

                b = batchCache.save(b);
                return b;
            }
            finally {
                launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_FINISHED_WITH_ERROR, null, null, null, batchId, null, null );
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

    public void updateBatchStatuses() {
        List<BatchAndWorkbookExecStates> statuses = batchRepository.findAllUnfinished();
        Map<Long, List<BatchAndWorkbookExecStates>> map = statuses.parallelStream().collect(Collectors.groupingBy(status -> status.batchId));
        for (Long batchId : map.keySet()) {
            boolean isFinished = true;
            for (BatchAndWorkbookExecStates execStates : map.get(batchId)) {
/*
                public enum WorkbookExecState {
                    ERROR(-2),          // some error in configuration
                    UNKNOWN(-1),        // unknown state
                    NONE(0),            // just created workbook
                    PRODUCING(1),       // producing was just started
                    PRODUCED(2),        // producing was finished
                    STARTED(3),         // started
                    STOPPED(4),         // stopped
                    FINISHED(5),        // finished
                    DOESNT_EXIST(6),    // doesn't exist. this state is needed at station side to reconcile list of experiments
                    EXPORTING_TO_ATLAS(7),    // workbook is marked as needed to be exported to atlas
                    EXPORTING_TO_ATLAS_WAS_STARTED(8),    // workbook is marked as needed to be exported to atlas and export was started
                    EXPORTED_TO_ATLAS(9);    // workbook was exported to atlas
*/

                if (execStates.workbookState != -2 && execStates.workbookState != 5) {
                    isFinished = false;
                    break;
                }
            }
            if (isFinished) {
                final Object obj = batchMap.computeIfAbsent(batchId, o -> new Object());
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (obj) {
                    changeStateToFinished(batchId);
                }
            }
        }
    }

    List<BatchData.ProcessResourceItem> getBatches(Page<Long> batchIds) {
        List<BatchData.ProcessResourceItem> items = new ArrayList<>();
        List<Object[]> batchInfos = binaryDataService.getFilenamesForBatchIds(batchIds.getContent());
        for (Long batchId : batchIds) {
            Batch batch = batchCache.findById(batchId);
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
//                Account account = accountCache.findByUsername()
                BatchParamsYaml bpy = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.params);
                items.add(new BatchData.ProcessResourceItem(
                        batch, planCode, execStateStr, batch.execState, ok, filename,
                        S.b(bpy.username) ? "accountId #"+batch.accountId : bpy.username ));
            }
        }
        return items;
    }

    // TODO 2019-10-13 change synchronization to use BatchSyncService
    public BatchParamsYaml.BatchStatus updateStatus(Batch b) {
        final Object obj = batchMap.computeIfAbsent(b.id, o -> new Object());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return updateStatusInternal(b);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                log.warn("#990.097 NeedRetryAfterCacheCleanException was caught");
            }
            try {
                return updateStatusInternal(b);
            }
            catch(NeedRetryAfterCacheCleanException e) {
                final BatchStatusProcessor statusProcessor = new BatchStatusProcessor().addGeneralStatus("#990.100 Can't update batch status, Try later");
                return new BatchParamsYaml.BatchStatus(initBatchStatus(statusProcessor).status);
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private BatchParamsYaml.BatchStatus updateStatusInternal(Batch batch)  {
        Long batchId = batch.id;
        try {
            if (!S.b(batch.getParams()) &&
                    (batch.execState == Enums.BatchExecState.Finished.code || batch.execState == Enums.BatchExecState.Error.code)) {
                BatchParamsYaml batchParams = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.getParams());
                return batchParams.batchStatus;
            }
            return updateBatchStatusWithoutSync(batchId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#990.120 Error updating batch, new: {}, curr: {}", batch, batchRepository.findById(batchId).orElse(null));
            log.error("#990.121 Error updating batch", e);
            batchCache.evictById(batchId);
            // because this error is somehow related to stationCache, let's invalidate it
            stationCache.clearCache();
            throw new NeedRetryAfterCacheCleanException();
        }
    }

    private BatchParamsYaml.BatchStatus updateBatchStatusWithoutSync(Long batchId) {
        Batch b = batchRepository.findByIdForUpdate(batchId);
        if (b == null) {
            final BatchStatusProcessor statusProcessor = new BatchStatusProcessor().addGeneralStatus("#990.113, Batch wasn't found, batchId: " + batchId, '\n');
            BatchParamsYaml.BatchStatus batchStatus = new BatchParamsYaml.BatchStatus(initBatchStatus(statusProcessor).status);
            batchStatus.ok = false;
            return batchStatus;
        }

        BatchStatusProcessor batchStatus = prepareStatusAndData(batchId, (PrepareZipData prepareZipData, File file) -> true, null);
        BatchParamsYaml batchParams = BatchParamsYamlUtils.BASE_YAML_UTILS.to(b.getParams());
        if (batchParams == null) {
            batchParams = new BatchParamsYaml();
        }
        if (batchParams.batchStatus == null) {
            batchParams.batchStatus = new BatchParamsYaml.BatchStatus();
        }
        batchParams.batchStatus.status = batchStatus.status;
        b.params = BatchParamsYamlUtils.BASE_YAML_UTILS.toString(batchParams);
        batchCache.save(b);

        return batchParams.batchStatus;
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
        public BatchStatusProcessor bs;
        public Task task;
        public File zipDir;
        public String mainDocument;
        public Long batchId;
        public Long workbookId;
    }

    public BatchStatusProcessor prepareStatusAndData(Long batchId, BiFunction<PrepareZipData, File, Boolean> prepareZip, File zipDir) {
        final BatchStatusProcessor bs = new BatchStatusProcessor();
        bs.originArchiveName = getUploadedFilename(batchId);

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
        initBatchStatus(bs);

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

    public String getUploadedFilename(Long batchId) {
        final String filename = binaryDataService.findFilenameByBatchId(batchId);
        if (S.b(filename)) {
            log.error("#990.392 Filename is blank for batchId: {}, will be used default name - result.zip", batchId);
            return Consts.RESULT_ZIP;
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
