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

package ai.metaheuristic.ai.dispatcher.ws;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.yaml.ws_event.WebsocketEventParams;
import ai.metaheuristic.ai.yaml.ws_event.WebsocketEventParamsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * @author Sergio Lissner
 * Date: 2/9/2024
 * Time: 2:03 PM
 */
@Slf4j
@Service
@Profile("dispatcher & websocket")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DispatcherWebsocketService {

    private final SimpMessagingTemplate template;

    public void sendEvent(Enums.WebsocketEventType type) {
        WebsocketEventParams params = new WebsocketEventParams();
        params.type = type;
        if (type== Enums.WebsocketEventType.function) {
            throw new IllegalStateException("Not implemented yet");
        }
        String text = WebsocketEventParamsUtils.BASE_UTILS.toString(params);
        this.template.convertAndSend("/topic/events", text);
    }
}
