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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeService {

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final DispatcherParamsService dispatcherParamsService;
    private final SourceCodeRepository sourceCodeRepository;
    private final SourceCodeValidationService sourceCodeValidationService;

    @Transactional(readOnly = true)
    public SourceCodeApiData.SourceCodesResult getSourceCodes(Pageable pageable, boolean isArchive, DispatcherContext context) {
        pageable = ControllerUtils.fixPageSize(globals.sourceCodeRowsLimit, pageable);
        List<Long> sourceCodeIds = sourceCodeRepository.findAllIdsByOrderByIdDesc(context.getCompanyId());
        AtomicInteger count = new AtomicInteger();

        List<SourceCode> activeSourceCodes = sourceCodeIds.stream()
                .map(sourceCodeCache::findById)
                .filter(Objects::nonNull)
                .filter(sourceCode-> {
                    try {
                        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
                        boolean b = !scspy.internalParams.archived;
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
                .collect(Collectors.toList());

        SourceCodeApiData.SourceCodesResult sourceCodesResult = new SourceCodeApiData.SourceCodesResult();
        sourceCodesResult.items = new PageImpl<>(sourceCodes, pageable, count.get());
        sourceCodesResult.assetMode = globals.assetMode;
        sourceCodesResult.batches = dispatcherParamsService.getBatches();
        sourceCodesResult.experiments = dispatcherParamsService.getExperiments();

        return sourceCodesResult;
    }

    @Transactional
    public OperationStatusRest deleteSourceCodeById(@Nullable Long sourceCodeId) {
        if (sourceCodeId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
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

    public static List<SourceCodeParamsYaml.Variable> findVariableByType(SourceCodeParamsYaml scpy, String type) {
        List<SourceCodeParamsYaml.Variable> list = new ArrayList<>();
        for (SourceCodeParamsYaml.Process process : scpy.source.processes) {
            findVariableByType(process, type, list);
        }
        return list;

    }

    private static void findVariableByType(SourceCodeParamsYaml.Process process, String type, List<SourceCodeParamsYaml.Variable> list) {
        for (SourceCodeParamsYaml.Variable output : process.outputs) {
            if (output.type.equals(type)) {
                list.add(output);
            }
        }
        if (process.subProcesses!=null) {
            for (SourceCodeParamsYaml.Process p : process.subProcesses.processes) {
                findVariableByType(p, type, list);
            }
        }
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.SourceCodeResult getSourceCode(Long sourceCodeId, DispatcherContext context) {
        final SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            String errorMessage = "#560.050 sourceCode wasn't found, sourceCodeId: " + sourceCodeId;
            return new SourceCodeApiData.SourceCodeResult(
                    errorMessage,
                    new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR, errorMessage));
        }
        SourceCodeStoredParamsYaml storedParams = sourceCode.getSourceCodeStoredParamsYaml();
        return new SourceCodeApiData.SourceCodeResult(sourceCode, storedParams.lang, storedParams.source);
    }

    @Transactional
    public SourceCodeApiData.SourceCodeResult validateSourceCode(Long sourceCodeId, DispatcherContext context) {
        final SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            String es = "#560.070 SourceCode wasn't found, sourceCodeId: " + sourceCodeId;
            return new SourceCodeApiData.SourceCodeResult(es,
                    new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR, es)
            );
        }

        SourceCodeStoredParamsYaml storedParams = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(sourceCode, storedParams.lang, storedParams.source);
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);
        result.errorMessages = sourceCodeValidation.errorMessages;
        result.infoMessages = sourceCodeValidation.infoMessages;
        result.validationResult = sourceCodeValidation.status;
        return result;
    }

    @Transactional
    public SourceCodeApiData.SourceCodeResult createSourceCode(String sourceCodeYamlAsStr, SourceCodeParamsYaml ppy, Long companyUniqueId) {
        SourceCodeImpl sourceCode = new SourceCodeImpl();
        SourceCodeStoredParamsYaml scspy = new SourceCodeStoredParamsYaml();
        scspy.source = sourceCodeYamlAsStr;
        scspy.lang = EnumsApi.SourceCodeLang.yaml;
        scspy.internalParams.updatedOn = System.currentTimeMillis();
        sourceCode.updateParams(scspy);

        sourceCode.companyId = companyUniqueId;
        sourceCode.createdOn = System.currentTimeMillis();
        sourceCode.uid = ppy.source.uid;
        sourceCode = sourceCodeCache.save(sourceCode);

        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);

        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(sourceCode, sourceCode.getSourceCodeStoredParamsYaml());
        result.infoMessages = sourceCodeValidation.infoMessages;
        result.errorMessages = sourceCodeValidation.errorMessages;
        return result;
    }

    @Transactional
    public SourceCodeApiData.SourceCodeResult updateSourceCode(Long sourceCodeId, String sourceCodeYamlAsStr, DispatcherContext context) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new SourceCodeApiData.SourceCodeResult("#560.160 Can't update a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            String es = "#560.010 sourceCode wasn't found, sourceCodeId: " + sourceCodeId;
            return new SourceCodeApiData.SourceCodeResult( es,
                    new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR, es));
        }
        if (StringUtils.isBlank(sourceCodeYamlAsStr)) {
            return new SourceCodeApiData.SourceCodeResult("#560.170 sourceCode yaml is empty");
        }

        SourceCodeParamsYaml ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeYamlAsStr);

        final String code = ppy.source.uid;
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

        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);

        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(sourceCode, scspy );
        result.infoMessages = sourceCodeValidation.infoMessages;
        result.errorMessages = sourceCodeValidation.errorMessages;
        return result;
    }

    @Transactional(readOnly = true)
    public OperationStatusRest deleteSourceCodeById(Long sourceCodeId, DispatcherContext context) {
        return deleteSourceCodeById(sourceCodeId);
    }

    @Transactional
    public OperationStatusRest archiveSourceCodeById(Long sourceCodeId, DispatcherContext context) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.260 Can't archive a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#560.270 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        scspy.internalParams.archived = true;
        scspy.internalParams.updatedOn = System.currentTimeMillis();
        sourceCode.updateParams(scspy);

        sourceCodeCache.save(sourceCode);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest uploadSourceCode(String yaml, SourceCodeParamsYaml ppy, DispatcherContext context) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.280 Can't upload sourceCode while 'replicated' mode of asset is active");
        }

        try {
            log.debug("Start loading sourceCode into db");
            SourceCodeApiData.SourceCodeResult result = createSourceCode(yaml, ppy, context.getCompanyId());

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.getErrorMessagesAsList(), result.getInfoMessagesAsList());
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.370 can't load source codes, Error: " + e.toString());
        }
    }

}
