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

import aiai.ai.launchpad.data.SimpleData;
import aiai.ai.launchpad.data.TasksData;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.api.v1.launchpad.Task;
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

    @GetMapping("/max-concrete-order/{flowInstanceId}")
    public SimpleData.IntegerResult findMaxConcreteOrder(@PathVariable Long flowInstanceId) {
        Integer max = taskRepository.findMaxConcreteOrder(flowInstanceId);
        return new SimpleData.IntegerResult(max);
    }

    @GetMapping("/any-with-concrete-order/{flowInstanceId}/{taskOrder}")
    public TasksData.ListOfTasksResult findAnyWithConcreteOrder(
            @PathVariable long flowInstanceId, @PathVariable int taskOrder) {
        List<Task> tasks = taskRepository.findAnyWithConcreteOrder(flowInstanceId, taskOrder);
        return new TasksData.ListOfTasksResult(tasks);
    }
}
