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

package ai.metaheuristic.ai.launchpad.rest.v1;

import ai.metaheuristic.ai.launchpad.batch.process_resource.BatchTopLevelService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Serge
 * Date: 6/14/2019
 * Time: 1:23 AM
 */
@RestController
@RequestMapping("/rest/v1/launchpad/batch")
@Slf4j
@Profile("launchpad")
@CrossOrigin
public class BatchRestController {

    private final BatchTopLevelService batchTopLevelService;

    public BatchRestController(BatchTopLevelService batchTopLevelService) {
        this.batchTopLevelService = batchTopLevelService;
    }

    @GetMapping("/batches")
    public BatchData.BatchesResult batches(@PageableDefault(size = 20) Pageable pageable) {
        return batchTopLevelService.getBatches(pageable);
    }

    @PostMapping("/batches-part")
    public BatchData.BatchesResult batchesPart(@PageableDefault(size = 20) Pageable pageable) {
        return batchTopLevelService.getBatches(pageable);
    }

    @GetMapping(value = "/batch-add")
    public BatchData.PlansForBatchResult batchAdd() {
        return batchTopLevelService.getPlansForBatchResult();
    }

    @PostMapping(value = "/batch-upload-from-file")
    public OperationStatusRest uploadFile(final MultipartFile file, Long planId) {
        return batchTopLevelService.batchUploadFromFile(file, planId);
    }

    @GetMapping(value= "/batch-status/{batchId}" )
    public BatchData.Status getProcessingResourceStatus(@PathVariable("batchId") Long batchId) {
        return batchTopLevelService.getProcessingResourceStatus(batchId);
    }

    @GetMapping("/batch-delete/{batchId}")
    public BatchData.Status processResourceDelete(@PathVariable Long batchId) {
        return batchTopLevelService.getProcessingResourceStatus(batchId);
    }

    @PostMapping("/batch-delete-commit")
    public OperationStatusRest processResourceDeleteCommit(Long batchId) {
        return batchTopLevelService.processResourceDeleteCommit(batchId);
    }

}
