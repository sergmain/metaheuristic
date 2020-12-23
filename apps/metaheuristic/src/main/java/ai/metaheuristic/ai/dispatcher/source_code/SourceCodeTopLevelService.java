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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class SourceCodeTopLevelService {

    private final SourceCodeService sourceCodeService;
    private final Globals globals;
    private final SourceCodeRepository sourceCodeRepository;

    public SourceCodeApiData.SourceCodeResult createSourceCode(String sourceCodeYamlAsStr, Long companyUniqueId) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new SourceCodeApiData.SourceCodeResult("#560.085 Can't add a new sourceCode while 'replicated' mode of asset is active");
        }
        if (StringUtils.isBlank(sourceCodeYamlAsStr)) {
            return new SourceCodeApiData.SourceCodeResult("#560.090 sourceCode yaml is empty");
        }

        SourceCodeParamsYaml ppy;
        try {
            ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeYamlAsStr);
        } catch (WrongVersionOfYamlFileException e) {
            String es = "#560.110 An error parsing yaml: " + e.getMessage();
            log.error(es, e);
            return new SourceCodeApiData.SourceCodeResult(es);
        }

        final SourceCodeApiData.SourceCodeResult sourceCodeResult = checkSourceCodeExist(ppy);
        if (sourceCodeResult != null) {
            return sourceCodeResult;
        }

        try {
            return sourceCodeService.createSourceCode(sourceCodeYamlAsStr, ppy, companyUniqueId);
        } catch (DataIntegrityViolationException e) {
            return new SourceCodeApiData.SourceCodeResult("#560.155 data integrity error: " + e.getMessage());
        }
    }

    @Nullable
    public SourceCodeApiData.SourceCodeResult checkSourceCodeExist(SourceCodeParamsYaml ppy) {
        final String code = ppy.source.uid;
        if (StringUtils.isBlank(code)) {
            return new SourceCodeApiData.SourceCodeResult("#560.130 the code of sourceCode is empty");
        }
        SourceCode f = sourceCodeRepository.findByUid(code);
        if (f!=null) {
            return new SourceCodeApiData.SourceCodeResult("#560.150 the sourceCode with code "+code+" already exists");
        }
        return null;
    }

    public OperationStatusRest uploadSourceCode(MultipartFile file, DispatcherContext context) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
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

        try {
            SourceCodeParamsYaml ppy;
            String sourceCodeYamlAsStr;
            try (InputStream is = file.getInputStream()) {
                sourceCodeYamlAsStr = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCodeYamlAsStr);
            } catch (WrongVersionOfYamlFileException e) {
                String es = "#560.340 An error parsing yaml: " + e.getMessage();
                log.error(es, e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
            final SourceCodeApiData.SourceCodeResult sourceCodeResult = checkSourceCodeExist(ppy);
            if (sourceCodeResult != null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, sourceCodeResult.getErrorMessagesAsList(), sourceCodeResult.getInfoMessagesAsList());
            }

            SourceCodeApiData.SourceCodeResult result = sourceCodeService.createSourceCode(sourceCodeYamlAsStr, ppy, context.getCompanyId());

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.getErrorMessagesAsList(), result.getInfoMessagesAsList());
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("#560.370 Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.380 can't load source codes, Error: " + e.toString());
        }
    }

}
