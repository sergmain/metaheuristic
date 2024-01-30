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

package ai.metaheuristic.ai.functions.ws;

import ai.metaheuristic.commons.yaml.YamlUtils;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Sergio Lissner
 * Date: 11/14/2023
 * Time: 7:31 PM
 */
public class ChatMessageEncoder implements Encoder.Text<ChatMessage> {

//    private static Gson gson = new Gson();

    @Override
    public String encode(ChatMessage message) {
        Yaml inited = YamlUtils.init(ChatMessage.class);
        return YamlUtils.toString(message, inited);
    }

    @Override
    public void init(EndpointConfig endpointConfig) {
        // Custom initialization logic
    }

    @Override
    public void destroy() {
        // Close resources
    }
}