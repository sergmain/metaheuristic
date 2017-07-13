package aiai.ai.comm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */
@Service
@EnableScheduling
//@Log4j2
public class LaunchpadRequester {

    @Value("${aiai.station.launchpad.url}")
    private String launchpadUrl;

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private RestTemplate rest;

    public LaunchpadRequester() {
        rest = new RestTemplate();
    }

    /**
     * This example uses fixedRate, which specifies the interval between method invocations measured from the start time of each invocation.
     * There are other options, like fixedDelay, which specifies the interval between invocations measured from the completion of the task
     */


    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayTask() {

        try {
//            List<Protocol.Command> commands = Collections.singletonList(new Protocol.Nop());
            List<Map<String, String>> commands = Collections.singletonList( Collections.singletonMap("command", "nop"));

            // Convert object to JSON string
            String jsonInString = mapper.writeValueAsString(commands);

/*
 postForObject

public <T> T postForObject(String url,
                           Object request,
                           Class<T> responseType,
                           Map<String,?> uriVariables)
                throws RestClientException

    Description copied from interface: RestOperations
    Create a new resource by POSTing the given object to the URI template, and returns the representation found in the response.

    URI Template variables are expanded using the given map.

    The request parameter can be a HttpEntity in order to add additional HTTP headers to the request.

    Specified by:
        postForObject in interface RestOperations

    Parameters:
        url - the URL
        request - the Object to be POSTed, may be null
        responseType - the type of the return value
        uriVariables - the variables to expand the template
    Returns:
        the converted object
    Throws:
        RestClientException
    See Also:
        HttpEntity


*/
//            String result = rest.postForObject(launchpadUrl + "/srv/in", null, String.class, Collections.singletonMap("json", jsonInString));
            String result = rest.getForObject(launchpadUrl + "/srv/in", String.class, Collections.singletonMap("json", jsonInString));

            System.out.println(new Date() + " This runs in a fixed delay, result: " + result);

        } catch (JsonProcessingException e) {
            //log.error();
            e.printStackTrace();
            throw new RuntimeException("Error", e);
        }
    }

/*
    @Scheduled(fixedRate = 6000)
    public void fixedRateTask() {
        System.out.println(new Date() + " This runs in a fixed rate");
    }
*/

    public static void main(String[] args) throws JsonProcessingException {
        List<Map<String, String>> commands = Collections.singletonList( Collections.singletonMap("command", "nop"));

        // Convert object to JSON string
        String jsonInString = mapper.writeValueAsString(commands);

        System.out.println(jsonInString);
    }
}

