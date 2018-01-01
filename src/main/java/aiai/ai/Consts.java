package aiai.ai;

import org.springframework.http.MediaType;

import java.nio.charset.Charset;

public class Consts {
    public static final String SESSIONID_NAME = "JSESSIONID";

    public static final String SERVER_REST_URL = "/rest-anon/srv";

    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));
}
