/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.BatchRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.PageUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

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
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BatchTopLevelService {

    private static final String SOURCE_CODE_NOT_FOUND = "Source code wasn't found";
    private static final Pattern ZIP_CHARS_PATTERN = Pattern.compile("^[/\\\\A-Za-z0-9._-]*$");

    private final SourceCodeCache sourceCodeCache;
    private final ExecContextCache execContextCache;
    private final BatchRepository batchRepository;
    private final BatchTxService batchTxService;
    private final BatchCache batchCache;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final BatchHelperService batchHelperService;
    private final VariableTxService variableTxService;
    private final ExecContextGraphService execContextGraphService;

    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_FUNCTION = BatchTopLevelService::isZipEntityNameOk;
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_ENTRY_SIZE_FUNCTION = BatchTopLevelService::isZipEntitySizeOk;

    public BatchData.ExecStatuses getBatchExecStatuses(DispatcherContext context) {
        BatchData.ExecStatuses execStatuses = new BatchData.ExecStatuses(batchRepository.getBatchExecStatuses(context.getCompanyId()));
        return execStatuses;
    }

    public void deleteOrphanOrObsoletedBatches(Set<Long> batchIds) {
        for (Long batchId : batchIds) {
            try {
                batchTxService.deleteBatch(batchId);
                log.info("210.140 Orphan or obsoleted batch #{} was deleted", batchId);
            }
            catch (Throwable th) {
                log.error("batchService.deleteBatch("+batchId+")", th);
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class FileWithMapping {
        public Path file;
        public String originName;
    }

    private static ZipUtils.ValidationResult isZipEntityNameOk(ZipEntry zipEntry) {
        Matcher m = ZIP_CHARS_PATTERN.matcher(zipEntry.getName());
        return m.matches() ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult("981.010 Wrong name of file in zip file. Name: "+zipEntry.getName());
    }

    private static ZipUtils.ValidationResult isZipEntitySizeOk(ZipEntry zipEntry) {
        if (zipEntry.isDirectory()) {
            return ZipUtils.VALIDATION_RESULT_OK;
        }
        return zipEntry.getSize()>0 ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult(
                "981.013 File "+zipEntry.getName()+" has a zero length.");
    }

    public BatchData.BatchesResult getBatches(Pageable pageable, DispatcherContext context, boolean includeDeleted, boolean filterBatches) {
        return getBatches(pageable, context.getCompanyId(), context.account, includeDeleted, filterBatches);
    }

    public BatchData.BatchesResult getBatches(Pageable pageable, Long companyUniqueId, @Nullable Account account, boolean includeDeleted, boolean filterBatches) {
        if (filterBatches && account==null) {
            log.warn("981.020 (filterBatches && account==null)");
            return new BatchData.BatchesResult();
        }
        pageable = PageUtils.fixPageSize(20, pageable);
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
            ExecContextImpl execContext = execContextCache.findById(batch.execContextId, true);
            if (execContext==null) {
                filename = "<ExecContext was deleted>";
                execContextDeleted = true;
            }
            else {
                filename = batchHelperService.findUploadedFilenameForBatchId(execContext.id, execContext.getExecContextParamsYaml(), Consts.UNKNOWN_FILENAME_IN_BATCH);
            }

            BatchParamsYaml bpy = BatchParamsYamlUtils.BASE_YAML_UTILS.to(batch.params);
            items.add(new BatchData.BatchExecInfo(
                    batch, uid, execStateStr, batch.execState, ok, filename,
                    S.b(bpy.username) ? "accountId #"+batch.accountId : bpy.username, execContextDeleted ));
        }
        return items;
    }

    public BatchData.UploadingStatus batchUploadFromFile(final MultipartFile file, Long sourceCodeId, final DispatcherContext dispatcherContext) {
        if (Consts.ID_1.equals(dispatcherContext.getCompanyId())) {
            return new BatchData.UploadingStatus("981.030 Batch can't be created in company #1");
        }
        if (file.getSize()==0) {
            return new BatchData.UploadingStatus("981.035 Can't create a new batch because uploaded file has a zero length");
        }

        log.info("981.055 Staring of batchUploadFromFile(), file: {}, size: {}", file.getOriginalFilename(), file.getSize());

        String tempFilename = file.getOriginalFilename();
        if (S.b(tempFilename)) {
            return new BatchData.UploadingStatus("981.040 name of uploaded file is blank");
        }
        // fix for the case when browser sends a full path, ie Edge
        final String originFilename = Path.of(tempFilename).getFileName().toString();

        String extTemp = StrUtils.getExtension(originFilename);
        if (extTemp==null) {
            return new BatchData.UploadingStatus(
                    "981.060 file without extension, bad filename: " + originFilename);
        }
        String ext = extTemp.toLowerCase();
        if (!StringUtils.equalsAny(ext, ZIP_EXT, XML_EXT)) {
            return new BatchData.UploadingStatus("981.080 only '.zip', '.xml' files are supported, bad filename: " + originFilename);
        }

        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, dispatcherContext.getCompanyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new BatchData.UploadingStatus(sourceCodesForCompany.getErrorMessagesAsList());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new BatchData.UploadingStatus("981.100 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        if (!sourceCode.getId().equals(sourceCodeId)) {
            return new BatchData.UploadingStatus("981.120 Fatal error in configuration of sourceCode, report to developers immediately");
        }
        Path tempDir = DirUtils.createMhTempPath("batch-processing-");
        if (tempDir==null) {
            return new BatchData.UploadingStatus("981.122 Can't create temporary directory. Batch file can't be processed");
        }
        try {
            // we need to create a temporary file because org.apache.commons.compress.archivers.zip.ZipFile
            // doesn't work with abstract InputStream
            Path tempFile;
            try {
                tempFile = Files.createTempFile(tempDir, "mh-temp-file-for-processing-", Consts.BIN_EXT);
                try (InputStream is = file.getInputStream()) {
                    DirUtils.copy(is, tempFile);
                }
                if (file.getSize()!=Files.size(tempFile)) {
                    return new BatchData.UploadingStatus("981.125 System error while preparing data. The sizes of files are different");
                }
            } catch (IOException e) {
                return new BatchData.UploadingStatus("981.140 Can't create a new temp file, " + e);
            }

            if (ext.equals(ZIP_EXT)) {
                List<String> errors = ZipUtils.validate(tempFile, VALIDATE_ZIP_ENTRY_SIZE_FUNCTION);
                if (!errors.isEmpty()) {
                    final BatchData.UploadingStatus status = new BatchData.UploadingStatus("981.144 Batch can't be created because of following errors:");
                    status.addErrorMessages(errors);
                    return status;
                }
            }

            dispatcherEventService.publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_FILE_UPLOADED, dispatcherContext.getCompanyId(), originFilename, file.getSize(), null, null, dispatcherContext );

            final SourceCodeImpl sc = sourceCodeCache.findById(sourceCode.id);
            if (sc==null) {
                return new BatchData.UploadingStatus("981.165 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
            }
            ExecContextData.UserExecContext context = dispatcherContext.asUserExecContext();

            // we must postpone a creation of tasks until input variable for SourceCode/ExecContext will be initialized
            ExecContextCreatorService.ExecContextCreationResult creationResult = execContextCreatorTopLevelService.createExecContextAndStart(sourceCodeId, context, false, null);
            if (creationResult.isErrorMessages()) {
                return new BatchData.UploadingStatus("981.180 Error creating execContext: " + creationResult.getErrorMessagesAsStr());
            }
            boolean verifyGraph = execContextGraphService.verifyGraph(creationResult.execContext.execContextGraphId);
            if (!verifyGraph) {
                return new BatchData.UploadingStatus("981.185 Graph is broken");
            }

            final ExecContextParamsYaml execContextParamsYaml = creationResult.execContext.getExecContextParamsYaml();
            ExecContextParamsYaml.Variable variable = execContextParamsYaml.variables.inputs.get(0);
            try (InputStream is = Files.newInputStream(tempFile)) {
                variableTxService.createInitializedTx(is, file.getSize(), variable.name, originFilename, creationResult.execContext.id, Consts.TOP_LEVEL_CONTEXT_ID, EnumsApi.VariableType.zip);
            }
            final BatchData.UploadingStatus uploadingStatus;
            uploadingStatus = ExecContextSyncService.getWithSync(creationResult.execContext.id, ()->
                    ExecContextGraphSyncService.getWithSync(creationResult.execContext.execContextGraphId, ()->
                            ExecContextTaskStateSyncService.getWithSync(creationResult.execContext.execContextTaskStateId, ()->
                                    batchTxService.createBatchForFile(
                                            sc, creationResult.execContext.id, dispatcherContext))));

            verifyGraph = execContextGraphService.verifyGraph(creationResult.execContext.execContextGraphId);
            if (!verifyGraph) {
                return new BatchData.UploadingStatus("981.200 Graph is broken");
            }
            return uploadingStatus;
        }
        catch (ExecContextTooManyInstancesException e) {
            String es = S.f("981.255 Too many instances of SourceCode '%s', max allowed: %d, current count: %d", e.sourceCodeUid, e.max, e.curr);
            log.warn(es);
            BatchData.UploadingStatus uploadingStatus = new BatchData.UploadingStatus();
            uploadingStatus.addInfoMessage(es);
            return uploadingStatus;
        }
        catch (Throwable th) {
            String es = "981.260 can't load file, error: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new BatchData.UploadingStatus(es);
        }
        finally {
            DirUtils.deletePathAsync(tempDir);
        }
    }

    public OperationStatusRest processBatchDeleteCommit(Long batchId, DispatcherContext context, boolean isVirtualDeletion) {
        try {
            return processBatchDeleteCommit(batchId, context.getCompanyId(), isVirtualDeletion);
        } catch (Throwable th) {
            final String es = "981.270 Error while deleting batch #" + batchId;
            log.error(es, th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
    }

    public OperationStatusRest processBatchDeleteCommit(Long batchId, Long companyUniqueId, boolean isVirtualDeletion) {
        Long execContextId = batchRepository.getExecContextId(batchId);
        if (execContextId == null) {
            final String es = "981.280 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        if (isVirtualDeletion) {
            Batch batch = batchCache.findById(batchId);
            if (batch == null || !batch.companyId.equals(companyUniqueId)) {
                final String es = "981.280 Batch wasn't found, batchId: " + batchId;
                log.info(es);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
            if (batch.deleted) {
                return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #" + batchId + " was deleted successfully.", null);
            }
            return ExecContextSyncService.getWithSync(execContextId, ()-> batchTxService.deleteBatchVirtually(execContextId, companyUniqueId, batchId));
        }
        else {
            return ExecContextSyncService.getWithSync(execContextId, ()-> batchTxService.deleteBatch(execContextId, companyUniqueId, batchId));
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
        return batchTxService.getBatchProcessingStatus(batchId, companyUniqueId, includeDeleted);
    }

    @Nullable
    public CleanerInfo getBatchProcessingResul(Long batchId, Long companyUniqueId, boolean includeDeleted) {
        try {
            return batchTxService.getBatchProcessingResultWitTx(batchId, companyUniqueId, includeDeleted);
        } catch (Throwable th) {
            String es = S.f("981.400 Error while getting a result of processing for batch #%d, error: %s", batchId, th.getMessage());
            log.error(es, th);
            return new CleanerInfo(es);
        }
    }

    @Nullable
    public CleanerInfo getBatchOriginFile(Long batchId) {
        try {
            return batchTxService.getBatchOriginFile(batchId);
        } catch (Throwable th) {
            String es = S.f("981.420 Error while getting an original file for batch #%d, error: %s", batchId, th.getMessage());
            log.error(es, th);
            ExceptionUtils.rethrow(th);
            return null;
        }
    }

}
