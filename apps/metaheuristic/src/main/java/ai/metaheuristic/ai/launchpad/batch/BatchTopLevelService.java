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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.batch.beans.Batch;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchStatus;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchWorkbook;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadEventService;
import ai.metaheuristic.ai.launchpad.launchpad_resource.ResourceService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 6/13/2019
 * Time: 11:52 PM
 */
@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class BatchTopLevelService {

    private static final String ATTACHMENTS_POOL_CODE = "attachments";
    private static final String RESULT_ZIP = "result.zip";

    private static final String CONFIG_FILE = "config.yaml";
    private static final String ALLOWED_CHARS_IN_ZIP_REGEXP = "^[/\\\\A-Za-z0-9._-]*$";
    private static final Pattern zipCharsPattern = Pattern.compile(ALLOWED_CHARS_IN_ZIP_REGEXP);
    private static final Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml", ".yml");
    private static final List<String> EXCLUDE_FROM_MAPPING = List.of("config.yaml", "config.yml");

    private final Globals globals;
    private final PlanCache planCache;
    private final PlanService planService;
    private final BinaryDataService binaryDataService;
    private final ResourceService resourceService;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final BatchCache batchCache;
    private final PlanRepository planRepository;
    private final WorkbookService workbookService;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final LaunchpadEventService launchpadEventService;

    public static final Function<String, Boolean> VALIDATE_ZIP_FUNCTION = BatchTopLevelService::isZipEntityNameOk;

    @Data
    @AllArgsConstructor
    public static class FileWithMapping {
        public File file;
        public String originName;
    }

    public static boolean isZipEntityNameOk(String name) {
        Matcher m = zipCharsPattern.matcher(name);
        return m.matches();
    }

    public BatchData.BatchesResult getBatches(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(20, pageable);
        Page<Long> batchIds = batchRepository.findAllByOrderByCreatedOnDesc(pageable);

        long total = batchIds.getTotalElements();

        List<BatchData.ProcessResourceItem> items = batchService.getBatches(batchIds);
        BatchData.BatchesResult result = new BatchData.BatchesResult();
        result.batches = new PageImpl<>(items, pageable, total);

        //noinspection unused
        int i=0;
        return result;
    }

    public BatchData.PlansForBatchResult getPlansForBatchResult() {
        final BatchData.PlansForBatchResult plans = new BatchData.PlansForBatchResult();
        plans.items = planRepository.findAllAsPlan().stream().filter(o->{
            if (!o.isValid()) {
                return false;
            }
            try {
                PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                return ppy.internalParams == null || !ppy.internalParams.archived;
            } catch (YAMLException e) {
                final String es = "#995.010 Can't parse Plan params. It's broken or unknown version. Plan id: #" + o.getId();
                plans.addErrorMessage(es);
                log.error(es);
                log.error("#995.015 Params:\n{}", o.getParams());
                log.error("#995.020 Error: {}", e.toString());
                return false;
            }
        }).sorted((o1,o2)-> Long.compare(o2.getId(), o1.getId())
        ).collect(Collectors.toList());

        return plans;
    }

    public OperationStatusRest batchUploadFromFile(final MultipartFile file, Long planId, final LaunchpadContext launchpadContext) {

        String tempFilename = file.getOriginalFilename();
        if (S.b(tempFilename)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.040 name of uploaded file is null or blank");
        }
        final String originFilename = tempFilename.toLowerCase();

        launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_FILE_UPLOADED, originFilename, file.getSize(), null, null, launchpadContext );

        PlanImpl plan = planCache.findById(planId);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.050 plan wasn't found, planId: " + planId);
        }

        // validate the plan
        // TODO 2019-07-06 Do we need to validate plan here in case that there is another check
        // in ai.metaheuristic.ai.launchpad.batch.process_resource.BatchTopLevelService.createAndProcessTask
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.060 validation of plan was failed, status: " + planValidation.status);
        }

        try {
            // tempDir will be deleted in processing thread
            File tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
            }
            String ext = StrUtils.getExtension(originFilename);
            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);
            log.debug("Start storing an uploaded file to disk");
            try(OutputStream os = new FileOutputStream(dataFile)) {
                IOUtils.copy(file.getInputStream(), os, 32000);
            }

            final Batch b = batchCache.save(new Batch(planId, Enums.BatchExecState.Stored));

            launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_CREATED, null, null, b.id, null, launchpadContext );

            try(InputStream is = new FileInputStream(dataFile)) {
                String code = ResourceUtils.toResourceCode(originFilename);
                binaryDataService.save(
                        is, dataFile.length(), EnumsApi.BinaryDataType.BATCH, code, code,
                        true, originFilename, b.id, EnumsApi.BinaryDataRefType.batch);
            }

            final Batch batch = batchService.changeStateToPreparing(b.id);
            // TODO 2019-10-14 when batch is null tempDir won't be deleted
            if (batch==null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.080 can't find batch with id " + b.id);
            }

            log.info("The file {} was successfully stored to disk", originFilename);
            new Thread(() -> {
                try {
                    if (originFilename.endsWith(".zip")) {

                        log.debug("Start unzipping archive");
                        Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, tempDir, true, EXCLUDE_FROM_MAPPING);
                        log.debug("Start loading file data to db");
                        loadFilesFromDirAfterZip(batch, tempDir, mapping);
                    }
                    else {
                        log.debug("Start loading file data to db");
                        loadFilesFromDirAfterZip(batch, tempDir, Map.of(dataFile.getName(), originFilename));
                    }
                }
                catch(UnzipArchiveException e) {
                    final String es = "#995.100 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
                    log.error(es, e);
                    batchService.changeStateToError(batch.id, es);
                }
                catch(Throwable th) {
                    final String es = "#995.110 General processing error. Error: " + th.getMessage() + ", class: " + th.getClass();
                    log.error(es, th);
                    batchService.changeStateToError(batch.id, es);
                }
                finally {
                    try {
                        FileUtils.deleteDirectory(tempDir);
                    } catch (IOException e) {
                        // it's cleaning so don't report any error
                    }
                }
            }).start();
            //noinspection unused
            int i=0;
        }
        catch (Throwable th) {
            log.error("Error", th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.120 can't load file, error: " + th.getMessage()+", class: " + th.getClass());
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest processResourceDeleteCommit(Long batchId) {

        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#995.250 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        List<Long> workbookIds = batchWorkbookRepository.findWorkbookIdsByBatchId(batch.id);
        for (Long workbookId : workbookIds) {
            planService.deleteWorkbook(workbookId);
        }
        batchWorkbookRepository.deleteByBatchId(batch.id);
        batchCache.deleteById(batch.id);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #"+batch.id+" was deleted successfully.", null);
    }

    private void loadFilesFromDirAfterZip(Batch batch, File srcDir, final Map<String, String> mapping) throws IOException {

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
                            final Stream<FileWithMapping> files = Files.list(dataFilePath)
                                    .filter(o -> o.toFile().isFile())
                                    .map(f -> {
                                        final String currFileName = file.getName() + File.separatorChar + f.toFile().getName();
                                        final String actualFileName = mapping.get(currFileName);
                                        return new FileWithMapping(f.toFile(), actualFileName);
                                    });
                            createAndProcessTask(batch, files, mainDocFile);
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            createAndProcessTask(batch, Stream.of(new FileWithMapping(file, actualFileName)), file);
                        }
                    } catch (BatchResourceProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.130 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });


        if (isEmpty.get()) {
            batchService.changeStateToFinished(batch.id);
        }

    }

    private File getMainDocumentFileFromConfig(File srcDir, Map<String, String> mapping) throws IOException {
        File configFile = new File(srcDir, CONFIG_FILE);
        if (!configFile.exists()) {
            throw new BatchResourceProcessingException("#995.140 config.yaml file wasn't found in path " + srcDir.getPath());
        }

        if (!configFile.isFile()) {
            throw new BatchResourceProcessingException("#995.150 config.yaml must be a file, not a directory");
        }
        Yaml yaml = new Yaml();

        String mainDocumentTemp;
        try (InputStream is = new FileInputStream(configFile)) {
            Map<String, Object> config = yaml.load(is);
            mainDocumentTemp = config.get(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH).toString();
        }

        if (StringUtils.isBlank(mainDocumentTemp)) {
            throw new BatchResourceProcessingException("#995.160 config.yaml must contain non-empty field '" + Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH + "' ");
        }

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

    private static WorkbookParamsYaml initWorkbookParamsYaml(String mainPoolCode, String attachPoolCode, List<String> attachmentCodes) {
        WorkbookParamsYaml yaml = new WorkbookParamsYaml();
        yaml.workbookYaml.preservePoolNames = true;
        yaml.workbookYaml.poolCodes.computeIfAbsent(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, o-> new ArrayList<>()).add(mainPoolCode);
        if (attachmentCodes.isEmpty()) {
            return yaml;
        }
        yaml.workbookYaml.poolCodes.computeIfAbsent(ATTACHMENTS_POOL_CODE, o-> new ArrayList<>()).add(attachPoolCode);
        return yaml;
    }

    private void createAndProcessTask(Batch batch, Stream<FileWithMapping> dataFiles, File mainDocFile) {

        Long planId = batch.planId;
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", planId, Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", planId, ATTACHMENTS_POOL_CODE, nanoTime);
        final AtomicBoolean isMainDocPresent = new AtomicBoolean(false);
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

        final WorkbookParamsYaml params = initWorkbookParamsYaml(mainPoolCode, attachPoolCode, attachments);
        PlanApiData.TaskProducingResultComplex producingResult = workbookService.createWorkbook(planId, params);
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            throw new BatchResourceProcessingException("#995.190 Error creating workbook: " + producingResult.planProducingStatus);
        }
        BatchWorkbook bw = new BatchWorkbook();
        bw.batchId=batch.id;
        bw.workbookId=producingResult.workbook.getId();
        batchWorkbookRepository.save(bw);

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


        batchService.changeStateToProcessing(batch.id);
        operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
    }

    public BatchData.Status getProcessingResourceStatus(Long batchId) {
        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#995.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return new BatchData.Status(es);
        }
        BatchStatus status = batchService.updateStatus(batchId);
        return new BatchData.Status(batchId, status.getStatus(), status.ok);
    }

    public ResourceWithCleanerInfo getBatchProcessingResult(Long batchId) throws IOException {
        ResourceWithCleanerInfo resource = new ResourceWithCleanerInfo();

        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
        resource.toClean.add(resultDir);

        File zipDir = new File(resultDir, "zip");
        zipDir.mkdir();

        BatchStatus status = batchService.prepareStatusAndData(batchId, this::prepareZip, zipDir);

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);
        File zipFile = new File(resultDir, RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile, status.renameTo);


        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.setContentDispositionFormData("attachment", RESULT_ZIP);
        resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile), RestUtils.getHeader(httpHeaders, zipFile.length()), HttpStatus.OK);
        return resource;
    }

    private boolean prepareZip(BatchService.PrepareZipData prepareZipData, File zipDir ) {
        final TaskParamsYaml taskParamYaml;
        try {
            taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(prepareZipData.task.getParams());
        } catch (YAMLException e) {
            prepareZipData.bs.getErrorStatus().add(
                    "#990.350 " + prepareZipData.mainDocument + ", " +
                            "Task has broken data in params, status: " + EnumsApi.TaskExecState.from(prepareZipData.task.getExecState()) +
                            ", batchId:" + prepareZipData.batchId +
                            ", workbookId: " + prepareZipData.workbookId + ", " +
                            "taskId: " + prepareZipData.task.getId(), '\n');
            return false;
        }

        File tempFile;
        try {
            tempFile = File.createTempFile("doc-", ".xml", zipDir);
        } catch (IOException e) {
            String msg = "#990.370 Error create a temp file in "+zipDir.getAbsolutePath();
            log.error(msg);
            prepareZipData.bs.getGeneralStatus().add(msg,'\n');
            return false;
        }

        // all documents are sored in zip folder
        prepareZipData.bs.renameTo.put("zip/" + tempFile.getName(), "zip/" + prepareZipData.mainDocument);

        try {
            binaryDataService.storeToFile(taskParamYaml.taskYaml.outputResourceCode, tempFile);
        } catch (BinaryDataNotFoundException e) {
            String msg = "#990.375 Error store data to temp file, data doesn't exist in db, code " + taskParamYaml.taskYaml.outputResourceCode +
                    ", file: " + tempFile.getPath();
            log.error(msg);
            prepareZipData.bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        return true;
    }


}
