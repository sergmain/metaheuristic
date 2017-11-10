package aiai.ai.comm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
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

    private CsrfTokenRepository csrfTokenRepository;

    private RestTemplate restTemplate;

    public LaunchpadRequester(CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
        restTemplate = new RestTemplate();
    }


/*
    @Test
    public void test_Get_WithoutTokens() {
        ResponseEntity<String> response = http(HttpMethod.GET, "/api/test", null);
        assertEquals("GET Received", response.getBody());
    }

    @Test(expected = HttpClientErrorException.class)
    public void test_Post_WithoutTokens() {
        http(HttpMethod.POST, "/api/test", null);
        fail("should throw the exception above");
    }

    @Test
    public void test_Post_WithTokens() {
        final String clientSecret = "my_little_secret";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("X-CSRF-TOKEN", clientSecret);
        httpHeaders.set("Cookie", "CSRF-TOKEN=" + clientSecret);
        ResponseEntity<String> response = http(HttpMethod.POST, "/api/test", httpHeaders);
        assertEquals("POST Received", response.getBody());
    }

    private ResponseEntity<String> http(final HttpMethod method, final String path, HttpHeaders headers) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders httpHeaders = headers == null ? new HttpHeaders() : headers;
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> testRequest = new HttpEntity<>(httpHeaders);
        return restTemplate.exchange("http://localhost:8181/" + path, method, testRequest, String.class);
    }

    public HttpHeaders csrfHeaders() {
        CsrfToken csrfToken = csrfTokenRepository.generateToken(null);
        HttpHeaders headers = basicAuthHeaders();

        headers.add(csrfToken.getHeaderName(), csrfToken.getToken());
        headers.add("Cookie", "XSRF-TOKEN=" + csrfToken.getToken());

        return headers;
}

*/

    /**
     */
//    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayTaskViaHttpComponent() {

        ExchangeData data = new ExchangeData(new Protocol.AssignStationId());

        String jsonInString;
        try {
            jsonInString = mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException("Error", e);
        }

        final String urlTarget = launchpadUrl + "/srv/in-str";
        try {

            final CsrfToken csrfToken = csrfHeader();
            final Header csrfHeader = new BasicHeader(csrfToken.getHeaderName(), csrfToken.getToken());

            // for actual working with csrf, an auto-login need to be added
            String json = Request.Post(urlTarget)
                    .setHeaders(csrfHeader)
                    .bodyForm(
                            Form.form()
                                    .add("_csrf", csrfToken.getToken())
                                    .add("json", jsonInString)
                                    .build(), UTF_8)
                    .execute().returnContent().asString(UTF_8);

            ExchangeData responses = mapper.readValue(json, ExchangeData.class);
            for (Command command : responses.commands) {
                switch(command.getType()) {
                    case ReportStation:
                        break;
                    case RequestDatasets:
                        break;
                    case AssignStationId:
                        System.out.println("New station Id: " + command.getResponse().get(CommConsts.STATION_ID));
                        break;
                }
            }

            System.out.println(new Date() + " This runs in a fixed delay via fixedDelayTaskViaHttpComponent(), result: " + json);
        } catch (HttpResponseException e) {
            e.printStackTrace();
            System.out.println("target url: " + urlTarget);
            System.out.println("http status code: " + e.getStatusCode());
            throw new RuntimeException("Error", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error", e);
        }
    }

    CsrfToken csrfHeader() {
        return csrfTokenRepository.generateToken(null);
    }

    /**
     * This example uses fixedRate, which specifies the interval between method invocations measured from the start time of each invocation.
     * There are other options, like fixedDelay, which specifies the interval between invocations measured from the completion of the task
     */

//    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayTask() {

        try {
//            List<Protocol.Command> commands = Collections.singletonList(new Protocol.Nop());
            List<Map<String, String>> commands = Collections.singletonList(Collections.singletonMap("command", "nop"));

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
            final String url = launchpadUrl + "/srv/in";
            String result = restTemplate.getForObject(url, String.class, Collections.singletonMap("json", jsonInString));


            System.out.println(new Date() + " This runs in a fixed delay, result: " + result);

        } catch (JsonProcessingException e) {
            //log.error();
            e.printStackTrace();
            throw new RuntimeException("Error", e);
        }
    }

    public static final Charset UTF_8 = Charset.forName("utf8");

    /**
     * Experementas. Spring Boot (m2/Snapshot) doesn't work at all
     */
//    @Scheduled(fixedDelayString = "#{ new Integer(environment.getProperty('aiai.station.request.launchpad.timeout')) > 10 ? new Integer(environment.getProperty('aiai.station.request.launchpad.timeout'))*1000 : 10000 }")
    public void fixedDelayTaskComplex() {

        final String url = launchpadUrl + "/srv/in";


        HttpHeaders headers = new HttpHeaders();

        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ExchangeData data = new ExchangeData(new Protocol.Nop());

        HttpEntity<ExchangeData> request = new HttpEntity<>(data, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        String result = response.getBody();

        //        ResponseEntity<ExchangeData> response = restTemplate.exchange(url, HttpMethod.POST, request, ExchangeData.class);
//        ExchangeData result = response.getBody();

        System.out.println(new Date() + " This runs in a fixed delay (Complex), result: " + result);
    }

    public static String encode(String input) {
        try {
            String encode;
            encode = URLEncoder.encode(input, "UTF-8");
            return StringUtils.replace(encode, "+", "%20");
        }
        catch (UnsupportedEncodingException | NullPointerException e) {
            throw new RuntimeException("Error, inout: " + input, e);
        }
    }

    public static void main(String[] args) throws JsonProcessingException {
        List<Map<String, String>> commands = Collections.singletonList(Collections.singletonMap("command", "nop"));
        // Convert object to JSON string
        String jsonInString = mapper.writeValueAsString(commands);
        System.out.println(jsonInString);
    }
}


