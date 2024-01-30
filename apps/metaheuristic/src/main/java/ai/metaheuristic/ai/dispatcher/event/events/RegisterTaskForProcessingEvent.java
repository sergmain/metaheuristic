/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 3:38 PM
 */
@Data
@AllArgsConstructor
public class RegisterTaskForProcessingEvent {

    @Data
    @AllArgsConstructor
    public static class ExecContextWithTaskIds {
        public Long execContextId;
        public Long taskId;
    }

    public final List<ExecContextWithTaskIds> tasks = new ArrayList<>();
}
