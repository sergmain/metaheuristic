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
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.batch.data.BatchStatus;
import ai.metaheuristic.ai.launchpad.beans.Batch;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadEventService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
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
import ai.metaheuristic.commons.S;
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
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private static final String ALLOWED_CHARS_IN_ZIP_REGEXP = "^[/\\\\A-Za-z0-9._-]*$";
    private static final Pattern zipCharsPattern = Pattern.compile(ALLOWED_CHARS_IN_ZIP_REGEXP);
    private static final List<String> EXCLUDE_FROM_MAPPING = List.of("config.yaml", "config.yml");

    private final PlanCache planCache;
    private final PlanService planService;
    private final BinaryDataService binaryDataService;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final BatchCache batchCache;
    private final PlanRepository planRepository;
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

    public BatchData.BatchesResult getBatches(Pageable pageable, LaunchpadContext context) {
        pageable = ControllerUtils.fixPageSize(20, pageable);
        Page<Long> batchIds = batchRepository.findAllByOrderByCreatedOnDesc(pageable, context.getCompanyId());

        long total = batchIds.getTotalElements();

        List<BatchData.ProcessResourceItem> items = batchService.getBatches(batchIds);
        BatchData.BatchesResult result = new BatchData.BatchesResult();
        result.batches = new PageImpl<>(items, pageable, total);

        //noinspection unused
        int i=0;
        return result;
    }

    public BatchData.PlansForBatchResult getPlansForBatchResult(LaunchpadContext context) {
        final BatchData.PlansForBatchResult plans = new BatchData.PlansForBatchResult();
        plans.items = planRepository.findAllAsPlan(context.getCompanyId()).stream().filter(o->{
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
        PlanImpl plan = planCache.findById(planId);
        if (plan == null || !plan.companyId.equals(launchpadContext.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.050 plan wasn't found, planId: " + planId);
        }
        if (!plan.companyId.equals(launchpadContext.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.051 plan wasn't found, planId: " + planId);
        }

        String tempFilename = file.getOriginalFilename();
        if (S.b(tempFilename)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.040 name of uploaded file is null or blank");
        }
        final String originFilename = tempFilename.toLowerCase();

        launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_FILE_UPLOADED, originFilename, file.getSize(), null, null, launchpadContext );

        // TODO 2019-07-06 Do we need to validate plan here in case that there is another check
        //  2019-10-28 it's working so left it as is until there will be found an issue with this
        // validate the plan
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
                        batchService.loadFilesFromDirAfterZip(batch, tempDir, mapping);
                    }
                    else {
                        log.debug("Start loading file data to db");
                        batchService.loadFilesFromDirAfterZip(batch, tempDir, Map.of(dataFile.getName(), originFilename));
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

    public OperationStatusRest processResourceDeleteCommit(Long batchId, LaunchpadContext context) {

        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(context.getCompanyId())) {
            final String es = "#995.250 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        List<Long> workbookIds = batchWorkbookRepository.findWorkbookIdsByBatchId(batch.id);
        for (Long workbookId : workbookIds) {
            planService.deleteWorkbook(workbookId, context);
        }
        batchWorkbookRepository.deleteByBatchId(batch.id);
        batchCache.deleteById(batch.id);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #"+batch.id+" was deleted successfully.", null);
    }

    public BatchData.Status getProcessingResourceStatus(Long batchId, LaunchpadContext context) {
        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(context.getCompanyId())) {
            final String es = "#995.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return new BatchData.Status(es);
        }
        BatchStatus status = batchService.updateStatus(batch);
        return new BatchData.Status(batchId, status.getStatus(), status.ok);
    }

    public ResourceWithCleanerInfo getBatchProcessingResult(Long batchId, LaunchpadContext context) throws IOException {
        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(context.getCompanyId())) {
            final String es = "#995.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return null;
        }
        ResourceWithCleanerInfo resource = new ResourceWithCleanerInfo();

        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
        resource.toClean.add(resultDir);

        File zipDir = new File(resultDir, "zip");
        zipDir.mkdir();

        BatchStatus status = batchService.prepareStatusAndData(batchId, this::prepareZip, zipDir);

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);
        File zipFile = new File(resultDir, Consts.RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile, status.renameTo);


        String filename = StrUtils.getName(status.originArchiveName) + Consts.ZIP_EXT;

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        // https://stackoverflow.com/questions/93551/how-to-encode-the-filename-parameter-of-content-disposition-header-in-http
        httpHeaders.setContentDisposition(ContentDisposition.parse(
                "filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())));
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

        // all documents are sorted in zip folder
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
