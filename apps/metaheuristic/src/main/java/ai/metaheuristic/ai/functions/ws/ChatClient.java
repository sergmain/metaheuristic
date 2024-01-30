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

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.net.URI;
import java.util.Scanner;

/**
 * @author Sergio Lissner
 * Date: 11/14/2023
 * Time: 8:26 PM
 */
@Slf4j
public class ChatClient {

    private static String URL = "ws://localhost:8080/ws/chat/one";

    public static void client() {
        try {
            WebSocketClient client = new StandardWebSocketClient();

            WebSocketStompClient stompClient = new WebSocketStompClient(client);
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());

            StompSessionHandler sessionHandler = new MyStompSessionHandler();
            final String url = URL;
            URI uri  = new URI(url);
            stompClient.connect(url, sessionHandler);

            String s;
            while ((s=new Scanner(System.in).nextLine())!=null) {
                System.out.println(s);
            }
        } catch (Throwable th) {
            log.error("Error", th);
        }
    }
}
