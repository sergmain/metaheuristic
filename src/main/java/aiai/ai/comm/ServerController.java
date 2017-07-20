package aiai.ai.comm;

import aiai.ai.launchpad.station.StationsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@Controller
@RequestMapping("/srv")
public class ServerController {

    private static ObjectMapper mapper;
    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private StationsRepository repository;

    public ServerController(StationsRepository repository) {
        this.repository = repository;
    }

//    @PostMapping("/in")
    @GetMapping("/in")
    public @ResponseBody String getDatasets(@RequestParam(required = false) String json )  {
//    public void getDatasets(String json )  {

        System.out.println("received json via GET: " + json);
/*
        List<Protocol.Command> commands

        repository.save( dataset );

        final Optional<Dataset> value = repository.findById(id);
        repository.save( dataset );
*/
        return "[{}]";
    }

/*
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
        try {
            ExchangeData data = mapper.readValue(json, ExchangeData.class);
            System.out.println(data);
        } catch (IOException e) {
            return e.toString();
        }

        return "Ok as string";
    }

    @PostMapping("/in-str")
    public @ResponseBody String postDataAsStr(@RequestParam(required = false) String json )  {
        System.out.println("received json via postDataAsStr(): " + json);
        return "Ok as string";
    }

}
