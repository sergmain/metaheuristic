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

package aiai.ai.launchpad.rest;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.exceptions.StoreNewFileException;
import aiai.ai.launchpad.beans.BinaryData;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.launchpad_resource.ResourceService;
import aiai.ai.launchpad.rest.data.OperationStatusRest;
import aiai.ai.launchpad.rest.data.ResourceData;
import aiai.ai.utils.ControllerUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/ng/launchpad/resource")
@Slf4j
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class ResourceRestController {

    private final Globals globals;
    private final ResourceService resourceService;
    private final BinaryDataService binaryDataService;

    public ResourceRestController(Globals globals, BinaryDataService binaryDataService, ResourceService resourceService) {
        this.globals = globals;
        this.binaryDataService = binaryDataService;
        this.resourceService = resourceService;
    }

    @GetMapping("/resources")
    public ResourceData.ResourcesResultRest getResources(@PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.resourceRowsLimit, pageable);
        return new ResourceData.ResourcesResultRest(binaryDataService.getAllAsSimpleResources(pageable));
    }

/*
    @RequestMapping(method = RequestMethod.POST, headers = ("content-type=multipart/*"), produces = "application/json", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    @ResponseStatus(value = HttpStatus.CREATED)
    public void handleFileUpload(@RequestPart(required = true) MultipartFile file) {
        storageService.store(file);  //your service to handle upload.
    }
*/

    @PostMapping(value = "/resource-upload-from-file", headers = ("content-type=multipart/*"), produces = "application/json", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OperationStatusRest createResourceFromFile(
            @RequestPart MultipartFile file,
            @RequestParam(name = "code") String resourceCode,
            @RequestParam(name = "poolCode") String resourcePoolCode ) {
        File tempFile = globals.createTempFileForLaunchpad("temp-raw-file-");
        if (tempFile.exists()) {
            if (!tempFile.delete() ) {
                return new OperationStatusRest(Enums.OperationStatus.ERROR,
                        "#173.36 can't delete dir " + tempFile.getAbsolutePath());
            }
        }
        try {
            FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
        } catch (IOException e) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#173.06 can't persist uploaded file as " +
                            tempFile.getAbsolutePath()+", error: " + e.toString());
        }

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#172.01 name of uploaded file is null");
        }
        String code = StringUtils.isNotBlank(resourceCode)
                ? resourceCode
                : resourcePoolCode + '-' + originFilename;

        try {
            resourceService.storeInitialResource(originFilename, tempFile, code, resourcePoolCode, originFilename);
        } catch (StoreNewFileException e) {
            String es = "#172.04 An error while saving data to file, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(Enums.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @PostMapping(value = "/resource-in-external-storage")
    public OperationStatusRest registerResourceInExternalStorage(
            @RequestParam(name = "poolCode") String resourcePoolCode,
            @RequestParam(name = "storageUrl") String storageUrl ) {

        if (StringUtils.contains(storageUrl, ' ')) {
            String es = "#172.05 storage url can't contain 'space' char: " + storageUrl;
            log.error(es);
            return new OperationStatusRest(Enums.OperationStatus.ERROR, es);
        }

        if (!StringUtils.startsWith(storageUrl, "disk://")) {
            String es = "#172.06 wrong format of storage url: " + storageUrl;
            log.error(es);
            return new OperationStatusRest(Enums.OperationStatus.ERROR, es);
        }

        String code = StringUtils.replaceEach(storageUrl, new String[] {"://", "/", "*", "?"}, new String[] {"-", "-", "-", "-"} );
        try {
            binaryDataService.saveWithSpecificStorageUrl(code, resourcePoolCode, storageUrl);
        } catch (StoreNewFileException e) {
            String es = "#172.08 An error while saving data to file, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(Enums.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @GetMapping("/resource/{id}")
    public ResourceData.ResourceResultRest get(@PathVariable Long id) {
        final BinaryData data = binaryDataService.findById(id).orElse(null);
        if (data==null) {
            return new ResourceData.ResourceResultRest("#172.10 Resource wasn't found for id: " + id);
        }
        return new ResourceData.ResourceResultRest(data);
    }

    @PostMapping("/resource-delete-commit")
    public OperationStatusRest deleteResource(Long id) {
        final BinaryData data = binaryDataService.findById(id).orElse(null);
        if (data==null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#172.20 Resource wasn't found for id: " + id);
        }
        binaryDataService.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
