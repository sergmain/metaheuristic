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

package ai.metaheuristic.ai.launchpad.source_code;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.data.SourceCodeData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadInternalEvent;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextCache;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextService;
import ai.metaheuristic.ai.launchpad.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.launchpad.variable.GlobalVariableService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.launchpad.ExecContext;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class SourceCodeTopLevelService {

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeService sourceCodeService;
    private final SourceCodeRepository sourceCodeRepository;
    private final ExecContextService execContextService;
    private final ApplicationEventPublisher publisher;
    private final ExecContextCache execContextCache;
    private final GlobalVariableService globalVariableService;

    public SourceCodeApiData.ExecContextResult addExecContext(Long sourceCodeId, String variable, LaunchpadContext context) {
        return getExecContextResult(variable, context, sourceCodeCache.findById(sourceCodeId));
    }

    public SourceCodeApiData.ExecContextResult addExecContext(String sourceCodeUid, String variable, LaunchpadContext context) {
        return getExecContextResult(variable, context, sourceCodeRepository.findByUidAndCompanyId(sourceCodeUid, context.getCompanyId()));
    }

    private SourceCodeApiData.ExecContextResult getExecContextResult(String variable, LaunchpadContext context, SourceCodeImpl sourceCode) {
        if (S.b(variable)) {
            return new SourceCodeApiData.ExecContextResult("#560.006 name of variable is empty");
        }
        if (globalVariableService.getIdInVariables(List.of(variable)).isEmpty()) {
            return new SourceCodeApiData.ExecContextResult( "#560.008 global variable " + variable +" wasn't found");
        }
        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeService.validateInternal(sourceCode);
        if (sourceCodeValidation.status != EnumsApi.SourceCodeValidateStatus.OK) {
            SourceCodeApiData.ExecContextResult r = new SourceCodeApiData.ExecContextResult();
            r.errorMessages = sourceCodeValidation.errorMessages;
            return r;
        }

        OperationStatusRest status = checkSourceCode(sourceCode, context);
        if (status != null) {
            return new SourceCodeApiData.ExecContextResult( "#560.011 access denied: " + status.getErrorMessagesAsStr());
        }
        return execContextService.createExecContextInternal(sourceCode, variable);
    }

    public SourceCodeApiData.SourceCodesResult getSourceCodes(Pageable pageable, boolean isArchive, LaunchpadContext context) {
        pageable = ControllerUtils.fixPageSize(globals.sourceCodeRowsLimit, pageable);
        List<Long> sourceCodeIds = sourceCodeRepository.findAllIdsByOrderByIdDesc(context.getCompanyId());
        AtomicInteger count = new AtomicInteger();

        List<SourceCode> activeSourceCodes = sourceCodeIds.stream()
                .map(sourceCodeCache::findById)
                .filter(sourceCode-> {
                    try {
                        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
                        boolean b = scspy.internalParams == null || !scspy.internalParams.archived;
                        b = isArchive != b;
                        if (b) {
                            count.incrementAndGet();
                        }
                        return b;
                    } catch (YAMLException e) {
                        log.error("#560.020 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #{}", sourceCode.getId());
                        log.error("#560.025 Params:\n{}", sourceCode.getParams());
                        log.error("#560.030 Error: {}", e.toString());
                        return false;
                    }
                }).collect(Collectors.toList());

        List<SourceCode> sourceCodes = activeSourceCodes.stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .peek(o-> o.setParams(null))
                .collect(Collectors.toList());

        SourceCodeApiData.SourceCodesResult sourceCodesResultRest = new SourceCodeApiData.SourceCodesResult();
        sourceCodesResultRest.items = new PageImpl<>(sourceCodes, pageable, count.get());
        sourceCodesResultRest.assetMode = globals.assetMode;

        return sourceCodesResultRest;
    }

    public SourceCodeApiData.SourceCodeResult getSourceCode(Long sourceCodeId, LaunchpadContext context) {
        final SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            return new SourceCodeApiData.SourceCodeResult(
                    "#560.050 sourceCode wasn't found, sourceCodeId: " + sourceCodeId,
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR);
        }
        SourceCodeStoredParamsYaml storedParams = sourceCode.getSourceCodeStoredParamsYaml();
        return new SourceCodeApiData.SourceCodeResult(sourceCode, storedParams.lang, storedParams.source);
    }

    public SourceCodeApiData.SourceCodeResult validateSourceCode(Long sourceCodeId, LaunchpadContext context) {
        final SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            return new SourceCodeApiData.SourceCodeResult("#560.070 sourceCode wasn't found, sourceCodeId: " + sourceCodeId,
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR);
        }

        SourceCodeStoredParamsYaml storedParams = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(sourceCode, storedParams.lang, storedParams.source);
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeService.validateInternal(sourceCode);
        result.errorMessages = sourceCodeValidation.errorMessages;
        result.infoMessages = sourceCodeValidation.infoMessages;
        result.status = sourceCodeValidation.status;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public SourceCodeApiData.SourceCodeResult addSourceCode(String sourceCodeYamlAsStr, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new SourceCodeApiData.SourceCodeResult("#560.085 Can't add a new sourceCode while 'replicated' mode of asset is active");
        }
        if (StringUtils.isBlank(sourceCodeYamlAsStr)) {
            return new SourceCodeApiData.SourceCodeResult("#560.090 sourceCode yaml is empty");
        }

        SourceCodeParamsYaml ppy;
        try {
            ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeYamlAsStr);
        } catch (WrongVersionOfYamlFileException e) {
            return new SourceCodeApiData.SourceCodeResult("#560.110 An error parsing yaml: " + e.getMessage());
        }

        final String code = ppy.source.code;
        if (StringUtils.isBlank(code)) {
            return new SourceCodeApiData.SourceCodeResult("#560.130 the code of sourceCode is empty");
        }
        SourceCode f = sourceCodeRepository.findByUidAndCompanyId(code, context.getCompanyId());
        if (f!=null) {
            return new SourceCodeApiData.SourceCodeResult("#560.150 the sourceCode with such code already exists, code: " + code);
        }

        SourceCodeImpl sourceCode = new SourceCodeImpl();
        SourceCodeStoredParamsYaml scspy = new SourceCodeStoredParamsYaml();
        scspy.source = sourceCodeYamlAsStr;
        scspy.lang = EnumsApi.SourceCodeLang.yaml;
        scspy.internalParams.updatedOn = System.currentTimeMillis();

        sourceCode.updateParams(scspy);

        sourceCode.companyId = context.getCompanyId();
        sourceCode.createdOn = System.currentTimeMillis();
        sourceCode.uid = ppy.source.code;
        sourceCode = sourceCodeCache.save(sourceCode);

        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeService.validateInternal(sourceCode);

        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(sourceCode, sourceCode.getSourceCodeStoredParamsYaml());
        result.infoMessages = sourceCodeValidation.infoMessages;
        result.errorMessages = sourceCodeValidation.errorMessages;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public SourceCodeApiData.SourceCodeResult updateSourceCode(Long sourceCodeId, String sourceCodeYamlAsStr, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new SourceCodeApiData.SourceCodeResult("#560.160 Can't update a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            return new SourceCodeApiData.SourceCodeResult(
                    "#560.010 sourceCode wasn't found, sourceCodeId: " + sourceCodeId,
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR);
        }
        if (StringUtils.isBlank(sourceCodeYamlAsStr)) {
            return new SourceCodeApiData.SourceCodeResult("#560.170 sourceCode yaml is empty");
        }

        SourceCodeParamsYaml ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeYamlAsStr);

        final String code = ppy.source.code;
        if (StringUtils.isBlank(code)) {
            return new SourceCodeApiData.SourceCodeResult("#560.190 code of sourceCode is empty");
        }
        SourceCode p = sourceCodeRepository.findByUidAndCompanyId(code, context.getCompanyId());
        if (p!=null && !p.getId().equals(sourceCode.getId())) {
            return new SourceCodeApiData.SourceCodeResult("#560.230 sourceCode with such code already exists, code: " + code);
        }
        sourceCode.uid = code;

        SourceCodeStoredParamsYaml scspy = new SourceCodeStoredParamsYaml();
        scspy.source = sourceCodeYamlAsStr;
        scspy.lang = EnumsApi.SourceCodeLang.yaml;
        scspy.internalParams.updatedOn = System.currentTimeMillis();
        sourceCode.updateParams(scspy);

        sourceCode = sourceCodeCache.save(sourceCode);

        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeService.validateInternal(sourceCode);

        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(sourceCode, scspy );
        result.infoMessages = sourceCodeValidation.infoMessages;
        result.errorMessages = sourceCodeValidation.errorMessages;
        return result;
    }

    public OperationStatusRest deleteSourceCodeById(Long sourceCodeId, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.240 Can't delete a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCode sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.250 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        sourceCodeCache.deleteById(sourceCodeId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest archiveSourceCodeById(Long sourceCodeId, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.260 Can't archive a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        OperationStatusRest status = checkSourceCode(sourceCode, context);
        if (status!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#560.270 sourceCode wasn't found, sourceCodeId: " + sourceCodeId+", " + status.getErrorMessagesAsStr());
        }
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        if (scspy.internalParams==null) {
            scspy.internalParams = new SourceCodeStoredParamsYaml.InternalParams();
        }
        scspy.internalParams.archived = true;
        scspy.internalParams.updatedOn = System.currentTimeMillis();
        sourceCode.updateParams(scspy);

        sourceCodeCache.save(sourceCode);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadSourceCode(MultipartFile file, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.280 Can't upload sourceCode while 'replicated' mode of asset is active");
        }

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.290 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.310 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.330 only '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("mh-sourceCode-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#560.350 can't create temporary directory in " + location);
            }
            final File sourceCodeFile = new File(tempDir, "source-codes" + ext);
            log.debug("Start storing an uploaded sourceCode to disk");
            try(OutputStream os = new FileOutputStream(sourceCodeFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            log.debug("Start loading sourceCode into db");
            String yaml = FileUtils.readFileToString(sourceCodeFile, StandardCharsets.UTF_8);
            SourceCodeApiData.SourceCodeResult result = addSourceCode(yaml, context);

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages, result.infoMessages);
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.370 can't load source codes, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }
    }

    // ========= ExecContext specific =============

    public OperationStatusRest changeExecContextState(String state, Long execContextId, LaunchpadContext context) {
        EnumsApi.ExecContextState execState = EnumsApi.ExecContextState.valueOf(state.toUpperCase());
        if (execState== EnumsApi.ExecContextState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.390 Unknown exec state, state: " + state);
        }
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        status = execContextService.execContextTargetState(execContextId, execState);
        return status;
    }

    public OperationStatusRest deleteExecContextById(Long execContextId, LaunchpadContext context) {
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        publisher.publishEvent( new LaunchpadInternalEvent.ExecContextDeletionEvent(this, execContextId) );
        execContextService.deleteExecContext(execContextId, context.getCompanyId());

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private OperationStatusRest checkExecContext(Long execContextId, LaunchpadContext context) {
        if (execContextId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.395 execContextId is null");
        }
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.400 ExecContext wasn't found, execContextId: " + execContextId );
        }
        SourceCodeData.SourceCodesForCompany sourceCodesForCompany = sourceCodeService.getSourceCode(context.getCompanyId(), wb.getSourceCodeId());
        if (sourceCodesForCompany.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.405 SourceCode wasn't found, " +
                    "companyId: "+context.getCompanyId()+", sourceCodeId: " + wb.getSourceCodeId()+", execContextId: " + wb.getId()+", error msg: " + sourceCodesForCompany.getErrorMessagesAsStr() );
        }
        return null;
    }

    private OperationStatusRest checkSourceCode(SourceCode sourceCode, LaunchpadContext context) {
        if (sourceCode ==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.395 sourceCode is null");
        }
        if (!Objects.equals(sourceCode.getCompanyId(), context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.405 Access to sourceCode is denied, sourceCodeId: " + sourceCode.getId() );
        }
        return null;
    }
}
