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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.LaunchpadEvent;
import ai.metaheuristic.ai.launchpad.repositories.LaunchpadEventRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.event.LaunchpadEventYaml;
import ai.metaheuristic.commons.yaml.event.LaunchpadEventYamlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:34 PM
 */
@Service
@RequiredArgsConstructor
@Profile("launchpad")
public class LaunchpadEventService {

    private final Globals globals;
    private final LaunchpadEventRepository launchpadEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishBatchEvent(
            EnumsApi.LaunchpadEventType event, String filename,
            Long size, Long batchId, Long workbookId, LaunchpadContext launchpadContext) {
        if (!globals.isEventEnabled) {
            return;
        }
        if (event== EnumsApi.LaunchpadEventType.BATCH_CREATED && (batchId==null || launchpadContext==null)) {
            throw new IllegalStateException("Error (event==Enums.LaunchpadEventType.BATCH_CREATED && (batchId==null || launchpadContext==null))");
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

    public void publishTaskEvent(EnumsApi.LaunchpadEventType event, Long stationId, Long taskId, Long workbookId) {
        if (!globals.isEventEnabled) {
            return;
        }
        LaunchpadEventYaml.TaskEventData taskEventData = new LaunchpadEventYaml.TaskEventData();
        taskEventData.stationId = stationId;
        taskEventData.taskId = taskId;
        taskEventData.workbookId = workbookId;
        applicationEventPublisher.publishEvent(new LaunchpadApplicationEvent(event, taskEventData));
    }

    @Async
    @EventListener
    public void handleAsync(LaunchpadApplicationEvent event) {
        if (!globals.isEventEnabled) {
            return;
        }
        LaunchpadEvent le = new LaunchpadEvent();
        le.period = getPeriod(event.launchpadEventYaml.createdOn);
        le.createdOn = event.launchpadEventYaml.createdOn;
        le.event = event.launchpadEventYaml.event.toString();
        le.params = LaunchpadEventYamlUtils.BASE_YAML_UTILS.toString(event.launchpadEventYaml);
        launchpadEventRepository.save(le);
    }

    private static int getPeriod(long createdOn) {
        LocalDate date = Instant.ofEpochMilli(createdOn)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return date.getYear() * 100 + date.getMonthValue();
    }
}
