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

package aiai.ai.launchpad.rest.v1;

import ai.metaheuristic.api.v1.data.OperationStatusRest;
import aiai.ai.launchpad.data.ResourceData;
import aiai.ai.launchpad.launchpad_resource.ResourceTopLevelService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rest/v1/launchpad/resource")
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class ResourceRestController {

    private final ResourceTopLevelService resourceTopLevelService;

    public ResourceRestController(ResourceTopLevelService resourceTopLevelService) {
        this.resourceTopLevelService = resourceTopLevelService;
    }

    @GetMapping("/resources")
    public ResourceData.ResourcesResult getResources(@PageableDefault(size = 5) Pageable pageable) {
        return resourceTopLevelService.getResources(pageable);
    }

    @PostMapping(value = "/resource-upload-from-file", headers = ("content-type=multipart/*"), produces = "application/json", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OperationStatusRest createResourceFromFile(
            @RequestPart MultipartFile file,
            @RequestParam(name = "code") String resourceCode,
            @RequestParam(name = "poolCode") String resourcePoolCode ) {
        return resourceTopLevelService.createResourceFromFile(file, resourceCode, resourcePoolCode);
    }

    @PostMapping(value = "/resource-in-external-storage")
    public OperationStatusRest registerResourceInExternalStorage(
            @RequestParam(name = "poolCode") String resourcePoolCode,
            @RequestParam(name = "storageUrl") String storageUrl ) {
        return resourceTopLevelService.registerResourceInExternalStorage(resourcePoolCode, storageUrl);
    }

    @GetMapping("/resource/{id}")
    public ResourceData.ResourceResult get(@PathVariable Long id) {
        return resourceTopLevelService.getResourceById(id);
    }

    @PostMapping("/resource-delete-commit")
    public OperationStatusRest deleteResource(Long id) {
        return resourceTopLevelService.deleteResource(id);
    }

    // ============= Service methods =============

    @PostMapping(value = "/store-initial-resource", headers = ("content-type=multipart/*"), produces = "application/json", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OperationStatusRest storeInitialResource(
            @RequestPart MultipartFile file,
            @RequestParam(name = "code") String resourceCode,
            @RequestParam(name = "poolCode") String resourcePoolCode,
            @RequestParam(name = "filename") String filename
    ) {
        return resourceTopLevelService.storeInitialResource(file, resourceCode, resourcePoolCode, filename);
    }

}
