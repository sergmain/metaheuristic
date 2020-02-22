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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.*;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionTopLevelService {

    private final Globals globals;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionService functionService;
    private final FunctionDataService functionDataService;

    public FunctionData.FunctionsResult getFunctions() {
        FunctionData.FunctionsResult result = new FunctionData.FunctionsResult();
        result.functions = functionRepository.findAll();
        result.functions.sort((o1, o2)->o2.getId().compareTo(o1.getId()));
        result.assetMode = globals.assetMode;
        return result;
    }

    public OperationStatusRest deleteFunctionById(Long id) {
        log.info("Start deleting function with id: {}", id );
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.005 Can't delete function while 'replicated' mode of asset is active");
        }
        final Function function = functionCache.findById(id);
        if (function == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.010 function wasn't found, functionId: " + id);
        }
        functionCache.delete(function.getId());
        functionDataService.deleteByFunctionCode(function.getCode());
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadFunction(final MultipartFile file) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.020 Can't upload function while 'replicated' mode of asset is active");
        }
        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.030 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.040 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT, YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.050 only '.zip', '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        File tempDir = null;
        try {
            tempDir = DirUtils.createTempDir("function-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#424.060 can't create temporary directory in " + location);
            }
            final File zipFile = new File(tempDir, "functions" + ext);
            log.debug("Start storing an uploaded function to disk");
            try(OutputStream os = new FileOutputStream(zipFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            List<FunctionApiData.FunctionConfigStatus> statuses;
            if (ZIP_EXT.equals(ext)) {
                log.debug("Start unzipping archive");
                ZipUtils.unzipFolder(zipFile, tempDir);
                log.debug("Start loading function data to db");
                statuses = new ArrayList<>();
                functionService.loadFunctionsRecursively(statuses, tempDir);
            }
            else {
                log.debug("Start loading function data to db");
                statuses = functionService.loadFunctionsFromDir(tempDir);
            }
            if (isError(statuses)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        toErrorMessages(statuses));
            }
        }
        catch (Exception e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.070 can't load functions, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private List<String> toErrorMessages(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).map(o->o.error).collect(Collectors.toList());
    }

    private boolean isError(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).findFirst().orElse(null)!=null;
    }

}
