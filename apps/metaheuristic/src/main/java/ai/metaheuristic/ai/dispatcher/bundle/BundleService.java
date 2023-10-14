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
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import static ai.metaheuristic.ai.Consts.ZIP_EXT;
import static ai.metaheuristic.api.EnumsApi.OperationStatus.ERROR;

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
public class BundleService {

    private static final Pattern ZIP_CHARS_PATTERN = Pattern.compile("^[/\\\\A-Za-z0-9._-]*$");
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_FUNCTION = BundleService::isZipEntityNameOk;
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_ENTRY_SIZE_FUNCTION = BundleService::isZipEntitySizeOk;

    private final SourceCodeCache sourceCodeCache;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final SourceCodeSelectorService sourceCodeSelectorService;

    public OperationStatusRest uploadFromFile(final MultipartFile file, final DispatcherContext dispatcherContext) {
        if (Consts.ID_1.equals(dispatcherContext.getCompanyId())) {
            return new OperationStatusRest(ERROR, "971.030 Batch can't be created in company #1");
        }
        if (file.getSize()==0) {
            return new OperationStatusRest(ERROR, "971.035 Can't upload bundle because uploaded file has a zero length");
        }

        log.info("971.055 Staring of uploadFromFile(), file: {}, size: {}", file.getOriginalFilename(), file.getSize());

        String tempFilename = file.getOriginalFilename();
        if (S.b(tempFilename)) {
            return new OperationStatusRest(ERROR,"971.040 name of uploaded file is blank");
        }
        // fix for the case when browser sends a full path, ie Edge
        final String originFilename = new File(tempFilename).getName();

        String extTemp = StrUtils.getExtension(originFilename);
        if (extTemp==null) {
            return new OperationStatusRest(ERROR, "971.060 file without extension, bad filename: " + originFilename);
        }
        String ext = extTemp.toLowerCase();
        if (!StringUtils.equalsAny(ext, ZIP_EXT)) {
            return new OperationStatusRest(ERROR,"971.080 only '.zip' files are supported, bad filename: " + originFilename);
        }

        Path tempFile;
        try {
            // TODO 2021.03.13 add a support of
            //  CleanerInfo resource = new CleanerInfo();
            Path tempDir = DirUtils.createMhTempPath("uploaded-bundle-");
            if (tempDir==null) {
                return new OperationStatusRest(ERROR, "971.090 Can't create a temporary dir");
            }
            tempFile = tempDir.resolve("zip.zip");
            file.transferTo(tempFile);
            if (file.getSize()!=Files.size(tempFile)) {
                return new OperationStatusRest(ERROR, "971.125 System error while preparing data. The sizes of files are different");
            }
            List<String> errors = ZipUtils.validate(tempFile, VALIDATE_ZIP_ENTRY_SIZE_FUNCTION);
            if (!errors.isEmpty()) {
                errors.add(0, "971.144 Batch can't be created because of following errors:");
                return new OperationStatusRest(ERROR, errors);
            }
        } catch (IOException e) {
            return new OperationStatusRest(ERROR,"971.140 Can't create a new temp file");
        }


        try {
//            try (InputStream is = new FileInputStream(tempFile)) {
//                execContextVariableService.initInputVariable(is, file.getSize(), originFilename, creationResult.execContext.id, execContextParamsYaml, 0);
//            }

            /*
            final BundleData.UploadingStatus uploadingStatus;
            uploadingStatus = execContextSyncService.getWithSync(creationResult.execContext.id, ()->
                    execContextGraphSyncService.getWithSync(creationResult.execContext.execContextGraphId, ()->
                            execContextTaskStateSyncService.getWithSync(creationResult.execContext.execContextTaskStateId, ()->
                                    batchService.createBatchForFile(
                                            sc, creationResult.execContext.id, execContextParamsYaml, dispatcherContext))));
            return uploadingStatus;
*/
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (ExecContextTooManyInstancesException e) {
            String es = S.f("971.255 Too many instances of SourceCode '%s', max allowed: %d, current count: %d", e.sourceCodeUid, e.max, e.curr);
            log.warn(es);
            return new OperationStatusRest(ERROR, es);
        }
        catch (Throwable th) {
            String es = "971.260 can't load file, error: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new OperationStatusRest(ERROR, es);
        }
    }

    private static ZipUtils.ValidationResult isZipEntityNameOk(ZipEntry zipEntry) {
        Matcher m = ZIP_CHARS_PATTERN.matcher(zipEntry.getName());
        return m.matches() ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult("971.010 Wrong name of file in zip file. Name: "+zipEntry.getName());
    }

    private static ZipUtils.ValidationResult isZipEntitySizeOk(ZipEntry zipEntry) {
        if (zipEntry.isDirectory()) {
            return ZipUtils.VALIDATION_RESULT_OK;
        }
        return zipEntry.getSize()>0 ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult(
                "971.013 File "+zipEntry.getName()+" has a zero length.");
    }

}
