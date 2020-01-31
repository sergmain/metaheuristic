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
package ai.metaheuristic.ai.launchpad.launchpad_resource;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.StoreNewFileException;
import ai.metaheuristic.ai.launchpad.beans.GlobalBinaryData;
import ai.metaheuristic.ai.launchpad.binary_data.GlobalBinaryDataService;
import ai.metaheuristic.ai.launchpad.data.ResourceData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class ResourceTopLevelService {

    private final Globals globals;
    private final GlobalBinaryDataService globalBinaryDataService;

    public ResourceData.ResourcesResult getResources(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.resourceRowsLimit, pageable);
        return new ResourceData.ResourcesResult(globalBinaryDataService.getAllAsSimpleResources(pageable));
    }

    public OperationStatusRest createResourceFromFile(MultipartFile file, String variable) {
        String originFilename = file.getOriginalFilename();
        return storeFileInternal(file, variable, originFilename);
    }

    private OperationStatusRest storeFileInternal(MultipartFile file, String variable, String originFilename) {
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#172.010 name of uploaded file is null");
        }
        File tempFile = globals.createTempFileForLaunchpad("temp-raw-file-");
        if (tempFile.exists()) {
            if (!tempFile.delete() ) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#172.020 can't delete dir " + tempFile.getAbsolutePath());
            }
        }
        try {
            try {
                FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
            } catch (IOException e) {
                log.error("Error while storing data to temp file", e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#172.030 can't persist uploaded file as " +
                                tempFile.getAbsolutePath()+", error: " + e.toString());
            }

            try {
                globalBinaryDataService.storeInitialResource(tempFile, variable, originFilename);
            } catch (StoreNewFileException e) {
                String es = "#172.040 An error while saving data to file, " + e.toString();
                log.error(es, e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
        } finally {
            DirUtils.deleteAsync(tempFile);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest createGlobalVariableWithExternalStorage(String variable, String params ) {

        if (StringUtils.isBlank(params)) {
            String es = "#172.050 resource params is blank";
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        DataStorageParams dsp = DataStorageParamsUtils.to(params);
        if (dsp.sourcing==null || dsp.sourcing== EnumsApi.DataSourcing.launchpad) {
            String es = "#172.055 Sourcing must be "+ EnumsApi.DataSourcing.disk + " or " +EnumsApi.DataSourcing.git +", actual: " + dsp.sourcing;
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        try {
            globalBinaryDataService.createGlobalVariableWithExternalStorage(variable, params);
        } catch (StoreNewFileException e) {
            String es = "#172.080 An error while saving data to file, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public ResourceData.ResourceResult getResourceById(Long id) {
        final SimpleVariable sv = globalBinaryDataService.getByIdAsSimpleResource(id);
        if (sv==null) {
            return new ResourceData.ResourceResult("#172.100 Resource wasn't found for id: " + id);
        }
        return new ResourceData.ResourceResult(sv);
    }

    public OperationStatusRest deleteResource(Long id) {
        final GlobalBinaryData data = globalBinaryDataService.findById(id).orElse(null);
        if (data==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#172.120 Resource wasn't found for id: " + id);
        }
        globalBinaryDataService.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // ============= Service methods =============

    public OperationStatusRest storeInitialResource(MultipartFile file, String variable, String filename) {
        return storeFileInternal(file, variable, filename);
    }
}
