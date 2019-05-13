/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.launchpad.launchpad_resource;

import aiai.ai.Globals;
import aiai.ai.exceptions.StoreNewFileException;
import metaheuristic.api.v1.EnumsApi;
import metaheuristic.api.v1.launchpad.BinaryData;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import metaheuristic.api.v1.data.OperationStatusRest;
import aiai.ai.launchpad.data.ResourceData;
import aiai.ai.utils.ControllerUtils;
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
            MultipartFile file, String resourceCode, String resourcePoolCode ) {
        String originFilename = file.getOriginalFilename();
        return storeFileInternal(file, resourceCode, resourcePoolCode, originFilename);
    }

    public OperationStatusRest storeFileInternal(MultipartFile file, String resourceCode, String resourcePoolCode, String originFilename) {
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#172.01 name of uploaded file is null");
        }
        File tempFile = globals.createTempFileForLaunchpad("temp-raw-file-");
        if (tempFile.exists()) {
            if (!tempFile.delete() ) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#173.36 can't delete dir " + tempFile.getAbsolutePath());
            }
        }
        try {
            FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
        } catch (IOException e) {
            log.error("Error while storing data to temp file", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#173.06 can't persist uploaded file as " +
                            tempFile.getAbsolutePath()+", error: " + e.toString());
        }

        String code = StringUtils.isNotBlank(resourceCode)
                ? resourceCode
                : resourcePoolCode + '-' + originFilename;

        try {
            resourceService.storeInitialResource(tempFile, code, resourcePoolCode, originFilename);
        } catch (StoreNewFileException e) {
            String es = "#172.04 An error while saving data to file, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest registerResourceInExternalStorage(String resourcePoolCode, String storageUrl ) {

        if (StringUtils.contains(storageUrl, ' ')) {
            String es = "#172.05 storage url can't contain 'space' char: " + storageUrl;
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        if (!StringUtils.startsWith(storageUrl, "disk://")) {
            String es = "#172.06 wrong format of storage url: " + storageUrl;
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        String code = StringUtils.replaceEach(storageUrl, new String[] {"://", "/", "*", "?"}, new String[] {"-", "-", "-", "-"} );
        try {
            binaryDataService.saveWithSpecificStorageUrl(code, resourcePoolCode, storageUrl);
        } catch (StoreNewFileException e) {
            String es = "#172.08 An error while saving data to file, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public ResourceData.ResourceResult getResourceById(Long id) {
        final BinaryData data = binaryDataService.findById(id).orElse(null);
        if (data==null) {
            return new ResourceData.ResourceResult("#172.10 Resource wasn't found for id: " + id);
        }
        return new ResourceData.ResourceResult(data);
    }

    public OperationStatusRest deleteResource(Long id) {
        final BinaryData data = binaryDataService.findById(id).orElse(null);
        if (data==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#172.20 Resource wasn't found for id: " + id);
        }
        binaryDataService.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // ============= Service methods =============

    public OperationStatusRest storeInitialResource(MultipartFile file, String resourceCode, String resourcePoolCode, String filename) {
        return storeFileInternal(file, resourceCode, resourcePoolCode, filename);
    }
}
