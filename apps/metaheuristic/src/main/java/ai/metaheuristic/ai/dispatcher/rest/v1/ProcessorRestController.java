/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/v1/dispatcher")
@Profile("dispatcher")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
@PreAuthorize("hasAnyRole('ADMIN')")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorRestController {

    private final ProcessorTopLevelService processorTopLevelService;

    @GetMapping("/processors")
    public ProcessorData.ProcessorsResult init(@PageableDefault(size = 50) Pageable pageable) {
        return processorTopLevelService.getProcessors(pageable);
    }

    @GetMapping(value = "/processor/{id}")
    public ProcessorData.ProcessorResult getProcessor(@PathVariable Long id) {
        return processorTopLevelService.getProcessor(id);
    }

    @PostMapping("/processor-form-commit")
    public ProcessorData.ProcessorResult updateDescription(@RequestBody Processor processor) {
        return processorTopLevelService.updateDescription(processor.id, processor.description);
    }

    @PostMapping("/processor-delete-commit")
    public OperationStatusRest deleteProcessorCommit(Long id) {
        return processorTopLevelService.deleteProcessorById(id);
    }

    /**
     *
     * @param processorIds comma-separated list of processorId for deleting
     * @return
     */
    @PostMapping("/processor-bulk-delete-commit")
    public ProcessorData.BulkOperations  processProcessorBulkDeleteCommit(String processorIds) {
        ProcessorData.BulkOperations  r = processorTopLevelService.processProcessorBulkDeleteCommit(processorIds);
        return r;
    }

}
