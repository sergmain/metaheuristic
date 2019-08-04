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

package ai.metaheuristic.ai.launchpad.atlas;

import ai.metaheuristic.ai.launchpad.beans.AtlasTask;
import ai.metaheuristic.ai.launchpad.repositories.AtlasTaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.atlas.AtlasTaskParamsYamlUtils;
import ai.metaheuristic.api.data.atlas.AtlasTaskParamsYaml;
import ai.metaheuristic.api.launchpad.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 8/4/2019
 * Time: 12:36 AM
 */
@Slf4j
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class AtlasStreamService {

    private final TaskRepository taskRepository;
    private final AtlasTaskRepository atlasTaskRepository;

    @Transactional
    public void transferTasksToAtlas(Long atlasId, Long workbookId) {
        try (Stream<Task> stream = taskRepository.findAllByWorkbookIdAsStream(workbookId)) {
            stream.forEach(t -> {
                AtlasTask at = new AtlasTask();
                at.atlasId = atlasId;
                at.taskId = t.getId();
                AtlasTaskParamsYaml atpy = new AtlasTaskParamsYaml();
                atpy.assignedOn = t.getAssignedOn();
                atpy.completed = t.isCompleted();
                atpy.completedOn = t.getCompletedOn();
                atpy.execState = t.getExecState();
                atpy.taskId = t.getId();
                atpy.taskParams = t.getParams();
                // typeAsString will be initialized when AtlasTaskParamsYaml will be requested
                // see method ai.metaheuristic.ai.launchpad.atlas.AtlasTopLevelService.findTasks
                atpy.typeAsString = null;
                atpy.snippetExecResults = t.getSnippetExecResults();
                atpy.metrics = t.getMetrics();

                at.params = AtlasTaskParamsYamlUtils.BASE_YAML_UTILS.toString(atpy);
                atlasTaskRepository.save(at);
            });
        }
    }
}