/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.event.events;

import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 12/18/2020
 * Time: 4:03 AM
 */
@AllArgsConstructor
public class SetTaskExecStateInQueueEvent implements CommonEvent {
    public final Long execContextId;
    public final Long taskId;
    public final EnumsApi.TaskExecState state;
    @Nullable
    public final Long coreId;
    @Nullable
    public EnumsApi.FunctionExecContext context;
    @Nullable
    public String funcCode;
}
