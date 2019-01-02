package aiai.ai.station.net;

import aiai.ai.Globals;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.utils.URIUtils;
import org.springframework.stereotype.Component;

import java.net.URL;

public class HttpClientExecutor {

    public static Executor getExecutor(String launchpadUrl, String restUsername, String restToken, String restPassword) {
        HttpHost launchpadHttpHostWithAuth;
        try {
            launchpadHttpHostWithAuth = URIUtils.extractHost(new URL(launchpadUrl).toURI());
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for "+launchpadUrl, th);
        }
        return Executor.newInstance()
                .authPreemptive(launchpadHttpHostWithAuth)
                .auth(launchpadHttpHostWithAuth,restUsername+'=' + restToken, restPassword);
    }
}
