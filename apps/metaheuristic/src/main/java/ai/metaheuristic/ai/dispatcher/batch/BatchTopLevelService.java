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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.BatchRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;
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
@SuppressWarnings({"DuplicatedCode", "SpellCheckingInspection"})
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class BatchTopLevelService {

    private static final String ALLOWED_CHARS_IN_ZIP_REGEXP = "^[/\\\\A-Za-z0-9._-]*$";
    private static final Pattern zipCharsPattern = Pattern.compile(ALLOWED_CHARS_IN_ZIP_REGEXP);

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final VariableService variableService;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final BatchCache batchCache;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextCreatorService execContextCreatorService;
    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final ExecContextSyncService execContextSyncService;
    private final BatchHelperService batchHelperService;
    private final ExecContextService execContextService;
    private final ApplicationEventPublisher eventPublisher;

    public static final Function<String, Boolean> VALIDATE_ZIP_FUNCTION = BatchTopLevelService::isZipEntityNameOk;

    @Transactional(readOnly = true)
    public BatchData.ExecStatuses getBatchExecStatuses(DispatcherContext context) {
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

    @Transactional(readOnly = true)
    public BatchData.BatchesResult getBatches(Pageable pageable, DispatcherContext context, boolean includeDeleted, boolean filterBatches) {
        return getBatches(pageable, context.getCompanyId(), context.account, includeDeleted, filterBatches);
    }

    @Transactional(readOnly = true)
    public BatchData.BatchesResult getBatches(Pageable pageable, Long companyUniqueId, @Nullable Account account, boolean includeDeleted, boolean filterBatches) {
        if (filterBatches && account==null) {
            log.warn("#981.020 (filterBatches && account==null)");
            return new BatchData.BatchesResult();
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

        List<BatchData.BatchExecInfo> items = batchService.getBatches(batchIds);
        BatchData.BatchesResult result = new BatchData.BatchesResult();
        result.batches = new PageImpl<>(items, pageable, total);

        return result;
    }

    @Nullable
    public BatchData.BatchExecInfo getBatchExecInfo(DispatcherContext context, Long batchId) {
        return batchService.getBatchExecInfo(context, batchId);
    }

    public BatchData.UploadingStatus batchUploadFromFile(final MultipartFile file, Long sourceCodeId, final DispatcherContext dispatcherContext) {
        String tempFilename = file.getOriginalFilename();
        if (S.b(tempFilename)) {
            return new BatchData.UploadingStatus("#981.040 name of uploaded file is blank");
        }
        // fix for the case when browser sends full path, ie Edge
        final String originFilename = new File(tempFilename).getName();

        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new BatchData.UploadingStatus(
                    "#981.060 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT, XML_EXT)) {
            return new BatchData.UploadingStatus("#981.080 only '.zip', '.xml' files are supported, bad filename: " + originFilename);
        }

        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, dispatcherContext.getCompanyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new BatchData.UploadingStatus(sourceCodesForCompany.getErrorMessagesAsList());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new BatchData.UploadingStatus("#981.100 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        if (!sourceCode.getId().equals(sourceCodeId)) {
            return new BatchData.UploadingStatus("#981.120 Fatal error in configuration of sourceCode, report to developers immediately");
        }
        dispatcherEventService.publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_FILE_UPLOADED, dispatcherContext.getCompanyId(), originFilename, file.getSize(), null, null, dispatcherContext );

        if (file.getSize()==0) {
            return new BatchData.UploadingStatus("#981.140 Empty files aren't supported");
        }

        // TODO 2019-07-06 Do we need to validate the sourceCode here in case that there is another check?
        //  2019-10-28 it's working so left it as is until an issue with this will be found
        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode.id);
        if (sourceCodeValidation.status.status != EnumsApi.SourceCodeValidateStatus.OK ) {
            return new BatchData.UploadingStatus("#981.160 validation of sourceCode was failed, status: " + sourceCodeValidation.status);
        }

        final SourceCodeImpl sc = sourceCodeCache.findById(sourceCode.id);
        if (sc==null) {
            return new BatchData.UploadingStatus("#981.165 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        try {
            ExecContextCreatorService.ExecContextCreationResult creationResult = execContextCreatorService.createExecContext(sourceCodeId, dispatcherContext);
            if (creationResult.isErrorMessages()) {
                throw new BatchResourceProcessingException("#981.180 Error creating execContext: " + creationResult.getErrorMessagesAsStr());
            }
            final ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(creationResult.execContext.params);
            final BatchData.UploadingStatus uploadingStatus;
            try(InputStream is = file.getInputStream()) {
                uploadingStatus = execContextSyncService.getWithSync(creationResult.execContext.id,
                        () -> batchService.createBatchForFile(is, file.getSize(), originFilename, sourceCode.id, creationResult.execContext.id, execContextParamsYaml, dispatcherContext));
            }
            eventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(sourceCode.id, dispatcherContext.getCompanyId(), true));
            return uploadingStatus;
        }
        catch (Throwable th) {
            String es = "#981.260 can't load file, error: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new BatchData.UploadingStatus(es);
        }
    }

    public OperationStatusRest processBatchDeleteCommit(Long batchId, DispatcherContext context, boolean isVirtualDeletion) {
        try {
            return processBatchDeleteCommit(batchId, context.getCompanyId(), isVirtualDeletion);
        } catch (Throwable th) {
            final String es = "Error while deleting batch #" + batchId;
            log.error(es, th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
    }

    public OperationStatusRest processBatchDeleteCommit(Long batchId, Long companyUniqueId, boolean isVirtualDeletion) {
        Long execContextId = batchRepository.getExecContextId(batchId);
        if (execContextId == null) {
            final String es = "#981.280 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        return execContextSyncService.getWithSync(execContextId, () -> batchService.deleteBatch(companyUniqueId, isVirtualDeletion, batchId));
    }

    public BatchData.BulkOperations processBatchBulkDeleteCommit(String batchIdsStr, Long companyUniqueId, boolean isVirtualDeletion) {
        BatchData.BulkOperations bulkOperations = new BatchData.BulkOperations();
        String[] batchIds = StringUtils.split(batchIdsStr, ", ");
        for (String batchIdStr : batchIds) {
            Long batchId = Long.parseLong(batchIdStr);
            OperationStatusRest statusRest = processBatchDeleteCommit(batchId, companyUniqueId, isVirtualDeletion);
            bulkOperations.operations.add( new BatchData.BulkOperation(batchId, statusRest));
        }
        return bulkOperations;
    }

    @Transactional(readOnly = true)
    public BatchData.Status getBatchProcessingStatus(Long batchId, Long companyUniqueId, boolean includeDeleted) {
        try {
            Batch batch = batchCache.findById(batchId);
            if (batch == null) {
                final String es = "#981.440 Batch wasn't found, batchId: " + batchId;
                log.warn(es);
                return new BatchData.Status(es);
            }

            CleanerInfo cleanerInfo = getBatchProcessingResultInternal(batch, companyUniqueId, includeDeleted, "batch-status");
            try {
                if (cleanerInfo.entity==null) {
                    final String es = "#981.305 Batch wasn't found, batchId: " + batchId;
                    log.warn(es);
                    return new BatchData.Status(es);
                }

                AbstractResource body = cleanerInfo.entity.getBody();
                if (body == null) {
                    final String es = "#981.320 Batch wasn't found, batchId: " + batchId;
                    log.warn(es);
                    return new BatchData.Status(es);
                }

                String status = IOUtils.toString(body.getInputStream(), StandardCharsets.UTF_8);
                return new BatchData.Status(batchId, status, true);
            }
            finally {
                DirUtils.deleteFiles(cleanerInfo.toClean);
            }
        } catch (IOException e) {
            final String es = "#981.340 System error: " + batchId;
            log.warn(es);
            return new BatchData.Status(es);
        }
    }

    @Nullable
    @Transactional(readOnly = true)
    public CleanerInfo getBatchProcessingResultWitTx(Long batchId, Long companyUniqueId, boolean includeDeleted) throws IOException {
        return getBatchProcessingResult(batchId, companyUniqueId, includeDeleted);
    }

    private CleanerInfo getBatchProcessingResult(Long batchId, Long companyUniqueId, boolean includeDeleted) throws IOException {
        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#981.440 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return new CleanerInfo();
        }
        return getBatchProcessingResultInternal(batch, companyUniqueId, includeDeleted, "batch-result");
    }

    private CleanerInfo getBatchProcessingResultInternal(Batch batch, Long companyUniqueId, boolean includeDeleted, String variableType) throws IOException {
        return getVariable(batch, companyUniqueId, includeDeleted, (scpy)-> {
            List<SourceCodeParamsYaml.Variable> vars = SourceCodeService.findVariableByType(scpy, variableType);
            if (vars.isEmpty()) {
                final String es = "#981.360 variable with type '"+variableType+"' wasn't found";
                log.warn(es);
                return null;
            }
            if (vars.size()>1) {
                final String es = "#981.380 too many variables with type '"+variableType+"'. " + vars;
                log.warn(es);
                return null;
            }
            return vars.get(0).name;
        }, (execContextId, scpy) -> {
            SimpleVariable inputVariable = variableService.getVariableAsSimple(execContextId, scpy.source.variables.startInputAs);
            if (inputVariable==null) {
                final String es = "#981.400 Can't find a start input variable '"+scpy.source.variables.startInputAs+"'";
                log.warn(es);
                return null;

            }
            String filename = StrUtils.getName(inputVariable.filename) + BatchService.getActualExtension(scpy, globals.defaultResultFileExtension);
            return filename;
        });
    }

    @Transactional(readOnly = true)
    public CleanerInfo getBatchOriginFile(Long batchId) {
        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#981.440 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return new CleanerInfo();
        }
        ExecContextImpl execContext = execContextService.findById(batch.execContextId);
        if (execContext==null) {
            return new CleanerInfo();
        }

        return getVariable(batch, null, true, (scpy)-> {
            String variableName = scpy.source.variables.startInputAs;
            if (S.b(variableName)) {
                final String es = "#981.420 a start input variable '" + scpy.source.variables.startInputAs +"' wasn't found";
                log.warn(es);
                return null;
            }
            return variableName;
        }, (execContextId, scpy) -> batchHelperService.findUploadedFilenameForBatchId(execContext, "origin-file.zip"));
    }

    private CleanerInfo getVariable(
            Batch batch, @Nullable Long companyUniqueId, boolean includeDeleted,
            Function<SourceCodeParamsYaml, String> variableSelector, BiFunction<Long, SourceCodeParamsYaml, String> outputFilenameFunction) {

        CleanerInfo resource = new CleanerInfo();
        try {
            File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
            resource.toClean.add(resultDir);

            File zipDir = new File(resultDir, "zip");
            //noinspection ResultOfMethodCallIgnored
            zipDir.mkdir();

            SourceCodeImpl sc = sourceCodeCache.findById(batch.sourceCodeId);
            if (sc==null) {
                final String es = "#981.460 SourceCode wasn't found, sourceCodeId: " + batch.sourceCodeId;
                log.warn(es);
                return resource;
            }
            SourceCodeStoredParamsYaml scspy = sc.getSourceCodeStoredParamsYaml();
            SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
            String resultBatchVariable = variableSelector.apply(scpy);

            SimpleVariable variable = variableService.getVariableAsSimple(batch.execContextId, resultBatchVariable);
            if (variable==null) {
                final String es = "#981.480 Can't find variable '"+resultBatchVariable+"'";
                log.warn(es);
                return resource;
            }

            String filename = variable.filename;
            if (S.b(filename)) {
                filename = outputFilenameFunction.apply(batch.execContextId, scpy);
                if (S.b(filename)) {
                    final String es = "#981.500 Can't find filename for file";
                    log.warn(es);
                    return resource;
                }
            }

            File zipFile = new File(resultDir, Consts.RESULT_ZIP);
            variableService.storeToFile(variable.id, zipFile);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            // https://stackoverflow.com/questions/93551/how-to-encode-the-filename-parameter-of-content-disposition-header-in-http
            httpHeaders.setContentDisposition(ContentDisposition.parse(
                    "filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())));
            resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile), RestUtils.getHeader(httpHeaders, zipFile.length()), HttpStatus.OK);
            return resource;
        } catch (VariableDataNotFoundException e) {
            log.error("Variable #{}, context: {}, {}", e.variableId, e.context, e.getMessage());
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        } catch (Throwable th) {
            log.error("#981.515 General error", th);
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        }
    }

    @Nullable
    private String getVariableAsString(
            Long batchId, @Nullable Long companyUniqueId, boolean includeDeleted,
            Function<SourceCodeParamsYaml, String> variableSelector) {
        Batch batch = batchCache.findById(batchId);
        if (batch == null || (companyUniqueId!=null && !batch.companyId.equals(companyUniqueId)) ||
                (!includeDeleted && batch.deleted)) {
            final String es = "#981.520 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return null;
        }

        SourceCodeImpl sc = sourceCodeCache.findById(batch.sourceCodeId);
        if (sc==null) {
            final String es = "#981.540 SourceCode wasn't found, sourceCodeId: " + batch.sourceCodeId;
            log.warn(es);
            return null;
        }
        SourceCodeStoredParamsYaml scspy = sc.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        String resultBatchVariable = variableSelector.apply(scpy);

        SimpleVariable variable = variableService.getVariableAsSimple(batch.execContextId, resultBatchVariable);
        if (variable==null) {
            final String es = "#981.560 Can't find variable '"+resultBatchVariable+"'";
            log.warn(es);
            return null;
        }

        String s = variableService.getVariableDataAsString(variable.id);
        return s;
    }

}
