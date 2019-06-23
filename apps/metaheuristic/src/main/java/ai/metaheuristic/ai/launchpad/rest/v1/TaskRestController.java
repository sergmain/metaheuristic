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

import ai.metaheuristic.api.data.SimpleApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.api.launchpad.Task;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rest/v1/launchpad/task")
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class TaskRestController {

    private final TaskRepository taskRepository;

    public TaskRestController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // ============= Service methods =============

    @GetMapping("/max-concrete-order/{workbookId}")
    public SimpleApiData.IntegerResult findMaxConcreteOrder(@PathVariable Long workbookId) {
        Integer max = taskRepository.findMaxConcreteOrder(workbookId);
        return new SimpleApiData.IntegerResult(max);
    }

    @GetMapping("/any-with-concrete-order/{workbookId}/{taskOrder}")
    public TaskApiData.ListOfTasksResult findAnyWithConcreteOrder(
            @PathVariable long workbookId, @PathVariable int taskOrder) {
        List<Task> tasks = taskRepository.findAnyWithConcreteOrder(workbookId, taskOrder);
        return new TaskApiData.ListOfTasksResult(tasks);
    }
}
