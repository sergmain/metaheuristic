/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.comm;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@RestController
public class ServerController {

    private static final ExchangeData EXCHANGE_DATA_NOP = new ExchangeData(new Protocol.Nop());
    private CommandProcessor commandProcessor;

    public ServerController(CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }

    @PostMapping("/rest-anon/srv")
    public ExchangeData postDatasets(@RequestBody ExchangeData data) {
        System.out.println("received ExchangeData via POST: " + data);
        ExchangeData resultData = new ExchangeData();
        List<Command> commands = data.getCommands();
        for (Command command : commands) {
            resultData.setCommand(commandProcessor.process(command));
        }

        return resultData.getCommands().isEmpty() ? EXCHANGE_DATA_NOP : resultData;
    }

    @GetMapping("/rest-anon/srv-str")
    public String getDataAsStr(@RequestParam(required = false) String json) {
        System.out.println("received json via getDataAsStr(): " + json);
        return "Ok as string";
    }

    @PostMapping("/rest-anon/srv-str")
    public String postDataAsStr(String json, HttpServletRequest request) {
        System.out.println("received json via postDataAsStr(): " + json);
        Map<String, String> sysParams = new HashMap<>();
        sysParams.put(CommConsts.IP, request.getRemoteAddr());
        return commandProcessor.processAll(json, sysParams);
    }
}
