package aiai.ai.station.net;

import aiai.ai.Globals;
import org.apache.http.client.fluent.Executor;
import org.springframework.stereotype.Component;

@Component
public class HttpClientExecutor {

    private final Globals globals;
    public final Executor executor;

    public HttpClientExecutor(Globals globals) {
        this.globals = globals;

        this.executor = Executor.newInstance()
                .auth(this.globals.launchpadHttpHostWithAuth,
                        this.globals.restUsername+'=' + this.globals.restToken,
                        this.globals.stationRestPassword);
    }
}
