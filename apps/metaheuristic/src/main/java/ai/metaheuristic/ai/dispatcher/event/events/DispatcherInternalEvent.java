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
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import java.util.function.Consumer;

/**
 * @author Serge
 * Date: 1/24/2020
 * Time: 1:25 AM
 */
public class DispatcherInternalEvent {

    @Data
    @AllArgsConstructor
    public static class DeleteExperimentByExecContextIdEvent {
        public Long execContextId;
    }

    @Data
    @AllArgsConstructor
    public static class DeleteExperimentEvent {
        public Long experimentId;
    }

    @AllArgsConstructor
    public static class ExecContextDeletionEvent {
        public final Long execContextId;
    }
}
