/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.BatchRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
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
@SuppressWarnings({"DuplicatedCode"})
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class BatchTopLevelService {

    private static final String SOURCE_CODE_NOT_FOUND = "Source code wasn't found";
    private static final String ALLOWED_CHARS_IN_ZIP_REGEXP = "^[/\\\\A-Za-z0-9._-]*$";
    private static final Pattern zipCharsPattern = Pattern.compile(ALLOWED_CHARS_IN_ZIP_REGEXP);

    private final SourceCodeCache sourceCodeCache;
    private final ExecContextCache execContextCache;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final BatchCache batchCache;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final ExecContextSyncService execContextSyncService;
    private final BatchHelperService batchHelperService;

    public static final Function<String, Boolean> VALIDATE_ZIP_FUNCTION = BatchTopLevelService::isZipEntityNameOk;

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

    public BatchData.BatchesResult getBatches(Pageable pageable, DispatcherContext context, boolean includeDeleted, boolean filterBatches) {
        return getBatches(pageable, context.getCompanyId(), context.account, includeDeleted, filterBatches);
    }

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

        List<BatchData.BatchExecInfo> items = getBatches(batchIds);
        BatchData.BatchesResult result = new BatchData.BatchesResult();
        result.batches = new PageImpl<>(items, pageable, total);

        return result;
    }

    @Nullable
    public BatchData.BatchExecInfo getBatchExecInfoForDeleting(DispatcherContext context, Long batchId) {
        List<BatchData.BatchExecInfo> items = getBatchExecInfos(List.of(batchId));
        if (items.isEmpty()) {
            return null;
        }
        BatchData.BatchExecInfo batchExecInfo = items.get(0);

        Batch b = batchExecInfo.batch;
        if (context.account.getAccountRoles().hasRole("ROLE_ADMIN")) {
            return b.companyId.equals(context.getCompanyId()) && !b.deleted ? batchExecInfo : null;
        }

        return b.companyId.equals(context.getCompanyId()) && b.accountId.equals(context.account.id) && !b.deleted ? batchExecInfo : null;
    }

    private List<BatchData.BatchExecInfo> getBatches(Page<Long> pageWithBatchIds) {
        List<Long> batchIds = pageWithBatchIds.getContent();
        return getBatchExecInfos(batchIds);
    }

    private List<BatchData.BatchExecInfo> getBatchExecInfos(List<Long> batchIds) {
        List<BatchData.BatchExecInfo> items = new ArrayList<>();
        for (Long batchId : batchIds) {
            Batch batch = batchCache.findById(batchId);
            if (batch == null) {
                continue;
            }
            SourceCodeImpl sourceCode = sourceCodeCache.findById(batch.getSourceCodeId());
            boolean ok = true;
            String uid = SOURCE_CODE_NOT_FOUND;
            if (sourceCode != null) {
                uid = sourceCode.getUid();
            } else {
                if (batch.execState != Enums.BatchExecState.Preparing.code) {
                    ok = false;
                }
            }
            String execStateStr = Enums.BatchExecState.toState(batch.execState).toString();

            String filename;
            boolean execContextDeleted = false;
            ExecContextImpl execContext = execContextCache.findById(batch.execContextId);
            if (execContext==null) {
                filename = "<ExecContext was deleted>";
                execContextDeleted = true;
            }
            else {
                filename = batchHelperService.findUploadedFilenameForBatchId(execContext, Consts.UNKNOWN_FILENAME_IN_BATCH);
            }

            BatchParamsYaml bpy = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.params);
            items.add(new BatchData.BatchExecInfo(
                    batch, uid, execStateStr, batch.execState, ok, filename,
                    S.b(bpy.username) ? "accountId #"+batch.accountId : bpy.username, execContextDeleted ));
        }
        return items;
    }

    public BatchData.UploadingStatus batchUploadFromFile(final MultipartFile file, Long sourceCodeId, final DispatcherContext dispatcherContext) {
        log.info("#981.055 Staring of batchUploadFromFile(), file: {}, size: {}", file.getOriginalFilename(), file.getSize());

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

        final SourceCodeImpl sc = sourceCodeCache.findById(sourceCode.id);
        if (sc==null) {
            return new BatchData.UploadingStatus("#981.165 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        try {
            ExecContextCreatorService.ExecContextCreationResult creationResult = execContextCreatorTopLevelService.createExecContext(sourceCodeId, dispatcherContext);
            if (creationResult.isErrorMessages()) {
                throw new BatchResourceProcessingException("#981.180 Error creating execContext: " + creationResult.getErrorMessagesAsStr());
            }
            final ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(creationResult.execContext.params);
            final BatchData.UploadingStatus uploadingStatus;
            try(InputStream is = file.getInputStream()) {
                uploadingStatus = execContextSyncService.getWithSync(creationResult.execContext.id,
                        () -> batchService.createBatchForFile(
                                is, file.getSize(), originFilename, sc, creationResult.execContext.id, execContextParamsYaml, dispatcherContext));
            }
            return uploadingStatus;
        }
        catch (ExecContextTooManyInstancesException e) {
            String es = S.f("#981.255 Too many instances of SourceCode '%s', max allowed: %d, current count: %d", e.sourceCodeUid, e.max, e.curr);
            log.warn(es);
            BatchData.UploadingStatus uploadingStatus = new BatchData.UploadingStatus();
            uploadingStatus.addInfoMessage(es);
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

        if (isVirtualDeletion) {
            Batch batch = batchCache.findById(batchId);
            if (batch == null || !batch.companyId.equals(companyUniqueId)) {
                final String es = "#981.280 Batch wasn't found, batchId: " + batchId;
                log.info(es);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
            if (batch.deleted) {
                return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #" + batchId + " was deleted successfully.", null);
            }
            return execContextSyncService.getWithSync(execContextId, () ->  batchService.deleteBatchVirtually(execContextId, companyUniqueId, batchId));
        }
        else {
            return execContextSyncService.getWithSync(execContextId, () -> batchService.deleteBatch(execContextId, companyUniqueId, batchId));
        }
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

    public BatchData.Status getBatchProcessingStatus(Long batchId, Long companyUniqueId, boolean includeDeleted) {
        return batchService.getBatchProcessingStatus(batchId, companyUniqueId, includeDeleted);
    }

    @Nullable
    public CleanerInfo getBatchProcessingResultWitTx(Long batchId, Long companyUniqueId, boolean includeDeleted) {
        try {
            return batchService.getBatchProcessingResultWitTx(batchId, companyUniqueId, includeDeleted);
        } catch (Throwable th) {
            String es = S.f("#981.400 Error while getting a result of processing for batch #%d, error: %s", batchId, th.getMessage());
            log.error(es, th);
            return new CleanerInfo(es);
        }
    }

    public CleanerInfo getBatchOriginFile(Long batchId) {
        try {
            return batchService.getBatchOriginFile(batchId);
        } catch (Throwable th) {
            String es = S.f("#981.420 Error while getting an original file for batch #%d, error: %s", batchId, th.getMessage());
            log.error(es, th);
            ExceptionUtils.rethrow(th);
            return null;
        }
    }

}
