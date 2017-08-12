package aiai.ai.comm;

import aiai.ai.launchpad.station.StationsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@Controller
@RequestMapping("/srv")
public class ServerController {

    private CommandProcessor commandProcessor;

    public ServerController(CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }

/*
    // right this isn't working. some problem in 'ResponseEntity<ExchangeData> response = restTemplate.exchange()'. need to investigate
    @PostMapping("/in")
    public @ResponseBody ExchangeData postDatasets(@RequestBody ExchangeData data  )  {
        System.out.println("received json via POST: " + data);
        return new ExchangeData(new Protocol.Ok());
    }
*/

    @PostMapping("/in")
    public @ResponseBody String postDatasets(@RequestBody ExchangeData data  )  {
        System.out.println("received ExchangeData via POST: " + data);
        return "Ok as string";
    }

    @GetMapping("/in-str")
    public @ResponseBody String getDataAsStr(@RequestParam(required = false) String json )  {
        System.out.println("received json via getDataAsStr(): " + json);
        return "Ok as string";
    }

    @PostMapping("/in-str")
    public @ResponseBody String postDataAsStr(@RequestParam(required = false) String json , HttpServletRequest request)  {
        System.out.println("received json via postDataAsStr(): " + json);
        Map<String, String> sysParams = new HashMap<>();
        sysParams.put(CommConsts.IP, request.getRemoteAddr());
        return commandProcessor.processAll(json, sysParams);
    }
}
