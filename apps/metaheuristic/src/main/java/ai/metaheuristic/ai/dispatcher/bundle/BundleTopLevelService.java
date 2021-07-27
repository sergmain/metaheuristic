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

package ai.metaheuristic.ai.dispatcher.bundle;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.batch.BatchHelperService;
import ai.metaheuristic.ai.dispatcher.batch.BatchService;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.data.BundleData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.BatchRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import static ai.metaheuristic.ai.Consts.XML_EXT;
import static ai.metaheuristic.ai.Consts.ZIP_EXT;

/**
 * @author Serge
 * Date: 7/26/2021
 * Time: 10:46 PM
 */
@SuppressWarnings({"DuplicatedCode"})
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class BundleTopLevelService {

    private static final Pattern ZIP_CHARS_PATTERN = Pattern.compile("^[/\\\\A-Za-z0-9._-]*$");
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_FUNCTION = BundleTopLevelService::isZipEntityNameOk;
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_ENTRY_SIZE_FUNCTION = BundleTopLevelService::isZipEntitySizeOk;

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
    private final ExecContextGraphSyncService execContextGraphSyncService;
    private final ExecContextTaskStateSyncService execContextTaskStateSyncService;
    private final ExecContextVariableService execContextVariableService;

    public BundleData.UploadingStatus uploadFromFile(final MultipartFile file, Long sourceCodeId, final DispatcherContext dispatcherContext) {
        if (Consts.ID_1.equals(dispatcherContext.getCompanyId())) {
            return new BundleData.UploadingStatus("#981.030 Batch can't be created in company #1");
        }
        if (file.getSize()==0) {
            return new BundleData.UploadingStatus("#981.035 Can't create a new batch because uploaded file has a zero length");
        }

        log.info("#981.055 Staring of batchUploadFromFile(), file: {}, size: {}", file.getOriginalFilename(), file.getSize());

        String tempFilename = file.getOriginalFilename();
        if (S.b(tempFilename)) {
            return new BundleData.UploadingStatus("#981.040 name of uploaded file is blank");
        }
        // fix for the case when browser sends a full path, ie Edge
        final String originFilename = new File(tempFilename).getName();

        String extTemp = StrUtils.getExtension(originFilename);
        if (extTemp==null) {
            return new BundleData.UploadingStatus(
                    "#981.060 file without extension, bad filename: " + originFilename);
        }
        String ext = extTemp.toLowerCase();
        if (!StringUtils.equalsAny(ext, ZIP_EXT, XML_EXT)) {
            return new BundleData.UploadingStatus("#981.080 only '.zip', '.xml' files are supported, bad filename: " + originFilename);
        }

        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeSelectorService.getSourceCodeById(sourceCodeId, dispatcherContext.getCompanyId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new BundleData.UploadingStatus(sourceCodesForCompany.getErrorMessagesAsList());
        }
        SourceCodeImpl sourceCode = sourceCodesForCompany.items.isEmpty() ? null : (SourceCodeImpl) sourceCodesForCompany.items.get(0);
        if (sourceCode==null) {
            return new BundleData.UploadingStatus("#981.100 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        if (!sourceCode.getId().equals(sourceCodeId)) {
            return new BundleData.UploadingStatus("#981.120 Fatal error in configuration of sourceCode, report to developers immediately");
        }
        File tempFile;
        try {
            // TODO 2021.03.13 add a support of
            //  CleanerInfo resource = new CleanerInfo();
            tempFile = File.createTempFile("mh-temp-file-for-checking integrity-", ".bin");
            file.transferTo(tempFile);
            if (file.getSize()!=tempFile.length()) {
                return new BundleData.UploadingStatus("#981.125 System error while preparing data. The sizes of files are different");
            }
        } catch (IOException e) {
            return new BundleData.UploadingStatus("#981.140 Can't create a new temp file");
        }

        if (ext.equals(ZIP_EXT)) {
            List<String> errors = ZipUtils.validate(tempFile, VALIDATE_ZIP_ENTRY_SIZE_FUNCTION);
            if (!errors.isEmpty()) {
                final BundleData.UploadingStatus status = new BundleData.UploadingStatus("#981.144 Batch can't be created because of following errors:");
                status.addErrorMessages(errors);
                return status;
            }
        }

        dispatcherEventService.publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_FILE_UPLOADED, dispatcherContext.getCompanyId(), originFilename, file.getSize(), null, null, dispatcherContext );

        final SourceCodeImpl sc = sourceCodeCache.findById(sourceCode.id);
        if (sc==null) {
            return new BundleData.UploadingStatus("#981.165 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        try {
            ExecContextCreatorService.ExecContextCreationResult creationResult = execContextCreatorTopLevelService.createExecContext(sourceCodeId, dispatcherContext);
            if (creationResult.isErrorMessages()) {
                throw new BatchResourceProcessingException("#981.180 Error creating execContext: " + creationResult.getErrorMessagesAsStr());
            }
            final ExecContextParamsYaml execContextParamsYaml = creationResult.execContext.getExecContextParamsYaml();
            try(InputStream is = new FileInputStream(tempFile)) {
                execContextVariableService.initInputVariable(is, file.getSize(), originFilename, creationResult.execContext.id, execContextParamsYaml, 0);
            }
/*
            final BundleData.UploadingStatus uploadingStatus;
            uploadingStatus = execContextSyncService.getWithSync(creationResult.execContext.id, ()->
                    execContextGraphSyncService.getWithSync(creationResult.execContext.execContextGraphId, ()->
                            execContextTaskStateSyncService.getWithSync(creationResult.execContext.execContextTaskStateId, ()->
                                    batchService.createBatchForFile(
                                            sc, creationResult.execContext.id, execContextParamsYaml, dispatcherContext))));
            return uploadingStatus;
*/
            return new BundleData.UploadingStatus();
        }
        catch (ExecContextTooManyInstancesException e) {
            String es = S.f("#981.255 Too many instances of SourceCode '%s', max allowed: %d, current count: %d", e.sourceCodeUid, e.max, e.curr);
            log.warn(es);
            BundleData.UploadingStatus uploadingStatus = new BundleData.UploadingStatus();
            uploadingStatus.addInfoMessage(es);
            return uploadingStatus;
        }
        catch (Throwable th) {
            String es = "#981.260 can't load file, error: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new BundleData.UploadingStatus(es);
        }
    }

    private static ZipUtils.ValidationResult isZipEntityNameOk(ZipEntry zipEntry) {
        Matcher m = ZIP_CHARS_PATTERN.matcher(zipEntry.getName());
        return m.matches() ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult("#981.010 Wrong name of file in zip file. Name: "+zipEntry.getName());
    }

    private static ZipUtils.ValidationResult isZipEntitySizeOk(ZipEntry zipEntry) {
        if (zipEntry.isDirectory()) {
            return ZipUtils.VALIDATION_RESULT_OK;
        }
        return zipEntry.getSize()>0 ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult(
                "#981.013 File "+zipEntry.getName()+" has a zero length.");
    }

}
