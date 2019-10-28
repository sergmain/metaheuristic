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

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.api.data.event.LaunchpadEventYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.Data;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:33 PM
 */
@Data
public class LaunchpadApplicationEvent {

    public final LaunchpadEventYaml launchpadEventYaml;

    public LaunchpadApplicationEvent(EnumsApi.LaunchpadEventType event, String contextId, LaunchpadEventYaml.BatchEventData batchEventData) {
        LaunchpadEventYaml launchpadEventYaml = new LaunchpadEventYaml();
        launchpadEventYaml.createdOn = System.currentTimeMillis();
        launchpadEventYaml.event = event;
        launchpadEventYaml.contextId = contextId;
        launchpadEventYaml.batchData = batchEventData;
        this.launchpadEventYaml = launchpadEventYaml;
    }

    public LaunchpadApplicationEvent(EnumsApi.LaunchpadEventType event, LaunchpadEventYaml.TaskEventData taskEventData) {
        LaunchpadEventYaml launchpadEventYaml = new LaunchpadEventYaml();
        launchpadEventYaml.createdOn = System.currentTimeMillis();
        launchpadEventYaml.event = event;
        launchpadEventYaml.taskData = taskEventData;
        this.launchpadEventYaml = launchpadEventYaml;
    }
}
