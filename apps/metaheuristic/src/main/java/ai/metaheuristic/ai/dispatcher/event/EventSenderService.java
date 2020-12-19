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

import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 11/2/2020
 * Time: 3:49 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class EventSenderService {

    private final ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("RedundantCast")
    public void sendEvents(DataHolder holder) {
        TxUtils.checkTxNotExists();
        for (CommonEvent event : holder.events) {
            if (event instanceof CheckTaskCanBeFinishedEvent) {
                eventPublisher.publishEvent((CheckTaskCanBeFinishedEvent)event);
            }
            else if (event instanceof VariableUploadedEvent) {
                eventPublisher.publishEvent((VariableUploadedEvent)event);
            }
            else if (event instanceof TaskCreatedEvent) {
                eventPublisher.publishEvent((TaskCreatedEvent)event);
            }
            else if (event instanceof UpdateTaskExecStatesInGraphEvent) {
                eventPublisher.publishEvent((UpdateTaskExecStatesInGraphEvent)event);
            }
            else {
                throw new IllegalStateException("Not supported event: " +  event.getClass().getName());
            }
        }

        holder.events.clear();
    }
}
