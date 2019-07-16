/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.BinaryData;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.data.ResourceData;
import ai.metaheuristic.ai.utils.ControllerUtils;
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
public class ResourceTopLevelService {

    private final Globals globals;
    private final ResourceService resourceService;
    private final BinaryDataService binaryDataService;

    public ResourceTopLevelService(Globals globals, BinaryDataService binaryDataService, ResourceService resourceService) {
        this.globals = globals;
        this.binaryDataService = binaryDataService;
        this.resourceService = resourceService;
    }

    public ResourceData.ResourcesResult getResources(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.resourceRowsLimit, pageable);
        return new ResourceData.ResourcesResult(binaryDataService.getAllAsSimpleResources(pageable));
    }

    public OperationStatusRest createResourceFromFile(
            MultipartFile file, String resourcePoolCode, String resourceCode) {
        String originFilename = file.getOriginalFilename();
        return storeFileInternal(file, resourceCode, resourcePoolCode, originFilename);
    }

    public OperationStatusRest storeFileInternal(MultipartFile file, String resourceCode, String resourcePoolCode, String originFilename) {
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
            FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
        } catch (IOException e) {
            log.error("Error while storing data to temp file", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#172.030 can't persist uploaded file as " +
                            tempFile.getAbsolutePath()+", error: " + e.toString());
        }

        String code = StringUtils.isNotBlank(resourceCode)
                ? resourceCode
                : resourcePoolCode + '-' + originFilename;

        try {
            resourceService.storeInitialResource(tempFile, code, resourcePoolCode, originFilename);
        } catch (StoreNewFileException e) {
            String es = "#172.040 An error while saving data to file, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest registerResourceInExternalStorage(String resourcePoolCode, String params ) {

        if (StringUtils.isBlank(params)) {
            String es = "#172.050 resource params is blank";
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        DataStorageParams dsp = DataStorageParamsUtils.to(params);
        if (dsp.sourcing==null || dsp.sourcing== EnumsApi.DataSourcing.launchpad) {
            String es = "#172.055 Sourcing must be "+ EnumsApi.DataSourcing.launchpad + " or " +EnumsApi.DataSourcing.launchpad +", actual: " + dsp.sourcing;
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        String code = ResourceUtils.toResourceCode(resourcePoolCode);
        try {
            binaryDataService.saveWithSpecificStorageUrl(code, resourcePoolCode, params);
        } catch (StoreNewFileException e) {
            String es = "#172.080 An error while saving data to file, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public ResourceData.ResourceResult getResourceById(Long id) {
        final BinaryData data = binaryDataService.findById(id).orElse(null);
        if (data==null) {
            return new ResourceData.ResourceResult("#172.100 Resource wasn't found for id: " + id);
        }
        return new ResourceData.ResourceResult(data);
    }

    public OperationStatusRest deleteResource(Long id) {
        final BinaryData data = binaryDataService.findById(id).orElse(null);
        if (data==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#172.120 Resource wasn't found for id: " + id);
        }
        binaryDataService.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // ============= Service methods =============

    public OperationStatusRest storeInitialResource(MultipartFile file, String resourceCode, String resourcePoolCode, String filename) {
        return storeFileInternal(file, resourceCode, resourcePoolCode, filename);
    }
}
