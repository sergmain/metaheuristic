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
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.beans.Batch;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.data.PlanData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadEventService;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.plan.PlanUtils;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.S;
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
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ai.metaheuristic.ai.Consts.XML_EXT;
import static ai.metaheuristic.ai.Consts.ZIP_EXT;

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

    private final PlanService planService;
    private final BinaryDataService binaryDataService;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final BatchCache batchCache;
    private final LaunchpadEventService launchpadEventService;
    private final WorkbookService workbookService;

    public static final Function<String, Boolean> VALIDATE_ZIP_FUNCTION = BatchTopLevelService::isZipEntityNameOk;

    @SuppressWarnings("unused")
    public BatchData.ExecStatuses getBatchExecStatuses(LaunchpadContext context) {
        //noinspection UnnecessaryLocalVariable
        BatchData.ExecStatuses execStatuses = new BatchData.ExecStatuses(batchRepository.getBatchExecStatuses(context.getCompanyId()));
        return execStatuses;
    }

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

    public BatchData.BatchesResult getBatches(Pageable pageable, LaunchpadContext context, boolean includeDeleted, boolean filterBatches) {
        return getBatches(pageable, context.getCompanyId(), context.account, includeDeleted, context.account != null && filterBatches);
    }

    public BatchData.BatchesResult getBatches(Pageable pageable, Long companyUniqueId, Account account, boolean includeDeleted, boolean filterBatches) {
        if (filterBatches && account==null) {
            throw new IllegalStateException("(filterBatches && account==null)");
        }
        pageable = ControllerUtils.fixPageSize(20, pageable);
        Page<Long> batchIds;
        if (includeDeleted) {
            if (filterBatches) {
                batchIds = batchRepository.findAllForAccountByOrderByCreatedOnDesc(pageable, companyUniqueId, account.id);
            }
            else {
                batchIds = batchRepository.findAllByOrderByCreatedOnDesc(pageable, companyUniqueId);
            }
        }
        else {
            if (filterBatches) {
                batchIds = batchRepository.findAllForAccountExcludeDeletedByOrderByCreatedOnDesc(pageable, companyUniqueId, account.id);
            }
            else {
                batchIds = batchRepository.findAllExcludeDeletedByOrderByCreatedOnDesc(pageable, companyUniqueId);
            }
        }

        long total = batchIds.getTotalElements();

        List<BatchData.ProcessResourceItem> items = batchService.getBatches(batchIds);
        BatchData.BatchesResult result = new BatchData.BatchesResult();
        result.batches = new PageImpl<>(items, pageable, total);

        return result;
    }

    public BatchData.UploadingStatus batchUploadFromFile(final MultipartFile file, Long planId, final LaunchpadContext context) {
        String tempFilename = file.getOriginalFilename();
        if (S.b(tempFilename)) {
            return new BatchData.UploadingStatus("#995.040 name of uploaded file is null or blank");
        }
        // fix for the case when browser send full path, ie Edge
        final String originFilename = new File(tempFilename.toLowerCase()).getName();

        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new BatchData.UploadingStatus(
                    "#995.043 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT, XML_EXT)) {
            return new BatchData.UploadingStatus("#995.046 only '.zip', '.xml' files are supported, bad filename: " + originFilename);
        }

        PlanData.PlansForCompany plansForCompany = planService.getPlan(context.getCompanyId(), planId);
        if (plansForCompany.isErrorMessages()) {
            return new BatchData.UploadingStatus(plansForCompany.errorMessages);
        }
        PlanImpl plan = plansForCompany.items.isEmpty() ? null : (PlanImpl) plansForCompany.items.get(0);
        if (plan==null) {
            return new BatchData.UploadingStatus("#995.050 plan wasn't found, planId: " + planId);
        }
        if (!plan.getId().equals(planId)) {
            return new BatchData.UploadingStatus("#995.038 Fatal error in configuration of plan, report to developers immediately");
        }
        launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_FILE_UPLOADED, context.getCompanyId(), originFilename, file.getSize(), null, null, context );

        // TODO 2019-07-06 Do we need to validate the plan here in case that there is another check
        //  2019-10-28 it's working so left it as is until there will be found an issue with this
        // validate the plan
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            return new BatchData.UploadingStatus("#995.060 validation of plan was failed, status: " + planValidation.status);
        }

        Batch b;
        PlanApiData.TaskProducingResultComplex producingResult;
        try {
            // tempDir will be deleted in processing thread
            File tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new BatchData.UploadingStatus("#995.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
            }
            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);
            log.debug("Start storing an uploaded file to disk");
            try(OutputStream os = new FileOutputStream(dataFile)) {
                IOUtils.copy(file.getInputStream(), os, 32000);
            }

            String code = ResourceUtils.toResourceCode(originFilename);

            WorkbookParamsYaml.WorkbookYaml workbookYaml = PlanUtils.asWorkbookParamsYaml(code);
            producingResult = workbookService.createWorkbook(planId, workbookYaml, false);
            if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
                throw new BatchResourceProcessingException("#995.075 Error creating workbook: " + producingResult.planProducingStatus);
            }

            try(InputStream is = new FileInputStream(dataFile)) {
                binaryDataService.save(
                        is, dataFile.length(), EnumsApi.BinaryDataType.BATCH, code,
                        originFilename, producingResult.workbook.getId());
            }

            b = new Batch(planId, producingResult.workbook.getId(), Enums.BatchExecState.Stored, context.getAccountId(), context.getCompanyId());
            BatchParamsYaml bpy = new BatchParamsYaml();
            bpy.username = context.account.username;
            b.params = BatchParamsYamlUtils.BASE_YAML_UTILS.toString(bpy);
            b = batchCache.save(b);

            launchpadEventService.publishBatchEvent(EnumsApi.LaunchpadEventType.BATCH_CREATED, context.getCompanyId(), plan.getCode(), null, b.id, producingResult.workbook.getId(), context );

            final Batch batch = batchService.changeStateToPreparing(b.id);
            // TODO 2019-10-14 when batch is null tempDir won't be deleted, this is wrong behavior and need to be fixed
            if (batch==null) {
                return new BatchData.UploadingStatus("#995.080 can't find batch with id " + b.id);
            }

            log.info("The file {} was successfully stored to disk", originFilename);

            if (true) {
                // TODO  insert here additional processing of file of batch.
                //  and which one is it?
                return new BatchData.UploadingStatus("Need to re-write");
            }

            //noinspection unused
            int i=0;
        }
        catch (Throwable th) {
            log.error("Error", th);
            return new BatchData.UploadingStatus("#995.120 can't load file, error: " + th.getMessage()+", class: " + th.getClass());
        }
        BatchData.UploadingStatus uploadingStatus = new BatchData.UploadingStatus(b.id, producingResult.workbook.getId());
        return uploadingStatus;
    }

    public OperationStatusRest processResourceDeleteCommit(Long batchId, LaunchpadContext context, boolean isVirtualDeletion) {
        return processResourceDeleteCommit(batchId, context.getCompanyId(), isVirtualDeletion);
    }

    public OperationStatusRest processResourceDeleteCommit(Long batchId, Long companyUniqueId, boolean isVirtualDeletion) {

        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(companyUniqueId)) {
            final String es = "#995.250 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        if (isVirtualDeletion) {
            if (!batch.deleted) {
                Batch b = batchRepository.findByIdForUpdate(batch.id, batch.companyId);
                b.deleted = true;
                batchCache.save(b);
            }
        }
        else {
            workbookService.deleteWorkbook(batch.workbookId, companyUniqueId);
            batchCache.deleteById(batch.id);
        }
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #"+batch.id+" was deleted successfully.", null);
    }

    public BatchData.Status getProcessingResourceStatus(Long batchId, LaunchpadContext context, boolean includeDeleted) {
        return getProcessingResourceStatus(batchId, context.getCompanyId(), includeDeleted);
    }

    public BatchData.Status getProcessingResourceStatus(Long batchId, Long companyUniqueId, boolean includeDeleted) {
        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(companyUniqueId) ||
                (!includeDeleted && batch.deleted)) {
            final String es = "#995.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return new BatchData.Status(es);
        }
        BatchParamsYaml.BatchStatus status = batchService.updateStatus(batch);
        return new BatchData.Status(batchId, status.getStatus(), status.ok);
    }

    public ResourceWithCleanerInfo getBatchProcessingResult(Long batchId, LaunchpadContext context, boolean includeDeleted) throws IOException {
        return getBatchProcessingResult(batchId, context.getCompanyId(), includeDeleted);
    }

    public ResourceWithCleanerInfo getBatchProcessingResult(Long batchId, Long companyUniqueId, boolean includeDeleted) throws IOException {
        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(companyUniqueId) ||
                (!includeDeleted && batch.deleted)) {
            final String es = "#995.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return null;
        }
        ResourceWithCleanerInfo resource = new ResourceWithCleanerInfo();

        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
        resource.toClean.add(resultDir);

        File zipDir = new File(resultDir, "zip");
        //noinspection ResultOfMethodCallIgnored
        zipDir.mkdir();

        BatchStatusProcessor status = batchService.prepareStatusAndData(batch, this::prepareZip, zipDir);

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

    public ResourceWithCleanerInfo getBatchOriginFile(Long batchId) throws IOException {
        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#995.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return null;
        }
        ResourceWithCleanerInfo resource = new ResourceWithCleanerInfo();

        File resultDir = DirUtils.createTempDir("prepare-origin-file-");
        resource.toClean.add(resultDir);

        String originFilename = batchService.getUploadedFilename(batchId);
        File tempFile = File.createTempFile("batch-origin-file-", ".bin", resultDir);

        try {
            binaryDataService.storeBatchOriginFileToFile(batchId, tempFile);
        } catch (BinaryDataNotFoundException e) {
            String msg = "#990.375 Error store data to temp file, data doesn't exist in db, batchId " + batchId +
                    ", file: " + tempFile.getPath();
            log.error(msg);
            return null;
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.setContentDisposition(ContentDisposition.parse("filename*=UTF-8''" + URLEncoder.encode(originFilename, StandardCharsets.UTF_8.toString())));
        resource.entity = new ResponseEntity<>(new FileSystemResource(tempFile), RestUtils.getHeader(httpHeaders, tempFile.length()), HttpStatus.OK);
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
            binaryDataService.storeToFile(taskParamYaml.taskYaml.outputResourceIds.values().iterator().next(), tempFile);
        } catch (BinaryDataNotFoundException e) {
            String msg = "#990.375 Error store data to temp file, data doesn't exist in db, code " +
                    taskParamYaml.taskYaml.outputResourceIds.values().iterator().next() +
                    ", file: " + tempFile.getPath();
            log.error(msg);
            prepareZipData.bs.getGeneralStatus().add(msg,'\n');
            return false;
        }
        return true;
    }


}
