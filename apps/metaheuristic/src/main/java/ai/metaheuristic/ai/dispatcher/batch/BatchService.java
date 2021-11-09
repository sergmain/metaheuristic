/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.batch.data.BatchStatusProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.TaskQueueCleanByExecContextIdEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.repositories.BatchRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYaml;
import ai.metaheuristic.ai.yaml.batch.BatchParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
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
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 6/1/2019
 * Time: 4:18 PM
 */
@SuppressWarnings({"UnusedReturnValue", "DuplicatedCode"})
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchService {

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final BatchCache batchCache;
    private final BatchRepository batchRepository;
    private final VariableService variableService;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextService execContextService;
    private final ExecContextFSM execContextFSM;
    private final ExecContextCache execContextCache;
    private final BatchHelperService batchHelperService;
    private final ExecContextTaskProducingService execContextTaskProducingService;
    private final ApplicationEventPublisher eventPublisher;

    public static String getActualExtension(SourceCodeStoredParamsYaml scspy, String defaultResultFileExtension) {
        return getActualExtension(SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source), defaultResultFileExtension);
    }

    private static String getActualExtension(SourceCodeParamsYaml scpy, String defaultResultFileExtension) {
        final String ext = MetaUtils.getValue(scpy.source.metas, ConstsApi.META_MH_RESULT_FILE_EXTENSION);

        return S.b(ext)
                ? (StringUtils.isNotBlank(defaultResultFileExtension) ? defaultResultFileExtension : ".bin")
                : ext;
    }

    private static void changeStateToPreparing(Batch b) {
            if (b.execState != Enums.BatchExecState.Unknown.code && b.execState != Enums.BatchExecState.Stored.code &&
                    b.execState != Enums.BatchExecState.Preparing.code) {
                throw new IllegalStateException("#990.020 Can't change state to Preparing, " +
                        "current state: " + Enums.BatchExecState.toState(b.execState));
            }
            if (b.execState == Enums.BatchExecState.Preparing.code) {
                return;
            }
            b.execState = Enums.BatchExecState.Preparing.code;
    }

    private void changeStateToProcessing(Batch b) {
        if (b.execState != Enums.BatchExecState.Preparing.code && b.execState != Enums.BatchExecState.Processing.code) {
            throw new IllegalStateException("#990.040 Can't change state to Finished, " +
                    "current state: " + Enums.BatchExecState.toState(b.execState));
        }
        if (b.execState == Enums.BatchExecState.Processing.code) {
            return;
        }
        b.execState = Enums.BatchExecState.Processing.code;
        dispatcherEventService.publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_PROCESSING_STARTED, null, null, null, b.id, null, null );
    }

    @Transactional
    public void updateBatchStatuses() {
        List<Long> batchIds = batchRepository.findAllUnfinishedAsId();
        for (Long batchId : batchIds) {
            Batch b = batchCache.findById(batchId);
            if (b==null) {
                log.warn("#990.050 batch wasn't found {}", batchId);
                continue;
            }
            ExecContextImpl ec = execContextCache.findById(b.execContextId);
            if (ec==null) {
                continue;
            }
            if (!EnumsApi.ExecContextState.isFinishedState(ec.state)) {
                continue;
            }
            if (b.execState != Enums.BatchExecState.Processing.code && b.execState != Enums.BatchExecState.Finished.code) {
                log.warn("#990.060 Can't change state to Finished, current state: {}", Enums.BatchExecState.toState(b.execState));
                continue;
            }
            if (b.execState == Enums.BatchExecState.Finished.code) {
                continue;
            }
            b.execState = Enums.BatchExecState.Finished.code;
            batchCache.save(b);
            dispatcherEventService.publishEventBatchFinished(batchId);
        }
    }

    @Data
    @AllArgsConstructor
    public static class PrepareZipData {
        public BatchStatusProcessor bs;
        public Task task;
        public File zipDir;
        public String mainDocument;
        public Long execContextId;
    }

    @Transactional
    public BatchData.UploadingStatus createBatchForFile(
            SourceCodeImpl sourceCode, Long execContextId,
            ExecContextParamsYaml execContextParamsYaml,
            final DispatcherContext dispatcherContext) {

        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            return new BatchData.UploadingStatus("#981.205 ExecContext was lost");
        }

        Batch b = createBatch(sourceCode, execContextId, dispatcherContext);

        changeStateToPreparing(b);

        // start producing new tasks
        OperationStatusRest operationStatus = execContextFSM.execContextTargetState(execContext, EnumsApi.ExecContextState.PRODUCING, dispatcherContext.getCompanyId());

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        SourceCodeApiData.TaskProducingResultComplex result = execContextTaskProducingService.produceAndStartAllTasks(sourceCode, execContext, execContextParamsYaml);

        if (result.sourceCodeValidationResult.status!= EnumsApi.SourceCodeValidateStatus.OK) {
            throw new BatchResourceProcessingException(result.sourceCodeValidationResult.error);
        }

        if (result.taskProducingStatus!= EnumsApi.TaskProducingStatus.OK) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }

        changeStateToProcessing(b);

        BatchData.UploadingStatus uploadingStatus = new BatchData.UploadingStatus(b.id, execContextId);
        return uploadingStatus;
    }

    private Batch createBatch(SourceCodeImpl sourceCode, Long execContextId, DispatcherContext dispatcherContext) {
        Batch b = new Batch(sourceCode.id, execContextId, Enums.BatchExecState.Stored,
                dispatcherContext.getAccountId(), dispatcherContext.getCompanyId());

        BatchParamsYaml bpy = new BatchParamsYaml();
        bpy.username = dispatcherContext.account.username;
        b.params = BatchParamsYamlUtils.BASE_YAML_UTILS.toString(bpy);
        b = batchCache.save(b);

        dispatcherEventService.publishBatchEvent(
                EnumsApi.DispatcherEventType.BATCH_CREATED, dispatcherContext.getCompanyId(),
                sourceCode.uid, null, b.id, execContextId, dispatcherContext );
        return b;
    }

    @Transactional
    public OperationStatusRest deleteBatchVirtually(Long execContextId, Long companyUniqueId, Long batchId) {
        ExecContextSyncService.checkWriteLockPresent(execContextId);

        Batch batch = batchCache.findById(batchId);
        if (batch == null || !batch.companyId.equals(companyUniqueId)) {
            final String es = "#981.280 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        if (!batch.deleted) {
            ExecContextImpl execContext = execContextCache.findById(batch.execContextId);
            if (execContext!=null) {
                execContextFSM.toFinished(execContext);
            }

            Batch b = batchRepository.findByIdForUpdate(batch.id, batch.companyId);
            b.deleted = true;
            batchCache.save(b);
        }
        eventPublisher.publishEvent(new TaskQueueCleanByExecContextIdEvent(execContextId));

        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #" + batchId + " was deleted successfully.", null);
    }

    @Transactional
    public OperationStatusRest deleteBatch(Long execContextId, Long companyUniqueId, Long batchId) {
        ExecContextSyncService.checkWriteLockPresent(execContextId);

        execContextService.deleteExecContext(execContextId, companyUniqueId);
        batchCache.deleteById(batchId);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #" + batchId + " was deleted successfully.", null);
    }

    @Transactional
    public void deleteBatch(Long batchId) {
        batchCache.deleteById(batchId);
    }

    @Nullable
    @Transactional(readOnly = true)
    public CleanerInfo getBatchProcessingResultWitTx(Long batchId, Long companyUniqueId, boolean includeDeleted) {
        return getBatchProcessingResult(batchId, companyUniqueId, includeDeleted);
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
        } catch (Throwable th) {
            String es = S.f("#981.380 Error while getting status for batch #%d, error: %s", batchId, th.getMessage());
            log.warn(es, th);
            return new BatchData.Status(es);
        }
    }

    @Transactional(readOnly = true)
    public CleanerInfo getBatchOriginFile(Long batchId) {
        final Batch batch = batchCache.findById(batchId);
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
            if (scpy.source.variables.inputs.size()!=1) {
                final String es = "#981.410 expected only one input variable in execContext but actual count: " + scpy.source.variables.inputs.size();
                log.warn(es);
                return null;
            }
            String variableName = scpy.source.variables.inputs.get(0).name;
            if (S.b(variableName)) {
                final String es = "#981.420 input variable in execContext #"+batch.execContextId+" is empty";
                log.warn(es);
                return null;
            }
            return variableName;
        }, (execContextId, scpy) -> batchHelperService.findUploadedFilenameForBatchId(execContext.id, execContext.getExecContextParamsYaml(), "origin-file.zip"));
    }

    public CleanerInfo getVariable(
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

    private CleanerInfo getBatchProcessingResult(Long batchId, Long companyUniqueId, boolean includeDeleted) {
        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#981.520 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return new CleanerInfo();
        }
        return getBatchProcessingResultInternal(batch, companyUniqueId, includeDeleted, "batch-result");
    }

    private CleanerInfo getBatchProcessingResultInternal(Batch batch, Long companyUniqueId, boolean includeDeleted, String variableType) {
        return getVariable(batch, companyUniqueId, includeDeleted, (scpy)-> {
            List<SourceCodeParamsYaml.Variable> vars = SourceCodeService.findVariableByType(scpy, variableType);
            if (vars.isEmpty()) {
                final String es = "#981.540 variable with type '"+variableType+"' wasn't found";
                log.warn(es);
                return null;
            }
            if (vars.size()>1) {
                final String es = "#981.560 too many variables with type '"+variableType+"'. " + vars;
                log.warn(es);
                return null;
            }
            return vars.get(0).name;
        }, (execContextId, scpy) -> {
            if (scpy.source.variables.inputs.size()!=1) {
                final String es = "#981.580 expected only one input variable in execContext but actual count: " + scpy.source.variables.inputs.size();
                log.warn(es);
                return null;
            }
            String variableName = scpy.source.variables.inputs.get(0).name;
            if (S.b(variableName)) {
                final String es = "#981.600 input variable in execContext #"+batch.execContextId+" is empty";
                log.warn(es);
                return null;
            }

            SimpleVariable inputVariable = variableService.getVariableAsSimple(execContextId, variableName);
            if (inputVariable==null) {
                final String es = "#981.620 Can't find a start input variable '"+variableName+"'";
                log.warn(es);
                return null;

            }
            String filename = StrUtils.getName(inputVariable.filename) + BatchService.getActualExtension(scpy, globals.dispatcher.defaultResultFileExtension);
            return filename;
        });
    }


}
