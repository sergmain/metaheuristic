/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:33 PM
 */
@Data
public class DispatcherApplicationEvent {

    public final DispatcherEventYaml dispatcherEventYaml;
    public Long companyUniqueId;

    public DispatcherApplicationEvent(EnumsApi.DispatcherEventType event, Long companyUniqueId, String contextId, DispatcherEventYaml.BatchEventData batchEventData) {
        this.companyUniqueId = companyUniqueId;
        DispatcherEventYaml dispatcherEventYaml = new DispatcherEventYaml();
        dispatcherEventYaml.createdOn = CommonConsts.EVENT_DATE_TIME_FORMATTER.format(LocalDateTime.now());
        dispatcherEventYaml.event = event;
        dispatcherEventYaml.contextId = contextId;
        dispatcherEventYaml.contextId = contextId;
        dispatcherEventYaml.batchData = batchEventData;
        this.dispatcherEventYaml = dispatcherEventYaml;
    }

    public DispatcherApplicationEvent(EnumsApi.DispatcherEventType event, DispatcherEventYaml.TaskEventData taskEventData) {
        DispatcherEventYaml dispatcherEventYaml = new DispatcherEventYaml();
        dispatcherEventYaml.createdOn = CommonConsts.EVENT_DATE_TIME_FORMATTER.format(LocalDateTime.now());
        dispatcherEventYaml.event = event;
        dispatcherEventYaml.taskData = taskEventData;
        this.dispatcherEventYaml = dispatcherEventYaml;
    }
}
