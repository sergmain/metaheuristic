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

package ai.metaheuristic.ai.launchpad.event;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.LaunchpadEvent;
import ai.metaheuristic.ai.launchpad.repositories.LaunchpadEventRepository;
import ai.metaheuristic.ai.yaml.event.LaunchpadEventYaml;
import ai.metaheuristic.ai.yaml.event.LaunchpadEventYamlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:34 PM
 */
@Service
@RequiredArgsConstructor
@Profile("launchpad")
public class LaunchpadEventService {

    private final LaunchpadEventRepository launchpadEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishBatchEvent(
            Enums.LaunchpadEventType event, String filename,
            Long size, Long batchId, Long workbookId, LaunchpadContext launchpadContext) {
        if (event==Enums.LaunchpadEventType.BATCH_PROCESSING_STARTED && (batchId==null || launchpadContext==null)) {
            throw new IllegalStateException("Error (event==Enums.LaunchpadEventType.BATCH_PROCESSING_STARTED && (batchId==null || launchpadContext==null))");
        }
        LaunchpadEventYaml.BatchEventData batchEventData = new LaunchpadEventYaml.BatchEventData();
        batchEventData.filename = filename;
        batchEventData.size = size;
        batchEventData.batchId = batchId;
        batchEventData.workbookId = workbookId;
        String contextId = null;
        if (launchpadContext!=null) {
            batchEventData.username = launchpadContext.authentication.getName();
            contextId = launchpadContext.contextId;
        }
        applicationEventPublisher.publishEvent(new LaunchpadApplicationEvent(event, contextId, batchEventData));
    }

    public void publishTaskEvent(Enums.LaunchpadEventType event, Long stationId, Long taskId, Long workbookId) {
        LaunchpadEventYaml.TaskEventData taskEventData = new LaunchpadEventYaml.TaskEventData();
        taskEventData.stationId = stationId;
        taskEventData.taskId = taskId;
        taskEventData.workbookId = workbookId;
        applicationEventPublisher.publishEvent(new LaunchpadApplicationEvent(event, taskEventData));
    }

    @Async
    @EventListener
    public void handleAsync(LaunchpadApplicationEvent event) {
        LaunchpadEvent le = new LaunchpadEvent();
        le.createdOn = event.launchpadEventYaml.createdOn;
        le.event = event.launchpadEventYaml.event.toString();
        le.params = LaunchpadEventYamlUtils.BASE_YAML_UTILS.toString(event.launchpadEventYaml);
        launchpadEventRepository.save(le);
    }
}
